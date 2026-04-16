package com.atendimento.cerebro.application.scheduling;

/**
 * Estado efémero (ThreadLocal) das ferramentas de agendamento/cancelamento no pedido HTTP actual.
 *
 * <p>Chame {@link #resetContext()} logo após {@code cancel_appointment} concluir com sucesso para libertar listas de
 * horários e o mapa opção→ID, permitindo novamente {@code check_availability} sem resíduos do fluxo de cancelamento.
 */
public final class SchedulingToolContext {

    private SchedulingToolContext() {}

    /**
     * Limpa slots ({@link SchedulingSlotCapture}) e o estado de cancelamento ({@link SchedulingCancelSessionCapture}).
     */
    public static void resetContext() {
        SchedulingSlotCapture.clear();
        SchedulingCancelSessionCapture.resetContext();
    }
}
