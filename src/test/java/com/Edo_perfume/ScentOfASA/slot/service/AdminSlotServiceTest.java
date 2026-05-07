package com.Edo_perfume.ScentOfASA.slot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.Edo_perfume.ScentOfASA.guide.domain.GuideStaff;
import com.Edo_perfume.ScentOfASA.guide.mapper.GuideStaffMapper;
import com.Edo_perfume.ScentOfASA.holiday.dto.HolidayCalendarDayResponse;
import com.Edo_perfume.ScentOfASA.holiday.service.StoreHolidayService;
import com.Edo_perfume.ScentOfASA.reservation.mapper.PublicReservationMapper;
import com.Edo_perfume.ScentOfASA.slot.config.SlotBootstrapProperties;
import com.Edo_perfume.ScentOfASA.slot.domain.AdminSlot;
import com.Edo_perfume.ScentOfASA.slot.dto.AdminSlotMonthResponse;
import com.Edo_perfume.ScentOfASA.slot.dto.AdminSlotResponse;
import com.Edo_perfume.ScentOfASA.slot.dto.AdminSlotUpdateRequest;
import com.Edo_perfume.ScentOfASA.slot.mapper.AdminSlotMapper;

@ExtendWith(MockitoExtension.class)
class AdminSlotServiceTest {

    @Mock
    private AdminSlotMapper adminSlotMapper;

    @Mock(lenient = true)
    private GuideStaffMapper guideStaffMapper;

    @Mock
    private StoreHolidayService storeHolidayService;

    @Mock
    private PublicReservationMapper publicReservationMapper;

    @Mock(lenient = true)
    private SlotBootstrapProperties slotBootstrapProperties;

    @InjectMocks
    private AdminSlotService adminSlotService;

