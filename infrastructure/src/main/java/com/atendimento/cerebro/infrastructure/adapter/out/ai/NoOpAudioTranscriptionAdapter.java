package com.atendimento.cerebro.infrastructure.adapter.out.ai;

import com.atendimento.cerebro.application.dto.AudioTranscriptionResult;
import com.atendimento.cerebro.application.port.out.AudioTranscriptionPort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Implementação inicial. Pode ser substituída por Whisper/OpenAI sem alterar o fluxo do chat.
 */
@Component
@ConditionalOnMissingBean(AudioTranscriptionPort.class)
public class NoOpAudioTranscriptionAdapter implements AudioTranscriptionPort {

    @Override
    public Optional<AudioTranscriptionResult> transcribe(
            TenantId tenantId, String mediaUrl, String mimeType, String providerMessageId) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
