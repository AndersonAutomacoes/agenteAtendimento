package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

/** Corpo de {@code POST /v1/messages/human-reply} — envio manual pelo monitor com o bot desligado. */
public record HumanReplyBody(String phoneNumber, String text) {}
