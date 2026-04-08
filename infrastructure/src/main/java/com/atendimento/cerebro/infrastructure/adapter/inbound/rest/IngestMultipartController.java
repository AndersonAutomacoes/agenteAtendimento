package com.atendimento.cerebro.infrastructure.adapter.inbound.rest;

import com.atendimento.cerebro.application.port.in.IngestionUseCase;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.IngestErrorResponse;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.IngestHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ingestão multipart via <strong>Spring MVC</strong> (não pelo servlet Camel em {@code /api/*}).
 * <p>
 * O servlet do Camel está mapeado em {@code /api/*}; pedidos multipart aí precisam de
 * {@code MultipartConfig} no servlet Camel (registo fora do {@code ServletRegistrationBean} típico), por isso
 * este endpoint usa o {@code DispatcherServlet} em {@code /v1/ingest}, onde o multipart do Spring funciona de
 * forma fiável.
 * <p>
 * URL: {@code POST /v1/ingest?tenantId=...} com {@code multipart/form-data}, campo de ficheiro {@code file}.
 */
@RestController
@RequestMapping("/v1")
public class IngestMultipartController {

    private static final Logger LOG = LoggerFactory.getLogger(IngestMultipartController.class);

    private final IngestionUseCase ingestionUseCase;

    public IngestMultipartController(IngestionUseCase ingestionUseCase) {
        this.ingestionUseCase = ingestionUseCase;
    }

    @PostMapping(value = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> ingest(
            @RequestParam("tenantId") String tenantId, @RequestPart("file") MultipartFile file) {
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.badRequest().body(new IngestErrorResponse("tenantId é obrigatório"));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(new IngestErrorResponse("ficheiro 'file' é obrigatório"));
        }
        try {
            String name = file.getOriginalFilename();
            if (name == null || name.isBlank()) {
                name = "upload";
            }
            byte[] bytes = file.getBytes();
            LOG.info("ingest (Spring MVC) tenantId={} filename={} size={}", tenantId.strip(), name, bytes.length);
            int n = ingestionUseCase.ingest(new TenantId(tenantId.strip()), bytes, name);
            return ResponseEntity.ok(new IngestHttpResponse(n));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new IngestErrorResponse(e.getMessage()));
        } catch (Exception e) {
            LOG.warn("ingest falha", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new IngestErrorResponse(e.getMessage()));
        }
    }
}
