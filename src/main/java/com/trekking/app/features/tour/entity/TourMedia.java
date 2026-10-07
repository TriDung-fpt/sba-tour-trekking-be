package com.trekking.app.features.tour.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.tour.enums.MediaType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tour_media", schema = "trekking")
public class TourMedia extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "tour_id", nullable = false)
    private Long tourId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, columnDefinition = "text")
    private MediaType mediaType;

    @Column(name = "url", nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "caption", columnDefinition = "text")
    private String caption;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "is_cover", nullable = false)
    private Boolean isCover = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tour_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Tour tour;
}
