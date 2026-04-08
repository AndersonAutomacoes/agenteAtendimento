package com.atendimento.cerebro.application.ingestion;

import java.util.ArrayList;
import java.util.List;

/**
 * Divide texto em janelas de {@code chunkSize} code units UTF-16 com sobreposição {@code overlap} entre inícios
 * consecutivos (avanço = {@code chunkSize - overlap}).
 */
public final class TextChunker {

    public static final int DEFAULT_CHUNK_SIZE = 1000;
    public static final int DEFAULT_OVERLAP = 200;

    private TextChunker() {}

    public static List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        if (chunkSize <= 0 || overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("chunkSize must be positive and overlap must be in [0, chunkSize)");
        }
        int step = chunkSize - overlap;
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
        }
        return chunks;
    }
}
