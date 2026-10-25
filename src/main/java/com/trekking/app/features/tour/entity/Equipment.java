package com.trekking.app.features.tour.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "equipment", schema = "trekking")
public class Equipment extends TimestampedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(columnDefinition = "text")
    private String description;
    @Column(name = "individual_rental_price_vnd")
    private Long individualRentalPriceVnd;

    @Column(name = "image_url", columnDefinition = "text")
    private String imageUrl;

    @Column(name = "storage_key", columnDefinition = "text")
    private String storageKey;
}
