package com.trekking.app.features.tour.dto.response;

public record TourManagementEquipment(long tourId, String title, String includedItems, String sharedItems,
                                      String personalItemsToBring, Long scheduleEquipmentFeeVnd) {}
