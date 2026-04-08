package com.atendimento.cerebro.infrastructure.adapter.inbound.rest;

/** Corpo JSON de {@code PUT /v1/bot-settings}. */
public record BotSettingsPutRequest(String botPersonality) {}
