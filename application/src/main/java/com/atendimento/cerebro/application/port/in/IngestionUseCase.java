package com.atendimento.cerebro.application.port.in;

import com.atendimento.cerebro.domain.tenant.TenantId;

/** Caso de uso: ingestão de ficheiros na base de conhecimento vetorial por tenant. */
public interface IngestionUseCase {

    /**
     * Extrai texto, divide em chunks e persiste com isolamento por {@code tenantId}.
     *
     * @param originalFilename nome original (extensão guia o detetor de tipo, ex.: PDF vs TXT)
     * @param fileSizeBytes tamanho declarado do ficheiro (metadados na listagem)
     */
    int ingest(TenantId tenantId, byte[] fileContent, String originalFilename, long fileSizeBytes);
}
