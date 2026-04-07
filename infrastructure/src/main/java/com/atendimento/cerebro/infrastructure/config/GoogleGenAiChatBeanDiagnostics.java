package com.atendimento.cerebro.infrastructure.config;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ajuda a distinguir falha de <strong>chat</strong> (bean {@code googleGenAiChatModel}) de <strong>embeddings</strong>
 * (RAG), que usam outra configuração. O Google AI Studio pode mostrar tráfego de embeddings mesmo quando o chat não
 * está disponível na aplicação.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai")
public class GoogleGenAiChatBeanDiagnostics {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleGenAiChatBeanDiagnostics.class);

    private final ApplicationContext applicationContext;

    public GoogleGenAiChatBeanDiagnostics(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @EventListener(ApplicationReadyEvent.class)
    void logChatModelPresence() {
        String[] names = applicationContext.getBeanNamesForType(GoogleGenAiChatModel.class);
        if (names.length == 0) {
            LOG.warn(
                    "Bean googleGenAiChatModel ausente: o endpoint de chat Gemini falhará até ser corrigido. "
                            + "Embeddings (RAG) podem continuar a funcionar com spring.ai.google.genai.embedding.* — "
                            + "o AI Studio pode registar essas chamadas mesmo sem chat. "
                            + "Verifique spring.ai.google.genai.api-key (ligação de chat) e o relatório de auto-configuração.");
        } else {
            LOG.info("Gemini chat: GoogleGenAiChatModel registado como bean(s): {}", Arrays.toString(names));
        }
    }
}
