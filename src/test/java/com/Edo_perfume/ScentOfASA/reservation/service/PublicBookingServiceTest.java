package com.Edo_perfume.ScentOfASA.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.Edo_perfume.ScentOfASA.holiday.dto.HolidayCalendarDayResponse;
import com.Edo_perfume.ScentOfASA.holiday.service.StoreHolidayService;
import com.Edo_perfume.ScentOfASA.reservation.domain.PublicReservation;
import com.Edo_perfume.ScentOfASA.reservation.dto.PublicAvailabilityResponse;
import com.Edo_perfume.ScentOfASA.reservation.dto.PublicReservationRequest;
import com.Edo_perfume.ScentOfASA.reservation.dto.PublicReservationResponse;
import com.Edo_perfume.ScentOfASA.reservation.mapper.PublicReservationMapper;
import com.Edo_perfume.ScentOfASA.slot.domain.AdminSlot;
import com.Edo_perfume.ScentOfASA.slot.mapper.AdminSlotMapper;
import com.Edo_perfume.ScentOfASA.slot.service.AdminSlotService;

@ExtendWith(MockitoExtension.class)
class PublicBookingServiceTest {

    @Mock
    private PublicReservationMapper publicReservationMapper;

    @Mock
    private AdminSlotMapper adminSlotMapper;

    @Mock
    private AdminSlotService adminSlotService;

    @Mock
    private StoreHolidayService storeHolidayService;

    @Mock
    private StripePaymentService stripePaymentService;

    @InjectMocks
    private PublicBookingService publicBookingService;

    @Test
    void getAvailabilityMarksClosedDaysAndLimitedSlots() {
        when(storeHolidayService.findMonthlyHolidays(2026, 5, "ja"))
                .thenReturn(List.of(new HolidayCalendarDayResponse(1L, LocalDate.of(2026, 5, 13), "CLOSED", "Store closed", "ja")));

        PublicReservation bookedReservation = new PublicReservation();
        bookedReservation.setReservationDate(LocalDate.of(2026, 5, 12));
        bookedReservation.setTimeSlot("13:00");
        bookedReservation.setGuestCount(3);
        when(publicReservationMapper.findByMonth(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "ja"))
                .thenReturn(List.of(bookedReservation));
        when(adminSlotMapper.findByMonthAndLanguage(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "ja"))
                .thenReturn(List.of(
                        createAdminSlot(LocalDate.of(2026, 5, 12), "11:00", "ja", 1L, "OPEN"),
                        createAdminSlot(LocalDate.of(2026, 5, 12), "13:00", "ja", 1L, "OPEN"),
                        createAdminSlot(LocalDate.of(2026, 5, 12), "15:30", "ja", null, "STOPPED"),
                        createAdminSlot(LocalDate.of(2026, 5, 13), "11:00", "ja", 1L, "OPEN")
                ));

        PublicAvailabilityResponse response = publicBookingService.getAvailability(2026, 5, "ja");

        assertThat(response.getLanguage()).isEqualTo("ja");
        assertThat(response.getDays()).hasSize(31);
        assertThat(response.getDays().stream()
                .filter(day -> day.getDate().equals(LocalDate.of(2026, 5, 13)))
                .findFirst()
                .orElseThrow()
                .isClosed()).isTrue();
        assertThat(response.getDays().stream()
                .filter(day -> day.getDate().equals(LocalDate.of(2026, 5, 12)))
                .findFirst()
                .orElseThrow()
                .getSlots())
                .anySatisfy(slot -> {
                    assertThat(slot.getTimeSlot()).isEqualTo("13:00");
                    assertThat(slot.getStatus()).isEqualTo("LIMITED");
                    assertThat(slot.isAvailable()).isTrue();
                    assertThat(slot.getRemainingCapacity()).isEqualTo(1);
                });
    }

