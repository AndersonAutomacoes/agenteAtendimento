package com.atendimento.cerebro.application.port.in;

import com.atendimento.cerebro.application.dto.ChatCommand;
import com.atendimento.cerebro.application.dto.ChatResult;

public interface ChatUseCase {

    ChatResult chat(ChatCommand command);
}
