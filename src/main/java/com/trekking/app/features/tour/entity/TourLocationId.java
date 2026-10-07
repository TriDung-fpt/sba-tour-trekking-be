package com.trekking.app.features.tour.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TourLocationId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long tourId;

    private Long locationId;
}
