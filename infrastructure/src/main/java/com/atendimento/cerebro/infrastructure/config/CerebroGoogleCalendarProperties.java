package com.atendimento.cerebro.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cerebro.google.calendar")
public class CerebroGoogleCalendarProperties {

    /** Quando true, não chama a Google Calendar API (desenvolvimento sem SA). Alinhar com {@code application.yml}. */
    private boolean mock = false;

    /** Caminho absoluto ou montado no container para o JSON da service account global. Se vazio, usa credentialsClasspath. */
    private String serviceAccountJsonPath = "";

    /**
     * ID do calendário Google (ex.: e-mail ou ID do calendário partilhado). Se vazio, usa {@code client_email} do JSON
     * da service account.
     */
    private String calendarId = "";

    /** Recurso no classpath (ex.: {@code credentials.json} em {@code src/main/resources} do módulo que arranca a app). */
    private String credentialsClasspath = "credentials.json";

    private String zone = "America/Sao_Paulo";
    private int slotMinutes = 30;
    private int workStartHour = 9;
    private int workEndHour = 18;

    public boolean isMock() {
        return mock;
    }

    public void setMock(boolean mock) {
        this.mock = mock;
    }

    public String getServiceAccountJsonPath() {
        return serviceAccountJsonPath;
    }

    public void setServiceAccountJsonPath(String serviceAccountJsonPath) {
        this.serviceAccountJsonPath = serviceAccountJsonPath;
    }

    public String getCalendarId() {
        return calendarId;
    }

    public void setCalendarId(String calendarId) {
        this.calendarId = calendarId;
    }

    public String getCredentialsClasspath() {
        return credentialsClasspath;
    }

    public void setCredentialsClasspath(String credentialsClasspath) {
        this.credentialsClasspath = credentialsClasspath;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public int getSlotMinutes() {
        return slotMinutes;
    }

    public void setSlotMinutes(int slotMinutes) {
        this.slotMinutes = slotMinutes;
    }

    public int getWorkStartHour() {
        return workStartHour;
    }

    public void setWorkStartHour(int workStartHour) {
        this.workStartHour = workStartHour;
    }

    public int getWorkEndHour() {
        return workEndHour;
    }

    public void setWorkEndHour(int workEndHour) {
        this.workEndHour = workEndHour;
    }
}
