package com.atendimento.cerebro.infrastructure.adapter.out.ai;



import static org.assertj.core.api.Assertions.assertThat;



import com.atendimento.cerebro.application.scheduling.SchedulingSlotCapture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;



class GeminiSchedulingToolsJsonTest {



    private static final ObjectMapper M = new ObjectMapper();



    @Test

    void buildEvolutionAvailabilityJson_matchesContract() throws Exception {

        String json =

                GeminiSchedulingTools.buildEvolutionAvailabilityJson(

                        "Selecione o melhor horário para amanhã (13/04):", List.of("09:00", "10:30", "14:00"));

        JsonNode root = M.readTree(json);

        assertThat(root.path("main_text").asText())

                .isEqualTo("Selecione o melhor horário para amanhã (13/04):");

        assertThat(root.path("buttons")).hasSize(3);

        assertThat(root.path("buttons").path(0).path("id").asText()).isEqualTo("09:00");

        assertThat(root.path("buttons").path(0).path("label").asText()).isEqualTo("09:00");

    }



    @Test

    void buildEvolutionAvailabilityJson_normalizesMessyInputsToValidButtons() throws Exception {

        List<String> messy = List.of(" 09:00 ", "\n10:30\u00a0", "", "  ", "14:15\t", "9:45");

        String json = GeminiSchedulingTools.buildEvolutionAvailabilityJson("Título:", messy);

        JsonNode root = M.readTree(json);

        assertThat(root.path("buttons")).hasSize(4);

        assertThat(root.path("buttons").path(0).path("id").asText()).isEqualTo("09:00");

        assertThat(root.path("buttons").path(1).path("label").asText()).isEqualTo("10:30");

        assertThat(root.path("buttons").path(3).path("label").asText()).isEqualTo("09:45");

    }



    @Test

    void parseAndJson_mockStyleLine_roundTrip() throws Exception {

        String mockLine =

                "Calendário (simulado): id. Horários livres em 2026-04-12 (America/Sao_Paulo): 08:00,  09:00 , 10:00";

        List<String> slots = SchedulingSlotCapture.parseSlotTimesFromAvailabilityLine(mockLine);

        String json = GeminiSchedulingTools.buildEvolutionAvailabilityJson("Selecione:", slots);

        JsonNode root = M.readTree(json);

        assertThat(root.path("buttons")).hasSize(3);

        assertThat(root.path("buttons").path(2).path("id").asText()).isEqualTo("10:00");

    }

}


