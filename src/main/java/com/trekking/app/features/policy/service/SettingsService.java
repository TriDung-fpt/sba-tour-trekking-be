package com.trekking.app.features.policy.service;

import com.trekking.app.features.policy.dto.request.SystemSettingCreateRequest;
import com.trekking.app.features.policy.dto.response.SystemSettingResponse;
import java.util.List;

public interface SettingsService {
    List<SystemSettingResponse> current();
    List<SystemSettingResponse> versions(String settingKey);
    SystemSettingResponse publish(SystemSettingCreateRequest request);
}
