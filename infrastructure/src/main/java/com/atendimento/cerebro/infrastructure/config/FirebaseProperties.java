package com.atendimento.cerebro.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cerebro.firebase")
public class FirebaseProperties {

    /** Se false, não inicializa o SDK (ex.: testes com verificador stub). */
    private boolean enabled = true;

    /** Project ID Firebase (ex.: my-proj). */
    private String projectId = "";

    /**
     * Caminho opcional para o JSON da conta de serviço. Se vazio, usa {@code GOOGLE_APPLICATION_CREDENTIALS}
     * ou credenciais default da Google.
     */
    private String serviceAccountJsonPath = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getServiceAccountJsonPath() {
        return serviceAccountJsonPath;
    }

    public void setServiceAccountJsonPath(String serviceAccountJsonPath) {
        this.serviceAccountJsonPath = serviceAccountJsonPath;
    }
}
