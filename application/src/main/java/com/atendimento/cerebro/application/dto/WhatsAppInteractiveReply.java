package com.atendimento.cerebro.application.dto;

import com.atendimento.cerebro.application.scheduling.CancelOptionMap;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Mensagem interativa Evolution API: horários (lista/botões), confirmação de rascunho ou escolha de cancelamento.
 *
 * @param verificationText linha opcional enviada antes do cartão/lista (ex.: verificação de disponibilidade).
 * @param requestedDate data da consulta de disponibilidade (lista “premium” em texto e contexto).
 */
public record WhatsAppInteractiveReply(
        WhatsAppInteractiveKind kind,
        String title,
        String description,
        List<String> slotTimes,
        String verificationText,
        LocalDate requestedDate,
        String listButtonText,
        String footerText,
        List<WhatsAppInteractiveRow> customRows) {

    public WhatsAppInteractiveReply {
        kind = kind != null ? kind : WhatsAppInteractiveKind.SLOTS;
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        slotTimes = slotTimes == null ? List.of() : List.copyOf(slotTimes);
        verificationText = verificationText == null ? "" : verificationText;
        listButtonText = listButtonText == null ? "" : listButtonText;
        footerText = footerText == null ? "" : footerText;
        customRows = customRows == null ? List.of() : List.copyOf(customRows);
    }

    /** Compatível com construção só com horários (tipo {@link WhatsAppInteractiveKind#SLOTS}). */
    public WhatsAppInteractiveReply(String title, String description, List<String> slotTimes) {
        this(
                WhatsAppInteractiveKind.SLOTS,
                title,
                description,
                slotTimes,
                "",
                null,
                "",
                "",
                List.of());
    }

    public WhatsAppInteractiveReply(String title, String description, List<String> slotTimes, String verificationText) {
        this(
                WhatsAppInteractiveKind.SLOTS,
                title,
                description,
                slotTimes,
                verificationText,
                null,
                "",
                "",
                List.of());
    }

    public WhatsAppInteractiveReply(String title, String description, List<String> slotTimes, String verificationText, LocalDate requestedDate) {
        this(
                WhatsAppInteractiveKind.SLOTS,
                title,
                description,
                slotTimes,
                verificationText,
                requestedDate,
                "",
                "",
                List.of());
    }

    /** Lista Sim/Não após pergunta de confirmação de agendamento. */
    public static WhatsAppInteractiveReply forConfirmationActions() {
        return new WhatsAppInteractiveReply(
                WhatsAppInteractiveKind.CONFIRMATION,
                "Confirmação",
                "Toque numa opção para responder.",
                List.of(),
                "",
                null,
                "Responder",
                "",
                List.of(
                        new WhatsAppInteractiveRow("confirm_yes", "Sim, confirmar"),
                        new WhatsAppInteractiveRow("confirm_no", "Não, quero alterar")));
    }

    /** Se o texto contém {@link CancelOptionMap#APPENDIX_PREFIX}, monta linhas {@code cancel_<appointmentId>} alinhadas ao mapa. */
    public static Optional<WhatsAppInteractiveReply> forCancelPickIfMapped(String transcriptWithAppendixOrEmpty) {
        if (transcriptWithAppendixOrEmpty == null || transcriptWithAppendixOrEmpty.isBlank()) {
            return Optional.empty();
        }
        Map<Integer, Long> map = CancelOptionMap.parseLastFromText(transcriptWithAppendixOrEmpty);
        if (map.isEmpty()) {
            return Optional.empty();
        }
        List<WhatsAppInteractiveRow> rows = new ArrayList<>();
        for (var e : new TreeMap<>(map).entrySet()) {
            int idx = e.getKey();
            long apptId = e.getValue();
            rows.add(
                    new WhatsAppInteractiveRow(
                            "cancel_" + apptId,
                            "Opção " + idx,
                            "Seleção para pedido de cancelamento"));
        }
        return Optional.of(
                new WhatsAppInteractiveReply(
                        WhatsAppInteractiveKind.CANCEL_PICK,
                        "Escolha o agendamento",
                        "Selecione a linha correspondente à lista anterior.",
                        List.of(),
                        "",
                        null,
                        "Ver opções",
                        "",
                        rows));
    }

    /** True quando este payload deve substituir o envio só com o corpo texto (lista de vagas Evolution). */
    public boolean replacesPrimaryOutboundTextSlotList() {
        return kind == WhatsAppInteractiveKind.SLOTS && !slotTimes.isEmpty();
    }
}
