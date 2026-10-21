package com.trekking.app.features.tour.dto.request;

import com.trekking.app.features.tour.enums.DifficultyLevel;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TourSearchRequest(@Size(max = 100) String query,
                                @Positive Long destinationId,
                                @Pattern(regexp = "easy|moderate|hard|expert") String difficulty,
                                OffsetDateTime dateFrom,
                                OffsetDateTime dateTo,
                                @PositiveOrZero Long minPrice,
                                @PositiveOrZero Long maxPrice,
                                @DecimalMin("0.0") @DecimalMax("5.0") BigDecimal minRating,
                                @Positive Long equipmentPackageId,
                                @Pattern(regexp = "recommended|price_asc|price_desc|rating_desc|departure_asc") String sort,
                                @Min(0) @Max(10_000) Integer page,
                                @Min(1) @Max(100) Integer size) {
    public TourSearchRequest {
        query = query == null || query.isBlank() ? null : query.trim();
        sort = sort == null || sort.isBlank() ? "recommended" : sort;
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }

    @AssertTrue(message = "minPrice must be less than or equal to maxPrice")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice <= maxPrice;
    }

    @AssertTrue(message = "dateFrom must be less than or equal to dateTo")
    public boolean isDateRangeValid() {
        return dateFrom == null || dateTo == null || !dateFrom.isAfter(dateTo);
    }

    public DifficultyLevel difficultyLevel() {
        return difficulty == null ? null : DifficultyLevel.valueOf(difficulty.toUpperCase(java.util.Locale.ROOT));
    }
}
