package com.atendimento.cerebro.infrastructure.calendar;

import java.time.Instant;

/** Resultado de {@link GoogleCalendarService#createEvent} com dados para auditoria e CRM. */
public record GoogleCalendarCreatedEvent(String eventId, String htmlLink, Instant start, Instant end) {}
