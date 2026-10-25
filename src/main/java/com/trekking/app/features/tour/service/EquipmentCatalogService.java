package com.trekking.app.features.tour.service;

import com.trekking.app.features.tour.dto.request.SaveEquipmentRequest;
import com.trekking.app.features.tour.dto.request.SaveRentalPackageRequest;
import com.trekking.app.features.tour.dto.response.EquipmentCatalogResponse;
import com.trekking.app.features.tour.dto.response.RentalPackageTemplateResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface EquipmentCatalogService {
    List<EquipmentCatalogResponse> equipment();
    EquipmentCatalogResponse createEquipment(SaveEquipmentRequest request);
    EquipmentCatalogResponse updateEquipment(long equipmentId, SaveEquipmentRequest request);
    EquipmentCatalogResponse uploadEquipmentImage(long equipmentId, MultipartFile file);
    void deleteEquipmentImage(long equipmentId);
    void deleteEquipment(long equipmentId);
    List<RentalPackageTemplateResponse> packages();
    RentalPackageTemplateResponse createPackage(SaveRentalPackageRequest request);
    RentalPackageTemplateResponse updatePackage(long packageId, SaveRentalPackageRequest request);
    RentalPackageTemplateResponse uploadPackageImage(long packageId, MultipartFile file);
    void deletePackageImage(long packageId);
    void deletePackage(long packageId);
}
