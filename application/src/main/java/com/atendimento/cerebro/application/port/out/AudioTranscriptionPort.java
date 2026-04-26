package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.application.dto.AudioTranscriptionResult;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.Optional;

public interface AudioTranscriptionPort {
    Optional<AudioTranscriptionResult> transcribe(
            TenantId tenantId, String mediaUrl, String mimeType, String providerMessageId);
}
