package com.Edo_perfume.ScentOfASA.reservation.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Edo_perfume.ScentOfASA.reservation.domain.PublicReservation;
import com.Edo_perfume.ScentOfASA.reservation.dto.AdminReservationListResponse;
import com.Edo_perfume.ScentOfASA.reservation.dto.AdminReservationResponse;
import com.Edo_perfume.ScentOfASA.reservation.mapper.PublicReservationMapper;

@Service
@Transactional
public class AdminReservationService {

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("ja", "en");
    private static final Set<String> SUPPORTED_STATUSES = Set.of(
            "PENDING",
            "CONFIRMED",
            "PAID",
            "CHECKED_IN",
            "CANCELLED",
            "NO_SHOW"
    );

    private final PublicReservationMapper publicReservationMapper;

    public AdminReservationService(PublicReservationMapper publicReservationMapper) {
        this.publicReservationMapper = publicReservationMapper;
    }

    @Transactional(readOnly = true)
    public AdminReservationListResponse searchReservations(LocalDate reservationDate,
                                                           String customerName,
                                                           String guideLanguage) {
        String normalizedName = normalizeOptional(customerName);
        String normalizedLanguage = normalizeLanguageFilter(guideLanguage);

        List<AdminReservationResponse> reservations = publicReservationMapper
                .search(reservationDate, normalizedName, normalizedLanguage)
                .stream()
                .map(this::toResponse)
                .toList();

        return new AdminReservationListResponse(reservations.size(), reservations);
    }

    public AdminReservationResponse updateReservationStatus(Long id, String reservationStatus) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("Reservation id is required.");
        }
        String normalizedStatus = normalizeStatus(reservationStatus);

        int updated = publicReservationMapper.updateStatus(id, normalizedStatus, LocalDateTime.now());
        if (updated == 0) {
            throw new IllegalArgumentException("Reservation was not found.");
        }

        PublicReservation reservation = publicReservationMapper.findById(id);
        if (reservation == null) {
            throw new IllegalArgumentException("Reservation was not found.");
        }

        return toResponse(reservation);
    }

    private AdminReservationResponse toResponse(PublicReservation reservation) {
        return new AdminReservationResponse(
                reservation.getId(),
                buildReservationCode(reservation.getId()),
                reservation.getReservationDate(),
                reservation.getTimeSlot(),
                reservation.getCustomerName(),
                reservation.getGuestCount(),
                reservation.getGuideLanguage(),
                reservation.getCustomerEmail(),
                reservation.getCustomerPhone(),
                reservation.getPaymentStatus(),
                reservation.getReservationStatus(),
                reservation.getNotes(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt()
        );
    }

    private String buildReservationCode(Long id) {
        return id == null ? null : "SOA-" + id;
    }

    private String normalizeLanguageFilter(String guideLanguage) {
        String normalized = normalizeOptional(guideLanguage);
        if (normalized == null || "all".equalsIgnoreCase(normalized)) {
            return null;
        }

        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_LANGUAGES.contains(normalized)) {
            throw new IllegalArgumentException("guideLanguage must be 'ja' or 'en'.");
        }
        return normalized;
    }

    private String normalizeStatus(String reservationStatus) {
        String normalized = normalizeOptional(reservationStatus);
        if (normalized == null) {
            throw new IllegalArgumentException("reservationStatus is required.");
        }

        normalized = normalized.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported reservation status.");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
