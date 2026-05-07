package com.Edo_perfume.ScentOfASA.reservation.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.Edo_perfume.ScentOfASA.config.StripeProperties;
import com.Edo_perfume.ScentOfASA.reservation.dto.PublicPaymentIntentResponse;
import com.Edo_perfume.ScentOfASA.reservation.dto.PublicReservationRequest;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;

@Service
public class StripePaymentService {

    private final StripeProperties stripeProperties;

    public StripePaymentService(StripeProperties stripeProperties) {
        this.stripeProperties = stripeProperties;
    }

    public PublicPaymentIntentResponse createPaymentIntent(PublicReservationRequest request, long amount) {
        ensureConfigured();

        try {
            Stripe.apiKey = stripeProperties.getSecretKey();
            PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                    .setAmount(amount)
                    .setCurrency(normalizeCurrency())
                    .addPaymentMethodType("card")
                    .putAllMetadata(buildReservationMetadata(request, amount));

            String normalizedEmail = normalizeEmail(request.getCustomerEmail());
            if (normalizedEmail != null) {
                builder.setReceiptEmail(normalizedEmail);
            }

            PaymentIntent paymentIntent = PaymentIntent.create(builder.build());
            return new PublicPaymentIntentResponse(
                    stripeProperties.getPublishableKey().trim(),
                    paymentIntent.getClientSecret(),
                    paymentIntent.getId(),
                    paymentIntent.getAmount(),
                    paymentIntent.getCurrency()
            );
        } catch (StripeException ex) {
            throw new PaymentGatewayException("Stripe payment setup failed.", ex);
        }
    }

    public void verifySuccessfulPayment(PublicReservationRequest request, long expectedAmount) {
        ensureConfigured();

        String paymentIntentId = normalizeOptional(request.getPaymentIntentId());
        if (paymentIntentId == null) {
            throw new IllegalArgumentException("Payment is required before creating a reservation.");
        }

        try {
            Stripe.apiKey = stripeProperties.getSecretKey();
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            if (!"succeeded".equals(paymentIntent.getStatus())) {
                throw new IllegalStateException("Payment has not completed successfully.");
            }
            if (paymentIntent.getAmount() == null || paymentIntent.getAmount() != expectedAmount) {
                throw new IllegalStateException("The payment amount no longer matches this reservation.");
            }
            if (!normalizeCurrency().equalsIgnoreCase(paymentIntent.getCurrency())) {
                throw new IllegalStateException("The payment currency no longer matches this reservation.");
            }

            Map<String, String> expectedMetadata = buildReservationMetadata(request, expectedAmount);
            for (Map.Entry<String, String> entry : expectedMetadata.entrySet()) {
                String actualValue = paymentIntent.getMetadata().get(entry.getKey());
                if (!entry.getValue().equals(actualValue)) {
                    throw new IllegalStateException("The payment details do not match the selected reservation.");
                }
            }
        } catch (StripeException ex) {
            throw new PaymentGatewayException("Stripe payment verification failed.", ex);
        }
    }

    private Map<String, String> buildReservationMetadata(PublicReservationRequest request, long amount) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("reservation_date", request.getReservationDate() == null ? "" : request.getReservationDate().toString());
        metadata.put("time_slot", normalizeOptional(request.getTimeSlot()) == null ? "" : request.getTimeSlot().trim());
        metadata.put("guide_language", normalizeOptional(request.getGuideLanguage()) == null
                ? ""
                : request.getGuideLanguage().trim().toLowerCase(Locale.ROOT));
        metadata.put("guest_count", request.getGuestCount() == null ? "" : String.valueOf(request.getGuestCount()));
        metadata.put("amount", String.valueOf(amount));
        return metadata;
    }

    private void ensureConfigured() {
        if (!stripeProperties.isConfigured()) {
            throw new PaymentGatewayException("Stripe keys are not configured on the server.");
        }
    }

    private String normalizeCurrency() {
        String currency = normalizeOptional(stripeProperties.getCurrency());
        return currency == null ? "jpy" : currency.toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
