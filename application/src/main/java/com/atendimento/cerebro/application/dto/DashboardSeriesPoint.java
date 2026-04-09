package com.atendimento.cerebro.application.dto;

/** Ponto da série temporal (início do bucket em ISO-8601 UTC). */
public record DashboardSeriesPoint(String bucketStart, long count) {}
