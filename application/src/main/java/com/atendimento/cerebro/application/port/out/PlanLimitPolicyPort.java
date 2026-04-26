package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import java.util.Optional;

/** Política de limites quantitativos por plano (BASIC/PRO/ULTRA). */
public interface PlanLimitPolicyPort {

    /** @return limite mensal de agendamentos; vazio = ilimitado. */
    Optional<Integer> maxAppointmentsPerMonth(ProfileLevel profileLevel);
}
