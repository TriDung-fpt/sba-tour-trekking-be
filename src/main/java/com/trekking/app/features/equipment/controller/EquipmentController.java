package com.trekking.app.features.equipment.controller;

import com.trekking.app.features.equipment.dto.request.EquipmentRequests;
import com.trekking.app.features.equipment.dto.response.EquipmentResponses;
import com.trekking.app.features.equipment.service.EquipmentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/admin/equipment")
@SecurityRequirement(name = "bearerAuth")
public class EquipmentController {
    private final EquipmentService equipment;

    public EquipmentController(EquipmentService equipment) { this.equipment = equipment; }

    @GetMapping("/inventory")
    public List<EquipmentResponses.EquipmentDetails> listInventory() { return equipment.listEquipment(); }

    @PutMapping("/{equipmentId}/inventory")
    public EquipmentResponses.EquipmentDetails updateInventory(@PathVariable long equipmentId,
            @Valid @RequestBody EquipmentRequests.SaveInventory request) {
        return equipment.updateInventory(equipmentId, request);
    }

    @GetMapping("/categories")
    public List<EquipmentResponses.Category> listCategories() { return equipment.listCategories(); }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public EquipmentResponses.Category createCategory(@Valid @RequestBody EquipmentRequests.SaveCategory request) {
        return equipment.createCategory(request);
    }

    @PutMapping("/categories/{categoryId}")
    public EquipmentResponses.Category updateCategory(@PathVariable long categoryId,
            @Valid @RequestBody EquipmentRequests.SaveCategory request) {
        return equipment.updateCategory(categoryId, request);
    }

    @DeleteMapping("/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable long categoryId) { equipment.deleteCategory(categoryId); }
}
