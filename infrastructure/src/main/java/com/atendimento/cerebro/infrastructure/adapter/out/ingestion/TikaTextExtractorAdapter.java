package com.atendimento.cerebro.infrastructure.adapter.out.ingestion;

import com.atendimento.cerebro.application.port.out.TextExtractorPort;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

@Component
public class TikaTextExtractorAdapter implements TextExtractorPort {

    private final AutoDetectParser parser = new AutoDetectParser();

    private static boolean isMarkdownFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        String lower = filename.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    @Override
    public String extract(byte[] data, String filename) {
        if (data == null || data.length == 0) {
            return "";
        }
        if (isMarkdownFilename(filename)) {
            return new String(data, StandardCharsets.UTF_8).strip();
        }
        Metadata metadata = new Metadata();
        if (filename != null && !filename.isBlank()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        }
        BodyContentHandler handler = new BodyContentHandler(-1);
        ParseContext context = new ParseContext();
        try (InputStream input = new ByteArrayInputStream(data)) {
            parser.parse(input, handler, metadata, context);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao extrair texto do ficheiro: " + filename, e);
        }
        return handler.toString().strip();
    }
}
