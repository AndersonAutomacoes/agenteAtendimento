package com.atendimento.cerebro.application.crm;

import com.atendimento.cerebro.application.dto.CrmCustomerRecord;

/**
 * Texto heurístico para a ficha CRM (sem chamada extra ao modelo).
 */
public final class CrmSalesInsightFormatter {

    private CrmSalesInsightFormatter() {}

    public static String buildInsight(CrmCustomerRecord c) {
        if (c == null) {
            return "";
        }
        String intent = firstNonBlank(c.lastDetectedIntent(), c.lastIntent());
        int appts = c.totalAppointments();
        boolean converted = c.isConverted();

        if (matches(intent, "orçamento", "orcamento", "cotação", "cotacao", "preço", "preco")) {
            if (!converted && appts == 0) {
                return "Este cliente costuma perguntar sobre preços mas raramente agenda. Sugestão: ofereça um check-up gratuito ou condição especial na revisão.";
            }
            if (appts > 0) {
                return "Interesse recorrente em orçamento com "
                        + appts
                        + " agendamento(s) no histórico. Reforce valor, garantia e prazos para fechar a próxima intervenção.";
            }
            return "Pedidos de orçamento identificados; acompanhe com proposta clara e follow-up em até 24 h.";
        }
        if (matches(intent, "agendamento", "agendar", "marcar")) {
            if (!converted) {
                return "Intenção de agendamento sem marcação confirmada. Sugestão: ofereça dois horários objetivos e confirme dados do veículo.";
            }
            return "Já há conversão registada; pode sugerir revisão periódica ou serviço complementar.";
        }
        if (intent != null && !intent.isBlank()) {
            return "Última intenção detetada: "
                    + intent.strip()
                    + ". Personalize o próximo contacto com base no histórico de mensagens e agendamentos.";
        }
        return "Analise o histórico de conversa e agendamentos para definir o próximo passo comercial.";
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.strip();
        }
        if (b != null && !b.isBlank()) {
            return b.strip();
        }
        return "";
    }

    private static boolean matches(String intent, String... tokens) {
        if (intent == null || intent.isBlank()) {
            return false;
        }
        String n = intent.toLowerCase().replace("ç", "c").replace("ã", "a");
        for (String t : tokens) {
            if (n.contains(t)) {
                return true;
            }
        }
        return false;
    }
}
