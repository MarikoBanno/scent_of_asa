package com.Edo_perfume.ScentOfASA.reservation.dto;

public class PublicPaymentIntentResponse {

    private String publishableKey;
    private String clientSecret;
    private String paymentIntentId;
    private Long amount;
    private String currency;

    public PublicPaymentIntentResponse() {
    }

    public PublicPaymentIntentResponse(String publishableKey, String clientSecret, String paymentIntentId, Long amount,
                                       String currency) {
        this.publishableKey = publishableKey;
        this.clientSecret = clientSecret;
        this.paymentIntentId = paymentIntentId;
        this.amount = amount;
        this.currency = currency;
    }

    public String getPublishableKey() {
        return publishableKey;
    }

    public void setPublishableKey(String publishableKey) {
        this.publishableKey = publishableKey;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    public void setPaymentIntentId(String paymentIntentId) {
        this.paymentIntentId = paymentIntentId;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
