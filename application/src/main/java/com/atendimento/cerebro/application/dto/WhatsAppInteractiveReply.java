package com.atendimento.cerebro.application.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Mensagem interativa (ex.: Evolution sendButtons): texto principal + horários para botões de resposta.
 * No máximo três botões são usados no envio; horários extra podem constar na descrição.
 *
 * @param verificationText linha opcional enviada antes dos botões (ex.: "Verificando disponibilidade para dd/MM/yyyy… 🕒").
 * @param requestedDate data da consulta de disponibilidade (para lista formatada “premium” no WhatsApp).
 */
public record WhatsAppInteractiveReply(
        String title, String description, List<String> slotTimes, String verificationText, LocalDate requestedDate) {
    public WhatsAppInteractiveReply {
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        slotTimes = slotTimes == null ? List.of() : List.copyOf(slotTimes);
        verificationText = verificationText == null ? "" : verificationText;
    }

    /** Compatível com construção sem texto de verificação explícito. */
    public WhatsAppInteractiveReply(String title, String description, List<String> slotTimes) {
        this(title, description, slotTimes, "", null);
    }

    public WhatsAppInteractiveReply(String title, String description, List<String> slotTimes, String verificationText) {
        this(title, description, slotTimes, verificationText, null);
    }
}
