package com.atendimento.cerebro.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatServiceGreetingMessageTest {

    @Test
    void isGreetingMessage_detectsCommonOpenings() {
        assertThat(ChatService.isGreetingMessage("oi")).isTrue();
        assertThat(ChatService.isGreetingMessage("Olá! tudo bem?")).isTrue();
        assertThat(ChatService.isGreetingMessage("Bom dia, preciso de ajuda")).isTrue();
        assertThat(ChatService.isGreetingMessage("BOA TARDE")).isTrue();
        assertThat(ChatService.isGreetingMessage("  hi ")).isTrue();
        assertThat(ChatService.isGreetingMessage("preciso oi de oleo")).isFalse();
        assertThat(ChatService.isGreetingMessage("   ")).isFalse();
    }
}
