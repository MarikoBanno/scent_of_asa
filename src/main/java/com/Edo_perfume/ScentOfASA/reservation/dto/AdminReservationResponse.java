package com.Edo_perfume.ScentOfASA.reservation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class AdminReservationResponse {

    private Long reservationId;
    private String reservationCode;
    private LocalDate reservationDate;
    private String timeSlot;
    private String customerName;
    private Integer guestCount;
    private String guideLanguage;
    private String customerEmail;
    private String customerPhone;
    private String paymentStatus;
    private String reservationStatus;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AdminReservationResponse() {
    }

    public AdminReservationResponse(Long reservationId, String reservationCode, LocalDate reservationDate, String timeSlot,
                                    String customerName, Integer guestCount, String guideLanguage, String customerEmail,
                                    String customerPhone, String paymentStatus, String reservationStatus, String notes,
                                    LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.reservationId = reservationId;
        this.reservationCode = reservationCode;
        this.reservationDate = reservationDate;
        this.timeSlot = timeSlot;
        this.customerName = customerName;
        this.guestCount = guestCount;
        this.guideLanguage = guideLanguage;
        this.customerEmail = customerEmail;
        this.customerPhone = customerPhone;
        this.paymentStatus = paymentStatus;
        this.reservationStatus = reservationStatus;
        this.notes = notes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public String getReservationCode() {
        return reservationCode;
    }

    public void setReservationCode(String reservationCode) {
        this.reservationCode = reservationCode;
    }

    public LocalDate getReservationDate() {
        return reservationDate;
    }

    public void setReservationDate(LocalDate reservationDate) {
        this.reservationDate = reservationDate;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public void setTimeSlot(String timeSlot) {
        this.timeSlot = timeSlot;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public Integer getGuestCount() {
        return guestCount;
    }

    public void setGuestCount(Integer guestCount) {
        this.guestCount = guestCount;
    }

    public String getGuideLanguage() {
        return guideLanguage;
    }

    public void setGuideLanguage(String guideLanguage) {
        this.guideLanguage = guideLanguage;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getReservationStatus() {
        return reservationStatus;
    }

    public void setReservationStatus(String reservationStatus) {
        this.reservationStatus = reservationStatus;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
