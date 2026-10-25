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
@Table(name = "rental_package_templates", schema = "trekking")
public class RentalPackageTemplate extends TimestampedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "shared_items", columnDefinition = "text")
    private String sharedItems;

    @Column(name = "personal_items_to_bring", columnDefinition = "text")
    private String personalItemsToBring;

    @Column(name = "rental_price_vnd")
    private Long rentalPriceVnd;

    @Column(name = "image_url", columnDefinition = "text")
    private String imageUrl;

    @Column(name = "storage_key", columnDefinition = "text")
    private String storageKey;
}
