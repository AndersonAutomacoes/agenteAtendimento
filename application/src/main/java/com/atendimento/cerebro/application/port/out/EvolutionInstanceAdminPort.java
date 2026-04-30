package com.atendimento.cerebro.application.port.out;

import java.util.Optional;

/** Chamadas administrativas à Evolution API (instância Baileys, webhook, pareamento). */
public interface EvolutionInstanceAdminPort {

    record CreateInstanceResult(boolean success, int httpStatus, String rawResponse, Optional<String> qrcodeBase64) {}

    /** Cria instância WhatsApp (Baileys) com nome único no servidor Evolution. */
    CreateInstanceResult createWhatsappBaileysInstance(
            String evolutionBaseUrl, String apiKey, String instanceName, boolean requestQrcodeInResponse);

    /** Regista webhook por instância. */
    void setInstanceWebhook(
            String evolutionBaseUrl,
            String apiKey,
            String instanceName,
            String webhookUrl,
            boolean webhookByEvents);

    /** Força novo ciclo de pareamento (novo QR quando aplicável). */
    Optional<String> connectAndFetchQrcodeBase64(String evolutionBaseUrl, String apiKey, String instanceName);
}
