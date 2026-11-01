package com.trekking.app.features.tour.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name = "tour_terms", schema = "trekking") @Getter @Setter
public class TourTerm {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tour_id", nullable = false) private Long tourId;
    @Column(name = "policy_version_id", nullable = false) private Long policyVersionId;
    @Column(name = "display_order", nullable = false) private Integer displayOrder;
}
