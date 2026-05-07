package com.Edo_perfume.ScentOfASA.reservation.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.Edo_perfume.ScentOfASA.reservation.domain.PublicReservation;

@Mapper
public interface PublicReservationMapper {

    PublicReservation findById(@Param("id") Long id);

    PublicReservation findByPaymentIntentId(@Param("paymentIntentId") String paymentIntentId);

    List<PublicReservation> search(@Param("reservationDate") LocalDate reservationDate,
                                   @Param("customerName") String customerName,
                                   @Param("guideLanguage") String guideLanguage);

    List<PublicReservation> findByMonth(@Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate,
                                        @Param("guideLanguage") String guideLanguage);

    boolean existsByDateAndTime(@Param("reservationDate") LocalDate reservationDate,
                                @Param("timeSlot") String timeSlot,
                                @Param("guideLanguage") String guideLanguage);

    Integer sumGuestCountByDateAndTime(@Param("reservationDate") LocalDate reservationDate,
                                       @Param("timeSlot") String timeSlot,
                                       @Param("guideLanguage") String guideLanguage);

    int updateStatus(@Param("id") Long id,
                     @Param("reservationStatus") String reservationStatus,
                     @Param("updatedAt") LocalDateTime updatedAt);

    int insert(PublicReservation reservation);
}
