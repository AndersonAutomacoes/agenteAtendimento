package com.atendimento.cerebro.application.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkerTest {

    @Test
    void emptyYieldsEmpty() {
        assertThat(TextChunker.chunk("", 1000, 200)).isEmpty();
        assertThat(TextChunker.chunk(null, 1000, 200)).isEmpty();
    }

    @Test
    void shortTextSingleChunk() {
        String s = "a".repeat(100);
        List<String> chunks = TextChunker.chunk(s, 1000, 200);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).hasSize(100);
    }

    @Test
    void overlapAdvancesByChunkSizeMinusOverlap() {
        String s = "x".repeat(2500);
        List<String> chunks = TextChunker.chunk(s, 1000, 200);
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).hasSize(1000);
        assertThat(chunks.get(1)).hasSize(1000);
        assertThat(chunks.get(2)).hasSize(900);
    }

    @Test
    void invalidOverlapThrows() {
        assertThatThrownBy(() -> TextChunker.chunk("abc", 1000, 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
