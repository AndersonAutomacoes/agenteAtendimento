package com.atendimento.cerebro.infrastructure.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atendimento.cerebro.application.ai.AiChatProvider;
import com.atendimento.cerebro.application.dto.AICompletionRequest;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ResponseStatusException;

class RoutingAIEnginePortTest {

    private static <T> ObjectProvider<T> providerOf(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getIfAvailable() {
                return value;
            }
        };
    }

    @Test
    void complete_throwsBadRequestWhenOpenAiNotConfigured() {
        var router = new RoutingAIEnginePort(providerOf(null), providerOf(null));
        var req = new AICompletionRequest(
                new TenantId("t1"), List.of(), List.of(), "hi", "", AiChatProvider.OPENAI);

        assertThatThrownBy(() -> router.complete(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason()).contains("OpenAI"));
    }
}
