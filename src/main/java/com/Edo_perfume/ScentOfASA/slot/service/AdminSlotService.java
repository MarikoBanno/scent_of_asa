package com.Edo_perfume.ScentOfASA.slot.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Edo_perfume.ScentOfASA.guide.domain.GuideStaff;
import com.Edo_perfume.ScentOfASA.guide.mapper.GuideStaffMapper;
import com.Edo_perfume.ScentOfASA.holiday.dto.HolidayCalendarDayResponse;
import com.Edo_perfume.ScentOfASA.holiday.service.StoreHolidayService;
import com.Edo_perfume.ScentOfASA.reservation.domain.PublicReservation;
import com.Edo_perfume.ScentOfASA.reservation.mapper.PublicReservationMapper;
import com.Edo_perfume.ScentOfASA.slot.domain.AdminSlot;
import com.Edo_perfume.ScentOfASA.slot.dto.AdminSlotDayResponse;
import com.Edo_perfume.ScentOfASA.slot.dto.AdminSlotMonthResponse;
import com.Edo_perfume.ScentOfASA.slot.dto.AdminSlotResponse;
import com.Edo_perfume.ScentOfASA.slot.dto.AdminSlotUpdateRequest;
import com.Edo_perfume.ScentOfASA.slot.mapper.AdminSlotMapper;
import com.Edo_perfume.ScentOfASA.slot.config.SlotBootstrapProperties;

@Service
@Transactional
public class AdminSlotService {

    private static final int SLOT_CAPACITY = 4;
    private static final List<String> SUPPORTED_TIME_SLOTS = List.of("11:00", "13:00", "15:30");
    private static final List<String> SUPPORTED_LANGUAGES = List.of("en", "ja");
    private static final List<String> SUPPORTED_SLOT_STATUSES = List.of("OPEN", "LIMITED", "FULL", "STOPPED");

    private final AdminSlotMapper adminSlotMapper;
    private final GuideStaffMapper guideStaffMapper;
    private final StoreHolidayService storeHolidayService;
    private final PublicReservationMapper publicReservationMapper;
    private final SlotBootstrapProperties slotBootstrapProperties;

    public AdminSlotService(AdminSlotMapper adminSlotMapper,
                            GuideStaffMapper guideStaffMapper,
                            StoreHolidayService storeHolidayService,
                            PublicReservationMapper publicReservationMapper,
                            SlotBootstrapProperties slotBootstrapProperties) {
        this.adminSlotMapper = adminSlotMapper;
        this.guideStaffMapper = guideStaffMapper;
        this.storeHolidayService = storeHolidayService;
        this.publicReservationMapper = publicReservationMapper;
        this.slotBootstrapProperties = slotBootstrapProperties;
    }

    public AdminSlotMonthResponse getMonthlySlots(int year, int month) {
        validateYearMonth(year, month);
        YearMonth yearMonth = prepareMonthlySlots(year, month);

        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        List<AdminSlot> slots = adminSlotMapper.findByMonth(startDate, endDate);
        Map<String, Integer> reservedGuestCountBySlotKey = buildReservedGuestCountBySlotKey(startDate, endDate);
        Map<LocalDate, List<AdminSlot>> slotsByDate = new HashMap<>();
        for (AdminSlot slot : slots) {
            slotsByDate.computeIfAbsent(slot.getSlotDate(), ignored -> new ArrayList<>()).add(slot);
        }

        Map<LocalDate, Map<String, HolidayCalendarDayResponse>> holidayByDateAndLanguage = buildHolidayMap(year, month);
        List<AdminSlotDayResponse> days = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            List<AdminSlot> dailySlots = new ArrayList<>(slotsByDate.getOrDefault(date, List.of()));
            dailySlots.sort(Comparator
                    .comparing(AdminSlot::getTimeSlot)
                    .thenComparing(AdminSlot::getGuideLanguage));

            Map<String, HolidayCalendarDayResponse> dayHolidayMap = holidayByDateAndLanguage.getOrDefault(date, Map.of());
            List<AdminSlotResponse> responses = dailySlots.stream()
                    .map(slot -> toResponse(
                            slot,
                            resolveEffectiveStatus(slot, dayHolidayMap),
                            reservedGuestCountBySlotKey.getOrDefault(toSlotKey(slot), 0)))
                    .toList();
            boolean dayClosed = !responses.isEmpty()
                    && responses.stream().allMatch(slot -> "CLOSED".equals(slot.getEffectiveStatus()));
            HolidayCalendarDayResponse sharedHoliday = dayHolidayMap.get("all");
            boolean bookingClosed = !dayClosed && isBookingClosedDate(date);

            String holidayType = sharedHoliday != null ? sharedHoliday.getHolidayType() : null;
            String holidayReason = sharedHoliday != null ? sharedHoliday.getReason() : null;
            days.add(new AdminSlotDayResponse(date, dayClosed, bookingClosed, holidayType, holidayReason, responses));
        }

