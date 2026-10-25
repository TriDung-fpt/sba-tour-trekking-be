package com.trekking.app.features.tour.controller;

import com.trekking.app.features.tour.dto.request.SaveEquipmentRequest;
import com.trekking.app.features.tour.dto.request.SaveRentalPackageRequest;
import com.trekking.app.features.tour.dto.response.EquipmentCatalogResponse;
import com.trekking.app.features.tour.dto.response.RentalPackageTemplateResponse;
import com.trekking.app.features.tour.service.EquipmentCatalogService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tours")
public class EquipmentCatalogController {
    private final EquipmentCatalogService catalog;

    public EquipmentCatalogController(EquipmentCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/equipment-catalog")
    public List<EquipmentCatalogResponse> equipment() {
        return catalog.equipment();
    }

    @PostMapping("/equipment-catalog")
    @ResponseStatus(HttpStatus.CREATED)
    public EquipmentCatalogResponse createEquipment(@Valid @RequestBody SaveEquipmentRequest request) {
        return catalog.createEquipment(request);
    }

    @PutMapping("/equipment-catalog/{equipmentId}")
    public EquipmentCatalogResponse updateEquipment(@PathVariable long equipmentId,
            @Valid @RequestBody SaveEquipmentRequest request) {
        return catalog.updateEquipment(equipmentId, request);
    }

    @PostMapping(value = "/equipment-catalog/{equipmentId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EquipmentCatalogResponse uploadEquipmentImage(@PathVariable long equipmentId,
            @RequestPart("file") MultipartFile file) {
        return catalog.uploadEquipmentImage(equipmentId, file);
    }

    @DeleteMapping("/equipment-catalog/{equipmentId}/image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEquipmentImage(@PathVariable long equipmentId) {
        catalog.deleteEquipmentImage(equipmentId);
    }

    @DeleteMapping("/equipment-catalog/{equipmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEquipment(@PathVariable long equipmentId) {
        catalog.deleteEquipment(equipmentId);
    }

    @GetMapping("/rental-packages")
    public List<RentalPackageTemplateResponse> packages() {
        return catalog.packages();
    }

    @PostMapping("/rental-packages")
    @ResponseStatus(HttpStatus.CREATED)
    public RentalPackageTemplateResponse createPackage(@Valid @RequestBody SaveRentalPackageRequest request) {
        return catalog.createPackage(request);
    }

    @PutMapping("/rental-packages/{packageId}")
    public RentalPackageTemplateResponse updatePackage(@PathVariable long packageId,
            @Valid @RequestBody SaveRentalPackageRequest request) {
        return catalog.updatePackage(packageId, request);
    }

    @PostMapping(value = "/rental-packages/{packageId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RentalPackageTemplateResponse uploadPackageImage(@PathVariable long packageId,
            @RequestPart("file") MultipartFile file) {
        return catalog.uploadPackageImage(packageId, file);
    }

    @DeleteMapping("/rental-packages/{packageId}/image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePackageImage(@PathVariable long packageId) {
        catalog.deletePackageImage(packageId);
    }

    @DeleteMapping("/rental-packages/{packageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePackage(@PathVariable long packageId) {
        catalog.deletePackage(packageId);
    }
}
