package com.atendimento.cerebro.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cerebro.analytics.intents")
public class AnalyticsIntentClassificationProperties {

    private boolean enabled = true;
    /** Dispara classificação por IA a cada N mensagens (total USER+ASSISTANT) na janela. */
    private int messageThreshold = 6;
    private int sessionLookbackDays = 7;
    private int maxTranscriptMessages = 48;
    private int maxTranscriptChars = 12_000;
    private boolean inactivityClassificationEnabled = true;
    /** Minutos sem mensagens para considerar conversa encerrada (job periódico). */
    private int inactivityMinutes = 30;
    private int maxClassificationsPerRun = 200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMessageThreshold() {
        return messageThreshold;
    }

    public void setMessageThreshold(int messageThreshold) {
        this.messageThreshold = messageThreshold;
    }

    public int getSessionLookbackDays() {
        return sessionLookbackDays;
    }

    public void setSessionLookbackDays(int sessionLookbackDays) {
        this.sessionLookbackDays = sessionLookbackDays;
    }

    public int getMaxTranscriptMessages() {
        return maxTranscriptMessages;
    }

    public void setMaxTranscriptMessages(int maxTranscriptMessages) {
        this.maxTranscriptMessages = maxTranscriptMessages;
    }

    public int getMaxTranscriptChars() {
        return maxTranscriptChars;
    }

    public void setMaxTranscriptChars(int maxTranscriptChars) {
        this.maxTranscriptChars = maxTranscriptChars;
    }

    public boolean isInactivityClassificationEnabled() {
        return inactivityClassificationEnabled;
    }

    public void setInactivityClassificationEnabled(boolean inactivityClassificationEnabled) {
        this.inactivityClassificationEnabled = inactivityClassificationEnabled;
    }

    public int getInactivityMinutes() {
        return inactivityMinutes;
    }

    public void setInactivityMinutes(int inactivityMinutes) {
        this.inactivityMinutes = inactivityMinutes;
    }

    public int getMaxClassificationsPerRun() {
        return maxClassificationsPerRun;
    }

    public void setMaxClassificationsPerRun(int maxClassificationsPerRun) {
        this.maxClassificationsPerRun = maxClassificationsPerRun;
    }
}
