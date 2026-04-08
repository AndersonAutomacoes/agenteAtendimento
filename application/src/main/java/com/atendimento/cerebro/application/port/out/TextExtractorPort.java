package com.atendimento.cerebro.application.port.out;

/** Extrai texto bruto de bytes (PDF, TXT, etc.) com base no nome do ficheiro. */
public interface TextExtractorPort {

    String extract(byte[] data, String filename);
}
