package com.atendimento.cerebro.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cerebro.analytics.chat")
public class ChatAnalyticsProperties {

    private boolean enabled = true;
    private int historyLookbackDays = 2;
    private int maxHistoryMessages = 20;
    private int maxTranscriptChars = 12000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getHistoryLookbackDays() {
        return historyLookbackDays;
    }

    public void setHistoryLookbackDays(int historyLookbackDays) {
        this.historyLookbackDays = historyLookbackDays;
    }

    public int getMaxHistoryMessages() {
        return maxHistoryMessages;
    }

    public void setMaxHistoryMessages(int maxHistoryMessages) {
        this.maxHistoryMessages = maxHistoryMessages;
    }

    public int getMaxTranscriptChars() {
        return maxTranscriptChars;
    }

    public void setMaxTranscriptChars(int maxTranscriptChars) {
        this.maxTranscriptChars = maxTranscriptChars;
    }
}
