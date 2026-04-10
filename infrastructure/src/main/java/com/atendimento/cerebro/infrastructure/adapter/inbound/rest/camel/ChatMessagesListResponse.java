package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import java.util.List;
import java.util.Map;

public record ChatMessagesListResponse(
        List<ChatMessageItemResponse> messages, Map<String, Boolean> botEnabledByPhone) {}
