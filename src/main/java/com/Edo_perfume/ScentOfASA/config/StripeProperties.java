package com.Edo_perfume.ScentOfASA.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.stripe")
public class StripeProperties {

    private String publishableKey;
    private String secretKey;
    private String currency = "jpy";

    public String getPublishableKey() {
        return publishableKey;
    }

    public void setPublishableKey(String publishableKey) {
        this.publishableKey = publishableKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean isConfigured() {
        return hasText(publishableKey) && hasText(secretKey);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
