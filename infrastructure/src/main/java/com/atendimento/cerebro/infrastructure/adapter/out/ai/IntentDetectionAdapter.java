package com.atendimento.cerebro.infrastructure.adapter.out.ai;

import com.atendimento.cerebro.application.port.out.IntentDetectionPort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IntentDetectionAdapter implements IntentDetectionPort {

    private static final Logger LOG = LoggerFactory.getLogger(IntentDetectionAdapter.class);

    private static final String INTENT_SYSTEM =
            "Classify the user message into exactly one lowercase English label from this list: "
                    + "greeting, support, sales, complaint, scheduling, other. "
                    + "Reply with only that single word, no punctuation or explanation.";

    private final GoogleGenAiChatModel chatModel;
    private final String chatModelName;

    public IntentDetectionAdapter(
            @Autowired(required = false) @Qualifier("googleGenAiChatModel") GoogleGenAiChatModel chatModel,
            @Value("${spring.ai.google.genai.chat.options.model:gemini-1.5-flash}") String chatModelName) {
        this.chatModel = chatModel;
        this.chatModelName = chatModelName != null && !chatModelName.isBlank() ? chatModelName : GeminiChatEngineAdapter.DEFAULT_MODEL;
    }

    @Override
    public Optional<String> detectIntent(TenantId tenantId, String userMessageText) {
        if (chatModel == null) {
            return Optional.empty();
        }
        if (tenantId == null || userMessageText == null || userMessageText.isBlank()) {
            return Optional.empty();
        }
        String trimmed = userMessageText.strip();
        if (trimmed.length() > 2000) {
            trimmed = trimmed.substring(0, 2000);
        }
        try {
            GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().model(chatModelName).build();
            Prompt prompt = new Prompt(List.of(new SystemMessage(INTENT_SYSTEM), new UserMessage(trimmed)), options);
            ChatResponse response = chatModel.call(prompt);
            String raw = response.getResult().getOutput().getText();
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            String label = normalize(raw);
            if (label.isEmpty()) {
                return Optional.empty();
            }
            if (label.length() > 128) {
                label = label.substring(0, 128);
            }
            return Optional.of(label);
        } catch (Exception e) {
            LOG.debug("intent detection failed tenant={}: {}", tenantId.value(), e.toString());
            return Optional.empty();
        }
    }

    private static String normalize(String raw) {
        String s = raw.strip().toLowerCase(Locale.ROOT);
        int space = s.indexOf(' ');
        if (space > 0) {
            s = s.substring(0, space);
        }
        int nl = s.indexOf('\n');
        if (nl > 0) {
            s = s.substring(0, nl);
        }
        return s.replaceAll("[^a-z_]", "");
    }
}
