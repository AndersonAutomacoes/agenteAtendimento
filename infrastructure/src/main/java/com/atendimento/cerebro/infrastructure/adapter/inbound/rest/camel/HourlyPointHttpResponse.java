package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

public record HourlyPointHttpResponse(String bucketStartUtc, long messageCount) {}