    @Test
    void getMonthlySlotsCreatesMissingMonthlySlots() {
        when(slotBootstrapProperties.isEnabled()).thenReturn(false);
        when(adminSlotMapper.findByDateTimeAndLanguage(any(), any(), any())).thenReturn(null);
        when(adminSlotMapper.findByMonth(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
                .thenReturn(List.of());
        when(publicReservationMapper.findByMonth(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "en"))
                .thenReturn(List.of());
        when(publicReservationMapper.findByMonth(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "ja"))
                .thenReturn(List.of());
        when(storeHolidayService.findMonthlyHolidays(2026, 5, null)).thenReturn(List.of());

        AdminSlotMonthResponse response = adminSlotService.getMonthlySlots(2026, 5);

        ArgumentCaptor<AdminSlot> captor = ArgumentCaptor.forClass(AdminSlot.class);
        verify(adminSlotMapper, org.mockito.Mockito.times(186)).insert(captor.capture());
        assertThat(captor.getAllValues()).hasSize(186);
        assertThat(response.getDays()).hasSize(31);
    }

    @Test
    void getMonthlySlotsMarksJapaneseSlotClosedWhenJapaneseHolidayExists() {
        when(slotBootstrapProperties.isEnabled()).thenReturn(false);
        AdminSlot englishSlot = createSlot(1L, LocalDate.of(2026, 5, 22), "11:00", "en", "English Guide", "OPEN");
        AdminSlot japaneseSlot = createSlot(2L, LocalDate.of(2026, 5, 22), "11:00", "ja", "Japanese Guide", "OPEN");

        when(adminSlotMapper.findByDateTimeAndLanguage(any(), any(), any())).thenReturn(new AdminSlot());
        when(adminSlotMapper.findByMonth(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
                .thenReturn(List.of(englishSlot, japaneseSlot));
        when(publicReservationMapper.findByMonth(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "en"))
                .thenReturn(List.of());
        when(publicReservationMapper.findByMonth(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "ja"))
                .thenReturn(List.of());
        when(storeHolidayService.findMonthlyHolidays(2026, 5, null))
                .thenReturn(List.of(
                        new HolidayCalendarDayResponse(10L, LocalDate.of(2026, 5, 22), "CLOSED", "研修", "ja")
                ));

        AdminSlotMonthResponse response = adminSlotService.getMonthlySlots(2026, 5);

        List<AdminSlotResponse> slots = response.getDays().stream()
                .filter(day -> day.getDate().equals(LocalDate.of(2026, 5, 22)))
                .findFirst()
                .orElseThrow()
                .getSlots();

        assertThat(slots).anySatisfy(slot -> {
            if ("ja".equals(slot.getGuideLanguage())) {
                assertThat(slot.getEffectiveStatus()).isEqualTo("CLOSED");
            }
        });
        assertThat(slots).anySatisfy(slot -> {
            if ("en".equals(slot.getGuideLanguage())) {
                assertThat(slot.getEffectiveStatus()).isEqualTo("OPEN");
            }
        });
    }

    @Test
    void updateSlotRejectsUnknownStatus() {
        when(slotBootstrapProperties.isEnabled()).thenReturn(false);
        AdminSlotUpdateRequest request = new AdminSlotUpdateRequest();
        request.setSlotStatus("BAD");

        AdminSlot slot = createSlot(1L, LocalDate.of(2026, 5, 22), "11:00", "en", "English Guide", "OPEN");
        when(adminSlotMapper.findById(1L)).thenReturn(slot);

        assertThatThrownBy(() -> adminSlotService.updateSlot(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Slot status must be OPEN, LIMITED, FULL, or STOPPED.");
    }

    @Test
    void updateSlotNormalizesGuideNameAndReturnsUpdatedSlot() {
        when(slotBootstrapProperties.isEnabled()).thenReturn(false);
        AdminSlotUpdateRequest request = new AdminSlotUpdateRequest();
        request.setGuideStaffId(4L);
        request.setSlotStatus("limited");

        AdminSlot slot = createSlot(1L, LocalDate.of(2026, 5, 22), "11:00", "ja", "Japanese Guide", "OPEN");
        when(adminSlotMapper.findById(1L)).thenReturn(slot);
        when(guideStaffMapper.findById(4L)).thenReturn(createGuideStaff(4L, "guide_ja_1", "Sato", "ja"));
        when(publicReservationMapper.sumGuestCountByDateAndTime(LocalDate.of(2026, 5, 22), "11:00", "ja")).thenReturn(0);
        when(storeHolidayService.findMonthlyHolidays(2026, 5, null)).thenReturn(List.of());

        AdminSlotResponse response = adminSlotService.updateSlot(1L, request);

        assertThat(slot.getGuideStaffId()).isEqualTo(4L);
        assertThat(slot.getGuideName()).isEqualTo("Sato");
        assertThat(slot.getSlotStatus()).isEqualTo("LIMITED");
        assertThat(response.getEffectiveStatus()).isEqualTo("LIMITED");
        verify(adminSlotMapper).update(eq(slot));
    }

    @Test
    void getMonthlySlotsMarksUnassignedOperatingSlotStopped() {
        when(slotBootstrapProperties.isEnabled()).thenReturn(false);
        AdminSlot slot = createSlot(1L, LocalDate.of(2026, 5, 22), "11:00", "ja", null, "OPEN");
        slot.setGuideStaffId(null);

        when(adminSlotMapper.findByDateTimeAndLanguage(any(), any(), any())).thenReturn(new AdminSlot());
        when(adminSlotMapper.findByMonth(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
                .thenReturn(List.of(slot));
        when(publicReservationMapper.findByMonth(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "en"))
                .thenReturn(List.of());
        when(publicReservationMapper.findByMonth(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "ja"))
                .thenReturn(List.of());
        when(storeHolidayService.findMonthlyHolidays(2026, 5, null)).thenReturn(List.of());

        AdminSlotMonthResponse response = adminSlotService.getMonthlySlots(2026, 5);

        AdminSlotResponse slotResponse = response.getDays().stream()
                .filter(day -> day.getDate().equals(LocalDate.of(2026, 5, 22)))
                .findFirst()
                .orElseThrow()
                .getSlots()
                .get(0);

        assertThat(slotResponse.getEffectiveStatus()).isEqualTo("STOPPED");
        assertThat(slotResponse.getGuideName()).isNull();
    }

    @Test
    void updateSlotRejectsGuideLanguageMismatch() {
        when(slotBootstrapProperties.isEnabled()).thenReturn(false);
        AdminSlotUpdateRequest request = new AdminSlotUpdateRequest();
        request.setGuideStaffId(4L);
        request.setSlotStatus("OPEN");

        AdminSlot slot = createSlot(1L, LocalDate.of(2026, 5, 22), "11:00", "ja", null, "STOPPED");
        when(adminSlotMapper.findById(1L)).thenReturn(slot);
        when(guideStaffMapper.findById(4L)).thenReturn(createGuideStaff(4L, "guide_en_1", "Alice", "en"));

        assertThatThrownBy(() -> adminSlotService.updateSlot(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The selected guide staff language does not match the slot language.");
    }

    @Test
    void updateSlotRejectsOpenStatusWithoutGuideAssignment() {
        when(slotBootstrapProperties.isEnabled()).thenReturn(false);
        AdminSlotUpdateRequest request = new AdminSlotUpdateRequest();
        request.setSlotStatus("OPEN");

        AdminSlot slot = createSlot(1L, LocalDate.of(2026, 5, 22), "11:00", "en", null, "STOPPED");
        when(adminSlotMapper.findById(1L)).thenReturn(slot);

        assertThatThrownBy(() -> adminSlotService.updateSlot(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A guide staff assignment is required unless the slot is STOPPED.");
    }

    @Test
    void getMonthlySlotsMarksTodayAsBookingClosedWhenNotHoliday() {
        when(slotBootstrapProperties.isEnabled()).thenReturn(false);
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        AdminSlot slot = createSlot(1L, today, "11:00", "ja", "Japanese Guide", "OPEN");

        when(adminSlotMapper.findByDateTimeAndLanguage(any(), any(), any())).thenReturn(new AdminSlot());
        when(adminSlotMapper.findByMonth(monthStart, monthEnd))
                .thenReturn(List.of(slot));
        when(publicReservationMapper.findByMonth(monthStart, monthEnd, "en"))
                .thenReturn(List.of());
        when(publicReservationMapper.findByMonth(monthStart, monthEnd, "ja"))
                .thenReturn(List.of());
        when(storeHolidayService.findMonthlyHolidays(today.getYear(), today.getMonthValue(), null)).thenReturn(List.of());

        AdminSlotMonthResponse response = adminSlotService.getMonthlySlots(today.getYear(), today.getMonthValue());

        assertThat(response.getDays()).anySatisfy(day -> {
            if (day.getDate().equals(today)) {
                assertThat(day.isBookingClosed()).isTrue();
                assertThat(day.isClosed()).isFalse();
            }
        });
    }

    @Test
    void getMonthlySlotsAssignsRecurringDefaultGuidesWhenBootstrapEnabled() {
        when(slotBootstrapProperties.isEnabled()).thenReturn(true);
        when(adminSlotMapper.findByDateTimeAndLanguage(any(), any(), any())).thenReturn(null);
        when(guideStaffMapper.findByLoginId(any())).thenReturn(null);
        when(adminSlotMapper.findByMonth(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
                .thenReturn(List.of());
        when(publicReservationMapper.findByMonth(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "en"))
                .thenReturn(List.of());
        when(publicReservationMapper.findByMonth(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "ja"))
                .thenReturn(List.of());
        when(storeHolidayService.findMonthlyHolidays(2026, 5, null)).thenReturn(List.of());
        when(guideStaffMapper.findByLoginId("guide_en_1")).thenReturn(createGuideStaff(1L, "guide_en_1", "English Guide 1", "en"));
        when(guideStaffMapper.findByLoginId("guide_en_2")).thenReturn(createGuideStaff(2L, "guide_en_2", "English Guide 2", "en"));
        when(guideStaffMapper.findByLoginId("guide_en_3")).thenReturn(createGuideStaff(3L, "guide_en_3", "English Guide 3", "en"));
        when(guideStaffMapper.findByLoginId("guide_ja_1")).thenReturn(createGuideStaff(4L, "guide_ja_1", "Japanese Guide 1", "ja"));
        when(guideStaffMapper.findByLoginId("guide_ja_2")).thenReturn(createGuideStaff(5L, "guide_ja_2", "Japanese Guide 2", "ja"));
        when(guideStaffMapper.findByLoginId("guide_ja_3")).thenReturn(createGuideStaff(6L, "guide_ja_3", "Japanese Guide 3", "ja"));

        adminSlotService.getMonthlySlots(2026, 5);

        ArgumentCaptor<AdminSlot> captor = ArgumentCaptor.forClass(AdminSlot.class);
        verify(adminSlotMapper, org.mockito.Mockito.times(186)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .anySatisfy(slot -> {
                    if (slot.getSlotDate().equals(LocalDate.of(2026, 5, 1))
                            && "en".equals(slot.getGuideLanguage())
                            && "11:00".equals(slot.getTimeSlot())) {
                        assertThat(slot.getGuideName()).isEqualTo("English Guide 1");
                        assertThat(slot.getSlotStatus()).isEqualTo("OPEN");
                    }
                })
                .anySatisfy(slot -> {
                    if (slot.getSlotDate().equals(LocalDate.of(2026, 5, 1))
                            && "ja".equals(slot.getGuideLanguage())
                            && "15:30".equals(slot.getTimeSlot())) {
                        assertThat(slot.getGuideName()).isEqualTo("Japanese Guide 3");
                        assertThat(slot.getSlotStatus()).isEqualTo("OPEN");
                    }
                })
                .anySatisfy(slot -> {
                    if (slot.getSlotDate().equals(LocalDate.of(2026, 5, 6))
                            && "en".equals(slot.getGuideLanguage())
                            && "11:00".equals(slot.getTimeSlot())) {
                        assertThat(slot.getGuideName()).isNull();
                        assertThat(slot.getSlotStatus()).isEqualTo("STOPPED");
                    }
                });
    }

    @Test
    void getMonthlySlotsBackfillsExistingStoppedRecurringSlotsWhenBootstrapEnabled() {
        when(slotBootstrapProperties.isEnabled()).thenReturn(true);
        when(adminSlotMapper.findByDateTimeAndLanguage(any(), any(), any())).thenReturn(null);

        AdminSlot existingThursdayEnglish = createSlot(10L, LocalDate.of(2026, 5, 7), "11:00", "en", null, "STOPPED");
        AdminSlot existingThursdayJapanese = createSlot(11L, LocalDate.of(2026, 5, 7), "13:00", "ja", null, "STOPPED");

        doReturn(existingThursdayEnglish).when(adminSlotMapper)
                .findByDateTimeAndLanguage(LocalDate.of(2026, 5, 7), "11:00", "en");
        doReturn(existingThursdayJapanese).when(adminSlotMapper)
                .findByDateTimeAndLanguage(LocalDate.of(2026, 5, 7), "13:00", "ja");
        doReturn(createGuideStaff(1L, "guide_en_1", "English Guide 1", "en")).when(guideStaffMapper)
                .findByLoginId("guide_en_1");
        doReturn(createGuideStaff(5L, "guide_ja_2", "Japanese Guide 2", "ja")).when(guideStaffMapper)
                .findByLoginId("guide_ja_2");
        when(adminSlotMapper.findByMonth(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
                .thenReturn(List.of(existingThursdayEnglish, existingThursdayJapanese));
        when(publicReservationMapper.findByMonth(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "en"))
                .thenReturn(List.of());
        when(publicReservationMapper.findByMonth(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "ja"))
                .thenReturn(List.of());
        when(storeHolidayService.findMonthlyHolidays(2026, 5, null)).thenReturn(List.of());

        adminSlotService.getMonthlySlots(2026, 5);

        assertThat(existingThursdayEnglish.getGuideName()).isEqualTo("English Guide 1");
        assertThat(existingThursdayEnglish.getSlotStatus()).isEqualTo("OPEN");
        assertThat(existingThursdayJapanese.getGuideName()).isEqualTo("Japanese Guide 2");
        assertThat(existingThursdayJapanese.getSlotStatus()).isEqualTo("OPEN");
        verify(adminSlotMapper, org.mockito.Mockito.atLeast(2)).update(any(AdminSlot.class));
    }

    private AdminSlot createSlot(Long id, LocalDate date, String timeSlot, String language,
                                 String guideName, String status) {
        AdminSlot slot = new AdminSlot();
        slot.setId(id);
        slot.setSlotDate(date);
        slot.setTimeSlot(timeSlot);
        slot.setGuideLanguage(language);
        slot.setGuideStaffId(guideName == null ? null : 1L);
        slot.setGuideName(guideName);
        slot.setSlotStatus(status);
        slot.setCreatedAt(LocalDateTime.of(2026, 4, 24, 10, 0));
        slot.setUpdatedAt(LocalDateTime.of(2026, 4, 24, 10, 0));
        return slot;
    }

    private GuideStaff createGuideStaff(Long id, String loginId, String displayName, String guideLanguage) {
        GuideStaff guideStaff = new GuideStaff();
        guideStaff.setId(id);
        guideStaff.setLoginId(loginId);
        guideStaff.setDisplayName(displayName);
        guideStaff.setGuideLanguage(guideLanguage);
        guideStaff.setActive(true);
        return guideStaff;
    }
}
