package com.Edo_perfume.ScentOfASA.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.Edo_perfume.ScentOfASA.reservation.domain.PublicReservation;
import com.Edo_perfume.ScentOfASA.reservation.dto.AdminReservationListResponse;
import com.Edo_perfume.ScentOfASA.reservation.dto.AdminReservationResponse;
import com.Edo_perfume.ScentOfASA.reservation.mapper.PublicReservationMapper;

@ExtendWith(MockitoExtension.class)
class AdminReservationServiceTest {

    @Mock
    private PublicReservationMapper publicReservationMapper;

    @InjectMocks
    private AdminReservationService adminReservationService;

    @Test
    void searchReservationsReturnsMappedResults() {
        PublicReservation reservation = createReservation(8L, "PENDING");
        when(publicReservationMapper.search(LocalDate.of(2026, 5, 12), "Smith", "en"))
                .thenReturn(List.of(reservation));

        AdminReservationListResponse response = adminReservationService
                .searchReservations(LocalDate.of(2026, 5, 12), "Smith", "en");

        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getReservations()).singleElement().satisfies(item -> {
            assertThat(item.getReservationCode()).isEqualTo("SOA-8");
            assertThat(item.getCustomerEmail()).isEqualTo("john@example.com");
            assertThat(item.getPaymentStatus()).isEqualTo("SUCCEEDED");
        });
    }

    @Test
    void updateReservationStatusPersistsAndReturnsUpdatedRow() {
        PublicReservation updated = createReservation(12L, "CHECKED_IN");
        when(publicReservationMapper.updateStatus(eq(12L), eq("CHECKED_IN"), any(LocalDateTime.class))).thenReturn(1);
        when(publicReservationMapper.findById(12L)).thenReturn(updated);

        AdminReservationResponse response = adminReservationService.updateReservationStatus(12L, "checked_in");

        verify(publicReservationMapper).updateStatus(eq(12L), eq("CHECKED_IN"), any(LocalDateTime.class));
        assertThat(response.getReservationStatus()).isEqualTo("CHECKED_IN");
        assertThat(response.getReservationCode()).isEqualTo("SOA-12");
    }

    @Test
    void updateReservationStatusRejectsUnsupportedStatus() {
        assertThatThrownBy(() -> adminReservationService.updateReservationStatus(1L, "done"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported reservation status.");
    }

    private PublicReservation createReservation(Long id, String status) {
        PublicReservation reservation = new PublicReservation();
        reservation.setId(id);
        reservation.setReservationDate(LocalDate.of(2026, 5, 12));
        reservation.setTimeSlot("11:00");
        reservation.setGuideLanguage("en");
        reservation.setGuestCount(2);
        reservation.setCustomerName("John Smith");
        reservation.setCustomerEmail("john@example.com");
        reservation.setCustomerPhone("090-1111-2222");
        reservation.setPaymentStatus("SUCCEEDED");
        reservation.setNotes("VIP");
        reservation.setReservationStatus(status);
        reservation.setCreatedAt(LocalDateTime.of(2026, 5, 1, 10, 0));
        reservation.setUpdatedAt(LocalDateTime.of(2026, 5, 1, 10, 30));
        return reservation;
    }
}
