package com.atendimento.cerebro.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Texto do card de confirmação de agendamento no WhatsApp e link opcional do Google Maps.
 */
@ConfigurationProperties(prefix = "cerebro.appointment.confirmation")
public class CerebroAppointmentConfirmationProperties {

    /** Linha exibida em "Local" no card. */
    private String locationLine = "Oficina InteliZap - Salvador, BA";

    /**
     * Se não vazio, envia-se uma segunda mensagem só com o link (facilita abrir no Maps no telefone).
     * Ex.: {@code https://maps.app.goo.gl/...}
     */
    private String mapsUrl = "";

    public String getLocationLine() {
        return locationLine;
    }

    public void setLocationLine(String locationLine) {
        this.locationLine = locationLine;
    }

    public String getMapsUrl() {
        return mapsUrl;
    }

    public void setMapsUrl(String mapsUrl) {
        this.mapsUrl = mapsUrl;
    }
}
