package com.trekking.app.features.tour.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tour_itinerary_days", schema = "trekking")
@Getter
@Setter
public class TourItineraryDay {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tour_id", nullable = false)
    private Long tourId;
    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;
    @Column(nullable = false, length = 250)
    private String title;
    private String meals;
    private String overnight;
}
