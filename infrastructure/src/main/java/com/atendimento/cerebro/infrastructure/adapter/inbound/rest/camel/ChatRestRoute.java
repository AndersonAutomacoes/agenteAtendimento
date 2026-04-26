package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.application.ai.AiChatProvider;
import com.atendimento.cerebro.application.ai.AiChatProviderResolver;
import com.atendimento.cerebro.application.dto.ChatCommand;
import com.atendimento.cerebro.application.dto.ChatResult;
import com.atendimento.cerebro.application.port.in.ChatUseCase;
import com.atendimento.cerebro.domain.conversation.ConversationId;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.List;
import java.util.Locale;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ChatRestRoute extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(ChatRestRoute.class);

    private static final String PROP_STOPWATCH = "chatStopwatch";
    private static final String PROP_TENANT = "tenantId";
    private static final String PROP_SESSION = "sessionId";
    private static final String PROP_CHAT_PROVIDER = "chatProvider";

    /** Propriedades definidas pelo Circuit Breaker EIP (Camel); nem sempre preenchidas — ver {@link #PROP_CHAT_FALLBACK}. */
    private static final String CAMEL_RESPONSE_FROM_FALLBACK = "CamelResponseFromFallback";

    private static final String CAMEL_RESPONSE_TIMED_OUT = "CamelResponseTimedOut";

    /** Definidas em {@link #respostaFallback} para diagnóstico fiável em {@link #logFimRota}. */
    private static final String PROP_CHAT_FALLBACK = "chatCircuitFallback";

    private static final String PROP_CHAT_TIMED_OUT = "chatCircuitTimedOut";

    private static final String PROP_CHAT_FAILURE_CLASS = "chatCircuitFailureClass";

    private final ChatUseCase chatUseCase;
    private final int circuitTimeoutMs;
    private final AiChatProvider defaultChatProvider;

    public ChatRestRoute(
            ChatUseCase chatUseCase,
            @Value("${chat.circuit.timeout-ms:15000}") int circuitTimeoutMs,
            @Value("${cerebro.ai.default-chat-provider:GEMINI}") String defaultChatProviderRaw) {
        this.chatUseCase = chatUseCase;
        this.circuitTimeoutMs = circuitTimeoutMs;
        this.defaultChatProvider =
                AiChatProvider.valueOf(defaultChatProviderRaw.trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public void configure() {
        restConfiguration()
                .component("servlet")
                .bindingMode(RestBindingMode.json)
                .contextPath("/")
                .dataFormatProperty("prettyPrint", "false");

        // Servlet Spring: camel.servlet.mapping.context-path=/api/* → URL completa /api/v1/chat
        rest("/v1/chat")
                .post()
                .consumes(MediaType.APPLICATION_JSON_VALUE)
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .type(ChatHttpRequest.class)
                .outType(ChatHttpResponse.class)
                .to("direct:chat");

        // @formatter:off
        from("direct:chat")
                .routeId("chatAtendimento")
                .onCompletion()
                    .process(this::logFimRota)
                .end()
                .process(this::logInicioRota)
                .process(this::montarComando)
                .circuitBreaker()
                    .resilience4jConfiguration()
                        .timeoutEnabled(true)
                        .timeoutDuration(circuitTimeoutMs)
                    .end()
                    .process(this::executarChat)
                .onFallback()
                    .process(this::respostaFallback)
                .end();
        // @formatter:on
    }

    private void logInicioRota(Exchange exchange) {
        StopWatch sw = new StopWatch();
        sw.start();
        exchange.setProperty(PROP_STOPWATCH, sw);
        ChatHttpRequest req = exchange.getIn().getBody(ChatHttpRequest.class);
        if (req != null) {
            exchange.setProperty(PROP_TENANT, req.tenantId());
            exchange.setProperty(PROP_SESSION, req.sessionId());
            LOG.info(
                    "chat inicio tenantId={} sessionId={} aiProviderJson={}",
                    req.tenantId(),
                    req.sessionId(),
                    req.aiProvider());
        } else {
            LOG.info("chat inicio (body ausente)");
        }
    }

    private void montarComando(Exchange exchange) {
        ChatHttpRequest req = exchange.getIn().getBody(ChatHttpRequest.class);
        if (req == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        exchange.setProperty(PROP_TENANT, req.tenantId());
        exchange.setProperty(PROP_SESSION, req.sessionId());
        AiChatProvider chatProvider = AiChatProviderResolver.resolve(req.aiProvider(), defaultChatProvider);
        exchange.setProperty(PROP_CHAT_PROVIDER, chatProvider);
        LOG.info(
                "chat motor de geração resolvido={} (RAG: embeddings Google GenAI)",
                chatProvider);
        exchange.getIn()
                .setBody(new ChatCommand(
                        new TenantId(req.tenantId()),
                        new ConversationId(req.sessionId()),
                        req.message(),
                        req.topK(),
                        chatProvider,
                        List.of()));
    }

    private void executarChat(Exchange exchange) {
        ChatCommand command = exchange.getIn().getBody(ChatCommand.class);
        ChatResult result = chatUseCase.chat(command);
        exchange.getIn()
                .setBody(
                        new ChatHttpResponse(
                                result.assistantMessage(),
                                result.whatsAppInteractive().orElse(null),
                                result.additionalOutboundMessages()));
    }

    private void respostaFallback(Exchange exchange) {
        Throwable failure = exchange.getException();
        if (failure == null) {
            failure = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        }
        boolean timedOut =
                isLikelyTimeout(failure)
                        || Boolean.TRUE.equals(exchange.getProperty(CAMEL_RESPONSE_TIMED_OUT, Boolean.class));
        String failureClass = failure != null ? failure.getClass().getName() : "unknown";

        exchange.setProperty(PROP_CHAT_FALLBACK, Boolean.TRUE);
        exchange.setProperty(PROP_CHAT_TIMED_OUT, timedOut);
        exchange.setProperty(PROP_CHAT_FAILURE_CLASS, failureClass);

        if (timedOut) {
            exchange.getIn().setBody(new ChatHttpResponse(ChatFallbackMessages.TIMEOUT, null, List.of()));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.GATEWAY_TIMEOUT.value());
            LOG.warn(
                    "chat circuit fallback (timeout) tenantId={} sessionId={} failureClass={}",
                    exchange.getProperty(PROP_TENANT, String.class),
                    exchange.getProperty(PROP_SESSION, String.class),
                    failureClass);
        } else {
            exchange.getIn().setBody(new ChatHttpResponse(ChatFallbackMessages.MAINTENANCE, null, List.of()));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.SERVICE_UNAVAILABLE.value());
            String tid = exchange.getProperty(PROP_TENANT, String.class);
            String sid = exchange.getProperty(PROP_SESSION, String.class);
            if (failure != null) {
                LOG.warn(
                        "chat circuit fallback (erro) tenantId={} sessionId={} failureClass={}",
                        tid,
                        sid,
                        failureClass,
                        failure);
            } else {
                LOG.warn(
                        "chat circuit fallback (erro) tenantId={} sessionId={} failureClass={}",
                        tid,
                        sid,
                        failureClass);
            }
        }
    }

    /**
     * TimeLimiter / Resilience4j e JDK usam várias hierarquias de exceção para timeout.
     */
    private static boolean isLikelyTimeout(Throwable t) {
        if (t == null) {
            return false;
        }
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            String name = c.getClass().getName();
            if (name.contains("Timeout") || name.contains("TimeLimiter")) {
                return true;
            }
        }
        return false;
    }

    private void logFimRota(Exchange exchange) {
        StopWatch sw = exchange.getProperty(PROP_STOPWATCH, StopWatch.class);
        if (sw != null && sw.isRunning()) {
            sw.stop();
        }
        long durationMs = sw != null ? sw.getTotalTimeMillis() : -1L;
        String tenant = exchange.getProperty(PROP_TENANT, String.class);
        String session = exchange.getProperty(PROP_SESSION, String.class);
        AiChatProvider motor = exchange.getProperty(PROP_CHAT_PROVIDER, AiChatProvider.class);
        boolean fromFallback =
                Boolean.TRUE.equals(exchange.getProperty(PROP_CHAT_FALLBACK, Boolean.class))
                        || Boolean.TRUE.equals(exchange.getProperty(CAMEL_RESPONSE_FROM_FALLBACK, Boolean.class));
        boolean timedOut =
                Boolean.TRUE.equals(exchange.getProperty(PROP_CHAT_TIMED_OUT, Boolean.class))
                        || Boolean.TRUE.equals(exchange.getProperty(CAMEL_RESPONSE_TIMED_OUT, Boolean.class));
        String failureClass = exchange.getProperty(PROP_CHAT_FAILURE_CLASS, String.class);

        LOG.info(
                "chat fim tenantId={} sessionId={} motorGeracao={} durationMs={} fallback={} timedOut={} failureClass={}",
                tenant,
                session,
                motor,
                durationMs,
                fromFallback,
                timedOut,
                failureClass);
    }
}
