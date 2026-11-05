package com.trekking.app.features.equipment.service;

import com.trekking.app.features.equipment.dto.request.EquipmentRequests;
import com.trekking.app.features.equipment.dto.response.EquipmentResponses;
import java.util.List;

public interface EquipmentService {
    List<EquipmentResponses.EquipmentDetails> listEquipment();
    EquipmentResponses.EquipmentDetails updateInventory(long equipmentId, EquipmentRequests.SaveInventory request);
    List<EquipmentResponses.Category> listCategories();
    EquipmentResponses.Category createCategory(EquipmentRequests.SaveCategory request);
    EquipmentResponses.Category updateCategory(long categoryId, EquipmentRequests.SaveCategory request);
    void deleteCategory(long categoryId);
}
