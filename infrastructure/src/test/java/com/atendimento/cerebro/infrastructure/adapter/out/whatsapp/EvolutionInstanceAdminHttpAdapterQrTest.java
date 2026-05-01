package com.atendimento.cerebro.infrastructure.adapter.out.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EvolutionInstanceAdminHttpAdapterQrTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void extractQr_fromNestedQrcode() throws Exception {
        JsonNode root = mapper.readTree("{\"qrcode\":{\"base64\":\"ABC123\"}}");
        assertThat(EvolutionInstanceAdminHttpAdapter.extractQrBase64(root)).contains("ABC123");
    }

    @Test
    void extractQr_fromNestedQrCode_camelCase() throws Exception {
        JsonNode root = mapper.readTree("{\"qrCode\":{\"base64\":\"CAMELB64\"}}");
        assertThat(EvolutionInstanceAdminHttpAdapter.extractQrBase64(root)).contains("CAMELB64");
    }

    @Test
    void extractQr_fromDataEnvelope() throws Exception {
        JsonNode root =
                mapper.readTree(
                        "{\"instance\":{\"instanceName\":\"ev\"},\"data\":{\"qrcode\":{\"base64\":\"PNGHERE\"}}}");
        assertThat(EvolutionInstanceAdminHttpAdapter.extractQrBase64(root)).contains("PNGHERE");
    }

    @Test
    void extractQr_missing_returnsEmpty() throws Exception {
        JsonNode root = mapper.readTree("{\"foo\":1}");
        assertThat(EvolutionInstanceAdminHttpAdapter.extractQrBase64(root)).isEqualTo(Optional.empty());
    }

    @Test
    void openConnectedResponse_detected() throws Exception {
        JsonNode root =
                mapper.readTree(
                        "{\"instance\":{\"instanceName\":\"evo-Pilates_6\",\"state\":\"open\"}}");
        assertThat(EvolutionInstanceAdminHttpAdapter.evolutionInstanceIndicatesOpenConnected(root))
                .isTrue();
    }

    @Test
    void connectingWithoutOpen_notDetectedAsOpenConnected() throws Exception {
        JsonNode root = mapper.readTree("{\"instance\":{\"instanceName\":\"x\",\"state\":\"connecting\"}}");
        assertThat(EvolutionInstanceAdminHttpAdapter.evolutionInstanceIndicatesOpenConnected(root))
                .isFalse();
    }
}
