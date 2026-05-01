package com.atendimento.cerebro.infrastructure.adapter.out.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TikaTextExtractorAdapterTest {

    private final TikaTextExtractorAdapter adapter = new TikaTextExtractorAdapter();

    @Test
    void extract_markdownUtf8_returnsDecodedText() {
        String md = "# Title\n\nHello **world**.";
        byte[] bytes = md.getBytes(StandardCharsets.UTF_8);
        assertThat(adapter.extract(bytes, "notes.md")).isEqualTo(md);
        assertThat(adapter.extract(bytes, "PAGE.MARKDOWN")).isEqualTo(md);
    }

    @Test
    void extract_plainTxt_stillUsesTika() {
        byte[] bytes = "plain line".getBytes(StandardCharsets.UTF_8);
        assertThat(adapter.extract(bytes, "file.txt")).contains("plain line");
    }
}
