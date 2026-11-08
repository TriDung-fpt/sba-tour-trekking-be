package com.trekking.app.features.tour.dto.response;

import java.util.List;

public record ScheduleEquipmentSummaryResponse(
        long scheduleId,
        int countedBookings,
        int bookingsWithEquipment,
        int activeHoldBookingsWithEquipment,
        int totalOrderedItems,
        int totalIssuedItems,
        int totalReturnedItems,
        int totalActiveHoldItems,
        List<ScheduleEquipmentSummaryItem> items
) {
    public record ScheduleEquipmentSummaryItem(
            long equipmentId,
            String equipmentName,
            int orderedQuantity,
            int issuedQuantity,
            int returnedQuantity,
            int bookingCount,
            int activeHoldQuantity,
            int activeHoldBookingCount
    ) {}
}
