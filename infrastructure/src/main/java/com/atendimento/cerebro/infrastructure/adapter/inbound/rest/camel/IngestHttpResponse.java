package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

/** Resposta JSON do endpoint {@code POST /v1/ingest} (Spring MVC; ver {@link com.atendimento.cerebro.infrastructure.adapter.inbound.rest.IngestMultipartController}). */
public record IngestHttpResponse(int chunksIngested) {}
