package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.domain.tenant.TenantConfiguration;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.domain.tenant.WhatsAppProviderType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Envio outbound: {@code direct:processWhatsAppResponse} escolhe Meta, Evolution ou log (SIMULATED / credenciais em falta).
 */
@Component
@Order(300)
public class WhatsAppOutboundRoutes extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsAppOutboundRoutes.class);

    private static final String PROP_FB_PATH = "fbGraphPath";

    private static final String PROP_EVOLUTION_URL = "evolutionFullUrl";

    private final TenantConfigurationStorePort tenantConfigurationStore;
    private final ObjectMapper objectMapper;
    private final String metaGraphApiVersion;
    /** Se não vazio, substitui {@link TenantConfiguration#whatsappBaseUrl()} ao montar a URL Evolution (ex.: rede Docker). */
    private final String evolutionBaseUrlOverride;

    public WhatsAppOutboundRoutes(
            TenantConfigurationStorePort tenantConfigurationStore,
            ObjectMapper objectMapper,
            @Value("${cerebro.whatsapp.meta.graph-api-version:v21.0}") String metaGraphApiVersion,
            @Value("${cerebro.whatsapp.evolution.base-url-override:}") String evolutionBaseUrlOverride) {
        this.tenantConfigurationStore = tenantConfigurationStore;
        this.objectMapper = objectMapper;
        this.metaGraphApiVersion = metaGraphApiVersion;
        this.evolutionBaseUrlOverride = evolutionBaseUrlOverride != null ? evolutionBaseUrlOverride : "";
    }

    @Override
    public void configure() {
        // @formatter:off
        from("direct:processWhatsAppResponse")
                .routeId("processWhatsAppResponse")
                .process(this::loadTenantAndHeaders)
                .choice()
                    .when(header(WhatsAppOutboundHeaders.PROVIDER).isEqualTo(WhatsAppProviderType.META.name()))
                        .to("direct:sendToMeta")
                    .when(header(WhatsAppOutboundHeaders.PROVIDER).isEqualTo(WhatsAppProviderType.EVOLUTION.name()))
                        .to("direct:sendToEvolution")
                    .otherwise()
                        .to("direct:sendToLog")
                .end();

        from("direct:sendToMeta")
                .routeId("sendToMeta")
                .doTry()
                    .process(this::prepareMetaHttp)
                    .toD("https://graph.facebook.com/${exchangeProperty." + PROP_FB_PATH + "}/messages")
                    .process(this::logMetaResponse)
                .doCatch(Exception.class)
                    .process(this::logOutboundFailure)
                .end();

        from("direct:sendToEvolution")
                .routeId("sendToEvolution")
                .doTry()
                    .process(this::prepareEvolutionHttp)
                    .toD("${exchangeProperty." + PROP_EVOLUTION_URL + "}")
                    .process(this::logEvolutionResponse)
                .doCatch(Exception.class)
                    .process(this::logOutboundFailure)
                .end();

        from("direct:sendToLog")
                .routeId("sendToLog")
                .process(this::logSimulation);
        // @formatter:on
    }

    private void loadTenantAndHeaders(Exchange exchange) {
        String tenantIdStr = exchange.getIn().getHeader(WhatsAppOutboundHeaders.TENANT_ID, String.class);
        String to = exchange.getIn().getHeader(WhatsAppOutboundHeaders.TO, String.class);
        String text = exchange.getIn().getBody(String.class);
        if (tenantIdStr == null || tenantIdStr.isBlank()) {
            LOG.warn("whatsapp outbound: tenantId em falta; a usar SIMULATED");
            exchange.getMessage().setHeader(WhatsAppOutboundHeaders.PROVIDER, WhatsAppProviderType.SIMULATED.name());
            exchange.setProperty(WhatsAppOutboundHeaders.PROP_WA_TO, to != null ? to : "");
            exchange.setProperty(WhatsAppOutboundHeaders.PROP_WA_TEXT, text != null ? text : "");
            return;
        }
        TenantId tenantId = new TenantId(tenantIdStr);
        TenantConfiguration config =
                tenantConfigurationStore.findByTenantId(tenantId).orElseGet(() -> TenantConfiguration.defaults(tenantId));

        WhatsAppProviderType effective = effectiveProvider(config);
        exchange.getMessage().setHeader(WhatsAppOutboundHeaders.PROVIDER, effective.name());
        exchange.setProperty(WhatsAppOutboundHeaders.PROP_WA_TENANT_CONFIG, config);
        exchange.setProperty(WhatsAppOutboundHeaders.PROP_WA_TO, to != null ? to : "");
        exchange.setProperty(WhatsAppOutboundHeaders.PROP_WA_TEXT, text != null ? text : "");
    }

    static WhatsAppProviderType effectiveProvider(TenantConfiguration c) {
        return switch (c.whatsappProviderType()) {
            case META -> (c.whatsappApiKey() != null && !c.whatsappApiKey().isBlank())
                            && (c.whatsappInstanceId() != null && !c.whatsappInstanceId().isBlank())
                    ? WhatsAppProviderType.META
                    : WhatsAppProviderType.SIMULATED;
            case EVOLUTION -> (c.whatsappApiKey() != null && !c.whatsappApiKey().isBlank())
                            && (c.whatsappInstanceId() != null && !c.whatsappInstanceId().isBlank())
                            && (c.whatsappBaseUrl() != null && !c.whatsappBaseUrl().isBlank())
                    ? WhatsAppProviderType.EVOLUTION
                    : WhatsAppProviderType.SIMULATED;
            case SIMULATED -> WhatsAppProviderType.SIMULATED;
        };
    }

    private void prepareMetaHttp(Exchange exchange) throws Exception {
        TenantConfiguration cfg = exchange.getProperty(WhatsAppOutboundHeaders.PROP_WA_TENANT_CONFIG, TenantConfiguration.class);
        String to = exchange.getProperty(WhatsAppOutboundHeaders.PROP_WA_TO, String.class);
        String text = exchange.getProperty(WhatsAppOutboundHeaders.PROP_WA_TEXT, String.class);
        String digits = onlyDigits(to);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("messaging_product", "whatsapp");
        root.put("recipient_type", "individual");
        root.put("to", digits);
        root.put("type", "text");
        ObjectNode textNode = root.putObject("text");
        textNode.put("preview_url", false);
        textNode.put("body", text != null ? text : "");

        exchange.getMessage().setBody(objectMapper.writeValueAsString(root));
        exchange.getMessage().removeHeaders("*");
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, "POST");
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getMessage().setHeader("Authorization", "Bearer " + cfg.whatsappApiKey());

        String path = metaGraphApiVersion + "/" + cfg.whatsappInstanceId();
        exchange.setProperty(PROP_FB_PATH, path);
    }

    private void logMetaResponse(Exchange exchange) {
        Integer code = exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        LOG.debug("whatsapp Meta HTTP resposta código={}", code);
    }

    private void prepareEvolutionHttp(Exchange exchange) throws Exception {
        TenantConfiguration cfg = exchange.getProperty(WhatsAppOutboundHeaders.PROP_WA_TENANT_CONFIG, TenantConfiguration.class);
        String to = exchange.getProperty(WhatsAppOutboundHeaders.PROP_WA_TO, String.class);
        String text = exchange.getProperty(WhatsAppOutboundHeaders.PROP_WA_TEXT, String.class);
        String digits = onlyDigits(to);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("number", digits);
        root.put("text", text != null ? text : "");

        String baseRaw =
                evolutionBaseUrlOverride.isBlank() ? cfg.whatsappBaseUrl() : evolutionBaseUrlOverride;
        String base = trimTrailingSlash(baseRaw);
        String url = base + "/message/sendText/" + cfg.whatsappInstanceId();

        exchange.getMessage().setBody(objectMapper.writeValueAsString(root));
        exchange.getMessage().removeHeaders("*");
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, "POST");
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getMessage().setHeader("apikey", cfg.whatsappApiKey());

        exchange.setProperty(PROP_EVOLUTION_URL, url);
    }

    private void logEvolutionResponse(Exchange exchange) {
        Integer code = exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        LOG.debug("whatsapp Evolution HTTP resposta código={}", code);
    }

    private void logOutboundFailure(Exchange exchange) {
        Exception ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        if (ex == null) {
            ex = exchange.getException();
        }
        String to = exchange.getProperty(WhatsAppOutboundHeaders.PROP_WA_TO, String.class);
        LOG.warn("whatsapp outbound falhou (to={}): {}", to, ex != null ? ex.getMessage() : "unknown");
        if (ex != null && LOG.isDebugEnabled()) {
            LOG.debug("whatsapp outbound stack", ex);
        }
    }

    private void logSimulation(Exchange exchange) {
        String to = exchange.getProperty(WhatsAppOutboundHeaders.PROP_WA_TO, String.class);
        String text = exchange.getProperty(WhatsAppOutboundHeaders.PROP_WA_TEXT, String.class);
        LOG.info("[Simulação WhatsApp para {}]: {}", to, text);
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }

    private static String onlyDigits(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
