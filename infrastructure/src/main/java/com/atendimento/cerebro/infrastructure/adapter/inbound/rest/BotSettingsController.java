package com.atendimento.cerebro.infrastructure.adapter.inbound.rest;

import com.atendimento.cerebro.application.dto.TenantSettingsUpdateCommand;
import com.atendimento.cerebro.application.port.in.UpdateTenantSettingsUseCase;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.IngestErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Personalidade do bot por tenant — Spring MVC em {@code /v1/bot-settings} (o servlet Camel ocupa {@code /api/*}).
 * <p>
 * URL: {@code PUT /v1/bot-settings?tenantId=...} com {@code application/json} {@code {"botPersonality":"..."}}.
 */
@RestController
@RequestMapping("/v1")
public class BotSettingsController {

    private static final Logger LOG = LoggerFactory.getLogger(BotSettingsController.class);

    private final UpdateTenantSettingsUseCase updateTenantSettings;

    public BotSettingsController(UpdateTenantSettingsUseCase updateTenantSettings) {
        this.updateTenantSettings = updateTenantSettings;
    }

    @PutMapping(value = "/bot-settings", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> putBotSettings(
            @RequestParam("tenantId") String tenantId, @RequestBody BotSettingsPutRequest body) {
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.badRequest().body(new IngestErrorResponse("tenantId é obrigatório"));
        }
        if (body == null || body.botPersonality() == null) {
            return ResponseEntity.badRequest().body(new IngestErrorResponse("botPersonality é obrigatório"));
        }
        try {
            String personality = body.botPersonality();
            LOG.info("bot-settings (Spring MVC) tenantId={} personalityChars={}", tenantId.strip(), personality.length());
            updateTenantSettings.updateTenantSettings(
                    new TenantId(tenantId.strip()),
                    new TenantSettingsUpdateCommand(personality, null, null, null, null));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new IngestErrorResponse(e.getMessage()));
        } catch (Exception e) {
            LOG.warn("bot-settings falha", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new IngestErrorResponse(e.getMessage()));
        }
    }
}
