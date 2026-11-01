package com.trekking.app.features.tour.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
@Table(name = "tour_schedule_defaults", schema = "trekking")
public class TourScheduleDefaults {
    @Id @Column(name = "tour_id") private Long tourId;
    @Column(nullable = false) private Integer capacity;
    @Column(name = "base_price_vnd", nullable = false) private Long basePriceVnd;
    @Column(name = "equipment_fee_vnd", nullable = false) private Long equipmentFeeVnd;
    @Column(name = "deposit_percent", nullable = false) private BigDecimal depositPercent;
    @Column(name = "meeting_point") private String meetingPoint;
    @Column(name = "contact_phone") private String contactPhone;
}
