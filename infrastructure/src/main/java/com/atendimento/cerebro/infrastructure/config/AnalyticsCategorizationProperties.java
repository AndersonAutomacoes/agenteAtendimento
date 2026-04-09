package com.atendimento.cerebro.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cerebro.analytics.categorization")
public class AnalyticsCategorizationProperties {

    private boolean enabled = true;
    private int maxConversationsPerRun = 500;
    private int maxUserTextChars = 4000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxConversationsPerRun() {
        return maxConversationsPerRun;
    }

    public void setMaxConversationsPerRun(int maxConversationsPerRun) {
        this.maxConversationsPerRun = maxConversationsPerRun;
    }

    public int getMaxUserTextChars() {
        return maxUserTextChars;
    }

    public void setMaxUserTextChars(int maxUserTextChars) {
        this.maxUserTextChars = maxUserTextChars;
    }
}