        return new AdminSlotMonthResponse(year, month, days);
    }

    public AdminSlotResponse updateSlot(Long id, AdminSlotUpdateRequest request) {
        if (request.getSlotStatus() == null || request.getSlotStatus().isBlank()) {
            throw new IllegalArgumentException("Slot status is required.");
        }

        AdminSlot slot = adminSlotMapper.findById(id);
        if (slot == null) {
            throw new NoSuchElementException("The slot was not found.");
        }

        String normalizedStatus = request.getSlotStatus().trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_SLOT_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("Slot status must be OPEN, LIMITED, FULL, or STOPPED.");
        }

        applyGuideAssignment(slot, request, normalizedStatus);
        slot.setUpdatedAt(LocalDateTime.now());
        adminSlotMapper.update(slot);

        Map<LocalDate, Map<String, HolidayCalendarDayResponse>> holidayMap =
                buildHolidayMap(slot.getSlotDate().getYear(), slot.getSlotDate().getMonthValue());
        int reservedGuestCount = safeGuestCount(publicReservationMapper
                .sumGuestCountByDateAndTime(slot.getSlotDate(), slot.getTimeSlot(), slot.getGuideLanguage()));
        return toResponse(slot, resolveEffectiveStatus(slot, holidayMap.getOrDefault(slot.getSlotDate(), Map.of())), reservedGuestCount);
    }

    public void ensureMonthlySlots(int year, int month) {
        prepareMonthlySlots(year, month);
    }

    private YearMonth prepareMonthlySlots(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        ensureMonthlySlots(yearMonth);
        return yearMonth;
    }

    private void ensureMonthlySlots(YearMonth yearMonth) {
        for (LocalDate date = yearMonth.atDay(1); !date.isAfter(yearMonth.atEndOfMonth()); date = date.plusDays(1)) {
            for (String guideLanguage : SUPPORTED_LANGUAGES) {
                for (String timeSlot : SUPPORTED_TIME_SLOTS) {
                    AdminSlot existingSlot = adminSlotMapper.findByDateTimeAndLanguage(date, timeSlot, guideLanguage);
                    if (existingSlot != null) {
                        applyDefaultAssignmentToExistingSlot(existingSlot);
                        continue;
                    }
                    AdminSlot slot = new AdminSlot();
                    slot.setSlotDate(date);
                    slot.setTimeSlot(timeSlot);
                    slot.setGuideLanguage(guideLanguage);
                    applyDefaultAssignment(slot);
                    LocalDateTime now = LocalDateTime.now();
                    slot.setCreatedAt(now);
                    slot.setUpdatedAt(now);
                    adminSlotMapper.insert(slot);
                }
            }
        }
    }

    private void applyDefaultAssignmentToExistingSlot(AdminSlot existingSlot) {
        if (!slotBootstrapProperties.isEnabled()) {
            return;
        }
        if (existingSlot.getGuideStaffId() != null || !"STOPPED".equals(existingSlot.getSlotStatus())) {
            return;
        }

        Long originalGuideStaffId = existingSlot.getGuideStaffId();
        String originalGuideName = existingSlot.getGuideName();
        String originalStatus = existingSlot.getSlotStatus();

        applyDefaultAssignment(existingSlot);
        boolean changed = !equalsNullable(originalGuideStaffId, existingSlot.getGuideStaffId())
                || !equalsNullable(originalGuideName, existingSlot.getGuideName())
                || !equalsNullable(originalStatus, existingSlot.getSlotStatus());
        if (!changed) {
            return;
        }

        existingSlot.setUpdatedAt(LocalDateTime.now());
        adminSlotMapper.update(existingSlot);
    }

    private Map<LocalDate, Map<String, HolidayCalendarDayResponse>> buildHolidayMap(int year, int month) {
        Map<LocalDate, Map<String, HolidayCalendarDayResponse>> result = new HashMap<>();
        for (HolidayCalendarDayResponse holiday : storeHolidayService.findMonthlyHolidays(year, month, null)) {
            String languageKey = holiday.getAppliesToLanguage() == null ? "all" : holiday.getAppliesToLanguage();
            result.computeIfAbsent(holiday.getHolidayDate(), ignored -> new HashMap<>())
                    .put(languageKey, holiday);
        }
        return result;
    }

    private String resolveEffectiveStatus(AdminSlot slot, Map<String, HolidayCalendarDayResponse> holidayMap) {
        HolidayCalendarDayResponse languageHoliday = holidayMap.get(slot.getGuideLanguage());
        HolidayCalendarDayResponse sharedHoliday = holidayMap.get("all");

        if (languageHoliday != null) {
            if ("SPECIAL_OPEN".equals(languageHoliday.getHolidayType())) {
                return slot.getGuideStaffId() == null ? "STOPPED" : slot.getSlotStatus();
            }
            if ("CLOSED".equals(languageHoliday.getHolidayType())) {
                return "CLOSED";
            }
        }
        if (sharedHoliday != null) {
            if ("SPECIAL_OPEN".equals(sharedHoliday.getHolidayType())) {
                return slot.getGuideStaffId() == null ? "STOPPED" : slot.getSlotStatus();
            }
            if ("CLOSED".equals(sharedHoliday.getHolidayType())) {
                return "CLOSED";
            }
        }
        if (slot.getGuideStaffId() == null) {
            return "STOPPED";
        }
        return slot.getSlotStatus();
    }

    private AdminSlotResponse toResponse(AdminSlot slot, String effectiveStatus, int reservedGuestCount) {
        int remainingCapacity = "OPEN".equals(effectiveStatus) || "LIMITED".equals(effectiveStatus)
                ? Math.max(0, SLOT_CAPACITY - reservedGuestCount)
                : 0;
        String displayStatus = effectiveStatus;
        if ("OPEN".equals(effectiveStatus) && reservedGuestCount > 0) {
            displayStatus = "LIMITED";
        } else if ("LIMITED".equals(effectiveStatus) && remainingCapacity <= 0) {
            displayStatus = "FULL";
            remainingCapacity = 0;
        }
        return new AdminSlotResponse(
                slot.getId(),
                slot.getTimeSlot(),
                slot.getGuideLanguage(),
                slot.getGuideStaffId(),
                slot.getGuideStaffId() == null ? null : slot.getGuideName(),
                slot.getSlotStatus(),
                displayStatus,
                "OPEN".equals(displayStatus) || "LIMITED".equals(displayStatus),
                remainingCapacity,
                reservedGuestCount
        );
    }

    private Map<String, Integer> buildReservedGuestCountBySlotKey(LocalDate startDate, LocalDate endDate) {
        Map<String, Integer> result = new HashMap<>();
        for (String language : SUPPORTED_LANGUAGES) {
            for (PublicReservation reservation : publicReservationMapper.findByMonth(startDate, endDate, language)) {
                result.merge(
                        toSlotKey(reservation.getReservationDate(), reservation.getTimeSlot(), reservation.getGuideLanguage()),
                        reservation.getGuestCount(),
                        Integer::sum);
            }
        }
        return result;
    }

    private String toSlotKey(AdminSlot slot) {
        return toSlotKey(slot.getSlotDate(), slot.getTimeSlot(), slot.getGuideLanguage());
    }

    private String toSlotKey(LocalDate slotDate, String timeSlot, String guideLanguage) {
        return slotDate + "|" + timeSlot + "|" + guideLanguage;
    }

    private int safeGuestCount(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean equalsNullable(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private void applyDefaultAssignment(AdminSlot slot) {
        if (!slotBootstrapProperties.isEnabled()) {
            slot.setGuideStaffId(null);
            slot.setGuideName(null);
            slot.setSlotStatus("STOPPED");
            return;
        }

        if (!isRecurringOpenDay(slot.getSlotDate())) {
            slot.setGuideStaffId(null);
            slot.setGuideName(null);
            slot.setSlotStatus("STOPPED");
            return;
        }

        String loginId = resolveDefaultGuideLoginId(slot.getGuideLanguage(), slot.getTimeSlot());
        if (loginId == null) {
            slot.setGuideStaffId(null);
            slot.setGuideName(null);
            slot.setSlotStatus("STOPPED");
            return;
        }

        GuideStaff guideStaff = guideStaffMapper.findByLoginId(loginId);
        if (guideStaff == null || !guideStaff.isActive()) {
            slot.setGuideStaffId(null);
            slot.setGuideName(null);
            slot.setSlotStatus("STOPPED");
            return;
        }

        slot.setGuideStaffId(guideStaff.getId());
        slot.setGuideName(guideStaff.getDisplayName());
        slot.setSlotStatus("OPEN");
    }

    private boolean isRecurringOpenDay(LocalDate slotDate) {
        DayOfWeek dayOfWeek = slotDate.getDayOfWeek();
        return dayOfWeek == DayOfWeek.THURSDAY || dayOfWeek == DayOfWeek.FRIDAY;
    }

    private String resolveDefaultGuideLoginId(String guideLanguage, String timeSlot) {
        String suffix = switch (timeSlot) {
            case "11:00" -> "1";
            case "13:00" -> "2";
            case "15:30" -> "3";
            default -> null;
        };
        if (suffix == null) {
            return null;
        }
        if ("en".equals(guideLanguage)) {
            return "guide_en_" + suffix;
        }
        if ("ja".equals(guideLanguage)) {
            return "guide_ja_" + suffix;
        }
        return null;
    }

    private void validateYearMonth(int year, int month) {
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Year must be between 2000 and 2100.");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12.");
        }
    }

    private void applyGuideAssignment(AdminSlot slot, AdminSlotUpdateRequest request, String normalizedStatus) {
        if (request.getGuideStaffId() == null) {
            if (!"STOPPED".equals(normalizedStatus)) {
                throw new IllegalArgumentException("A guide staff assignment is required unless the slot is STOPPED.");
            }
            slot.setGuideStaffId(null);
            slot.setGuideName(null);
            slot.setSlotStatus(normalizedStatus);
            return;
        }

        GuideStaff guideStaff = guideStaffMapper.findById(request.getGuideStaffId());
        if (guideStaff == null || !guideStaff.isActive()) {
            throw new NoSuchElementException("The guide staff was not found.");
        }
        if (!slot.getGuideLanguage().equals(guideStaff.getGuideLanguage())) {
            throw new IllegalArgumentException("The selected guide staff language does not match the slot language.");
        }

        slot.setGuideStaffId(guideStaff.getId());
        slot.setGuideName(guideStaff.getDisplayName());
        slot.setSlotStatus(normalizedStatus);
    }

    private boolean isBookingClosedDate(LocalDate slotDate) {
        return !slotDate.isAfter(LocalDate.now().plusDays(1));
    }
}