    @Test
    void getAvailabilityMarksTodayAndTomorrowAsBookingClosed() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());

        when(publicReservationMapper.findByMonth(monthStart, monthEnd, "ja"))
                .thenReturn(List.of());
        when(adminSlotMapper.findByMonthAndLanguage(monthStart, monthEnd, "ja"))
                .thenReturn(List.of(
                        createAdminSlot(today, "11:00", "ja", 1L, "OPEN"),
                        createAdminSlot(tomorrow, "13:00", "ja", 1L, "OPEN")
                ));
        when(storeHolidayService.findMonthlyHolidays(today.getYear(), today.getMonthValue(), "ja"))
                .thenReturn(List.of());

        PublicAvailabilityResponse response = publicBookingService.getAvailability(today.getYear(), today.getMonthValue(), "ja");

        assertThat(response.getDays()).anySatisfy(day -> {
            if (day.getDate().equals(today)) {
                assertThat(day.isBookingClosed()).isTrue();
                assertThat(day.getReason()).isEqualTo("RESERVATION_CLOSED");
            }
        });
        assertThat(response.getDays()).anySatisfy(day -> {
            if (day.getDate().equals(tomorrow)) {
                assertThat(day.isBookingClosed()).isTrue();
                assertThat(day.getSlots()).allSatisfy(slot -> assertThat(slot.getStatus()).isEqualTo("BOOKING_CLOSED"));
            }
        });
    }

    @Test
    void prepareReservationPaymentRejectsClosedDates() {
        PublicReservationRequest request = baseRequest();
        request.setReservationDate(LocalDate.of(2026, 5, 13));

        when(storeHolidayService.isHoliday(LocalDate.of(2026, 5, 13), "ja")).thenReturn(true);

        assertThatThrownBy(() -> publicBookingService.prepareReservationPayment(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The selected date is closed for reservations.");
    }

    @Test
    void prepareReservationPaymentDoesNotRequireCustomerDetails() {
        PublicReservationRequest request = new PublicReservationRequest();
        request.setReservationDate(LocalDate.of(2026, 5, 12));
        request.setTimeSlot("13:00");
        request.setGuideLanguage("ja");
        request.setGuestCount(2);

        when(storeHolidayService.isHoliday(LocalDate.of(2026, 5, 12), "ja")).thenReturn(false);
        when(adminSlotMapper.findByDateTimeAndLanguage(LocalDate.of(2026, 5, 12), "13:00", "ja"))
                .thenReturn(createAdminSlot(LocalDate.of(2026, 5, 12), "13:00", "ja", 1L, "OPEN"));
        when(publicReservationMapper.sumGuestCountByDateAndTime(LocalDate.of(2026, 5, 12), "13:00", "ja"))
                .thenReturn(0);

        long amount = publicBookingService.prepareReservationPayment(request);

        assertThat(amount).isEqualTo(26400L);
    }

    @Test
    void createReservationNormalizesFieldsBeforeInsert() {
        PublicReservationRequest request = baseRequest();
        request.setReservationDate(LocalDate.of(2026, 5, 12));
        request.setTimeSlot(" 13:00 ");
        request.setGuideLanguage(" JA ");
        request.setGuestCount(3);
        request.setCustomerName(" Hanako Yamada ");
        request.setCustomerEmail(" HANAKO@EXAMPLE.COM ");
        request.setCustomerPhone(" 090-1234-5678 ");
        request.setNotes(" Allergic to smoke ");
        request.setPaymentIntentId(" pi_test_123 ");

        when(storeHolidayService.isHoliday(LocalDate.of(2026, 5, 12), "ja")).thenReturn(false);
        when(adminSlotMapper.findByDateTimeAndLanguage(LocalDate.of(2026, 5, 12), "13:00", "ja"))
                .thenReturn(createAdminSlot(LocalDate.of(2026, 5, 12), "13:00", "ja", 1L, "OPEN"));
        when(publicReservationMapper.sumGuestCountByDateAndTime(LocalDate.of(2026, 5, 12), "13:00", "ja"))
                .thenReturn(1);
        when(publicReservationMapper.findByPaymentIntentId("pi_test_123")).thenReturn(null);

        PublicReservationResponse response = publicBookingService.createReservation(request);
        ArgumentCaptor<PublicReservation> captor = ArgumentCaptor.forClass(PublicReservation.class);

        verify(stripePaymentService).verifySuccessfulPayment(request, 39600L);
        verify(publicReservationMapper).insert(captor.capture());
        PublicReservation inserted = captor.getValue();
        inserted.setId(7L);

        assertThat(inserted.getGuideLanguage()).isEqualTo("ja");
        assertThat(inserted.getTimeSlot()).isEqualTo("13:00");
        assertThat(inserted.getCustomerEmail()).isEqualTo("hanako@example.com");
        assertThat(inserted.getCustomerPhone()).isEqualTo("090-1234-5678");
        assertThat(inserted.getNotes()).isEqualTo("Allergic to smoke");
        assertThat(inserted.getPaymentIntentId()).isEqualTo("pi_test_123");
        assertThat(inserted.getPaymentStatus()).isEqualTo("SUCCEEDED");
        assertThat(response.getStatus()).isEqualTo("PAID");
    }

    @Test
    void prepareReservationPaymentRejectsWhenGuestCountExceedsRemainingCapacity() {
        PublicReservationRequest request = baseRequest();
        request.setGuestCount(3);

        when(storeHolidayService.isHoliday(LocalDate.of(2026, 5, 12), "ja")).thenReturn(false);
        when(adminSlotMapper.findByDateTimeAndLanguage(LocalDate.of(2026, 5, 12), "13:00", "ja"))
                .thenReturn(createAdminSlot(LocalDate.of(2026, 5, 12), "13:00", "ja", 1L, "OPEN"));
        when(publicReservationMapper.sumGuestCountByDateAndTime(LocalDate.of(2026, 5, 12), "13:00", "ja"))
                .thenReturn(2);

        assertThatThrownBy(() -> publicBookingService.prepareReservationPayment(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The selected slot is no longer available.");
    }

    @Test
    void prepareReservationPaymentRejectsTodayAndTomorrowBookingWindow() {
        PublicReservationRequest request = baseRequest();
        request.setReservationDate(LocalDate.now().plusDays(1));

        when(storeHolidayService.isHoliday(request.getReservationDate(), "ja")).thenReturn(false);

        assertThatThrownBy(() -> publicBookingService.prepareReservationPayment(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Reservations for the selected date have already closed.");
    }

    @Test
    void createReservationRejectsReusedPaymentIntent() {
        PublicReservationRequest request = baseRequest();
        request.setPaymentIntentId("pi_test_123");

        when(storeHolidayService.isHoliday(LocalDate.of(2026, 5, 12), "ja")).thenReturn(false);
        when(adminSlotMapper.findByDateTimeAndLanguage(LocalDate.of(2026, 5, 12), "13:00", "ja"))
                .thenReturn(createAdminSlot(LocalDate.of(2026, 5, 12), "13:00", "ja", 1L, "OPEN"));
        when(publicReservationMapper.sumGuestCountByDateAndTime(LocalDate.of(2026, 5, 12), "13:00", "ja"))
                .thenReturn(0);
        when(publicReservationMapper.findByPaymentIntentId("pi_test_123")).thenReturn(new PublicReservation());

        assertThatThrownBy(() -> publicBookingService.createReservation(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("This payment has already been used for a reservation.");

        verify(publicReservationMapper, never()).insert(any());
    }

    private PublicReservationRequest baseRequest() {
        PublicReservationRequest request = new PublicReservationRequest();
        request.setReservationDate(LocalDate.of(2026, 5, 12));
        request.setTimeSlot("13:00");
        request.setGuideLanguage("ja");
        request.setGuestCount(2);
        request.setCustomerName("Hanako Yamada");
        request.setCustomerEmail("hanako@example.com");
        request.setPaymentIntentId("pi_test_123");
        return request;
    }

    private AdminSlot createAdminSlot(LocalDate date, String timeSlot, String language, Long guideStaffId, String slotStatus) {
        AdminSlot slot = new AdminSlot();
        slot.setSlotDate(date);
        slot.setTimeSlot(timeSlot);
        slot.setGuideLanguage(language);
        slot.setGuideStaffId(guideStaffId);
        slot.setSlotStatus(slotStatus);
        return slot;
    }
}
