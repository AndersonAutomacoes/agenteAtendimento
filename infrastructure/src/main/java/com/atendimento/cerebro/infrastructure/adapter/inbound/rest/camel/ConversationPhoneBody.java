package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConversationPhoneBody(String phoneNumber) {}
