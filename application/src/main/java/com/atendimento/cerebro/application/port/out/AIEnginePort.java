package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.application.dto.AICompletionRequest;
import com.atendimento.cerebro.application.dto.AICompletionResponse;

public interface AIEnginePort {

    AICompletionResponse complete(AICompletionRequest request);
}
