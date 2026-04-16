package com.atendimento.cerebro.application.scheduling;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mapeamento opção (1, 2, 3) → {@code appointmentId} (PK em {@code tenant_appointments.id}) no pedido actual
 * (ThreadLocal), preenchido quando a ferramenta {@code get_active_appointments} corre neste mesmo request. O histórico
 * persistido continua a levar {@code [cancel_option_map:…]} para resolver escolhas em turnos seguintes.
 */
public final class SchedulingCancelSessionCapture {

    private static final ThreadLocal<Map<Integer, Long>> OPTION_TO_APPOINTMENT = new ThreadLocal<>();
    /** Quando o cliente ainda não enviou o índice da lista (1, 2, 3…) a cancelar. */
    private static final ThreadLocal<Boolean> WAITING_FOR_CANCELLATION_CHOICE = new ThreadLocal<>();

    private SchedulingCancelSessionCapture() {}

    public static void setOptionToAppointmentId(Map<Integer, Long> optionOneBasedToAppointmentId) {
        if (optionOneBasedToAppointmentId == null || optionOneBasedToAppointmentId.isEmpty()) {
            OPTION_TO_APPOINTMENT.remove();
            return;
        }
        OPTION_TO_APPOINTMENT.set(new LinkedHashMap<>(optionOneBasedToAppointmentId));
    }

    public static void setWaitingForCancellationChoice(boolean waiting) {
        if (!waiting) {
            WAITING_FOR_CANCELLATION_CHOICE.remove();
        } else {
            WAITING_FOR_CANCELLATION_CHOICE.set(true);
        }
    }

    public static boolean isWaitingForCancellationChoice() {
        return Boolean.TRUE.equals(WAITING_FOR_CANCELLATION_CHOICE.get());
    }

    /** Cópia de leitura; vazio se não houver mapa neste pedido. */
    public static Map<Integer, Long> getOptionToAppointmentId() {
        return getSelectionMap();
    }

    /** Alias: opção visível (1…) → {@code tenant_appointments.id}. */
    public static Map<Integer, Long> getSelectionMap() {
        Map<Integer, Long> m = OPTION_TO_APPOINTMENT.get();
        if (m == null || m.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(m));
    }

    /**
     * Liberta o mapa de opções e o estado «à espera de índice» — equivale a sair do modo cancelamento na sessão HTTP.
     */
    public static void resetContext() {
        OPTION_TO_APPOINTMENT.remove();
        WAITING_FOR_CANCELLATION_CHOICE.remove();
    }

    public static void clear() {
        resetContext();
    }
}
