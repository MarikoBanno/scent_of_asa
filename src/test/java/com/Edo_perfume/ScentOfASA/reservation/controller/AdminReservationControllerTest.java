package com.Edo_perfume.ScentOfASA.reservation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.Edo_perfume.ScentOfASA.reservation.dto.AdminReservationListResponse;
import com.Edo_perfume.ScentOfASA.reservation.dto.AdminReservationResponse;
import com.Edo_perfume.ScentOfASA.reservation.service.AdminReservationService;

@WebMvcTest(AdminReservationController.class)
@Import(AdminReservationApiExceptionHandler.class)
class AdminReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminReservationService adminReservationService;

    @Test
    void searchReservationsReturnsJson() throws Exception {
        when(adminReservationService.searchReservations(LocalDate.of(2026, 5, 12), "Smith", "en"))
                .thenReturn(new AdminReservationListResponse(
                        1,
                        List.of(new AdminReservationResponse(
                                15L,
                                "SOA-15",
                                LocalDate.of(2026, 5, 12),
                                "13:00",
                                "John Smith",
                                2,
                                "en",
                                "john@example.com",
                                "090-1234-5678",
                                "SUCCEEDED",
                                "PENDING",
                                "Notes",
                                LocalDateTime.of(2026, 5, 1, 10, 0),
                                LocalDateTime.of(2026, 5, 1, 10, 0)
                        ))
                ));

        mockMvc.perform(get("/api/admin/reservations")
                        .param("date", "2026-05-12")
                        .param("customerName", "Smith")
                        .param("guideLanguage", "en"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.reservations[0].reservationCode").value("SOA-15"));
    }

    @Test
    void updateReservationStatusReturnsUpdatedJson() throws Exception {
        when(adminReservationService.updateReservationStatus(any(), any()))
                .thenReturn(new AdminReservationResponse(
                        15L,
                        "SOA-15",
                        LocalDate.of(2026, 5, 12),
                        "13:00",
                        "John Smith",
                        2,
                        "en",
                        "john@example.com",
                        "090-1234-5678",
                        "SUCCEEDED",
                        "PAID",
                        null,
                        LocalDateTime.of(2026, 5, 1, 10, 0),
                        LocalDateTime.of(2026, 5, 1, 10, 10)
                ));

        mockMvc.perform(put("/api/admin/reservations/15/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationStatus": "PAID"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(15L))
                .andExpect(jsonPath("$.reservationStatus").value("PAID"));
    }

    @Test
    void updateReservationStatusReturnsBadRequestOnValidationError() throws Exception {
        when(adminReservationService.updateReservationStatus(any(), any()))
                .thenThrow(new IllegalArgumentException("Unsupported reservation status."));

        mockMvc.perform(put("/api/admin/reservations/15/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationStatus": "DONE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported reservation status."));
    }
}
