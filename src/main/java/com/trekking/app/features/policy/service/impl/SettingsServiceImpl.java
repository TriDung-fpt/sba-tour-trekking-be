package com.trekking.app.features.policy.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.service.audit.AuditWriter;
import com.trekking.app.features.policy.dto.request.SystemSettingCreateRequest;
import com.trekking.app.features.policy.dto.response.SystemSettingResponse;
import com.trekking.app.features.policy.entity.SystemSetting;
import com.trekking.app.features.policy.repository.SystemSettingRepository;
import com.trekking.app.features.policy.service.SettingsService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsServiceImpl implements SettingsService {
    private static final Pattern SETTING_KEY = Pattern.compile("[a-z0-9_]{1,100}");

    private final SystemSettingRepository settings;
    private final AuthorizationService authorization;
    private final AuditWriter audits;
    private final Clock clock;

    public SettingsServiceImpl(SystemSettingRepository settings, AuthorizationService authorization,
                               AuditWriter audits, Clock clock) {
        this.settings = settings;
        this.authorization = authorization;
        this.audits = audits;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SystemSettingResponse> current() {
        authorization.require(Permission.POLICY_MANAGE);
        return settings.findCurrent(OffsetDateTime.now(clock)).stream().map(SettingsServiceImpl::response).toList();
    }

    @Transactional(readOnly = true)
    public List<SystemSettingResponse> versions(String settingKey) {
        authorization.require(Permission.POLICY_MANAGE);
        return settings.findAllBySettingKeyOrderByVersionNumberDesc(settingKey).stream()
                .map(SettingsServiceImpl::response).toList();
    }

    @Transactional
    public SystemSettingResponse publish(SystemSettingCreateRequest request) {
        authorization.require(Permission.POLICY_MANAGE);
        String key = request.settingKey();
        if (!SETTING_KEY.matcher(key).matches()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_SETTING_KEY",
                    "settingKey must be lowercase letters, digits and underscores");
        }
        settings.lockSettingKey(key);
        var previous = settings.findFirstBySettingKeyOrderByVersionNumberDesc(key);
        // PostgreSQL stores microseconds. Compare the same precision that will be persisted.
        var effectiveFrom = request.effectiveFrom().truncatedTo(ChronoUnit.MICROS);
        if (previous.isPresent() && !effectiveFrom.isAfter(previous.get().getEffectiveFrom())) {
            throw new AuthException(HttpStatus.CONFLICT, "SETTING_EFFECTIVE_FROM_OUT_OF_ORDER",
                    "A new setting version must become effective after the previous version");
        }
        var setting = new SystemSetting();
        setting.setSettingKey(key);
        setting.setVersionNumber(previous.map(value -> value.getVersionNumber() + 1).orElse(1));
        setting.setValue(request.value());
        setting.setEffectiveFrom(effectiveFrom);
        setting.setChangedBy(authorization.currentAccountId());
        setting.setChangeReason(request.reason());
        settings.saveAndFlush(setting);
        audits.record("system_setting", setting.getId(), "SETTING_VERSION_PUBLISHED",
                authorization.currentAccountId(), request.reason(),
                previous.map(SettingsServiceImpl::state).orElseGet(Map::of),
                state(setting));
        return response(setting);
    }

    private static Map<String, Object> state(SystemSetting setting) {
        var state = new LinkedHashMap<String, Object>();
        state.put("settingKey", setting.getSettingKey());
        state.put("versionNumber", setting.getVersionNumber());
        state.put("value", setting.getValue());
        state.put("effectiveFrom", setting.getEffectiveFrom().toString());
        return state;
    }

    private static SystemSettingResponse response(SystemSetting setting) {
        return new SystemSettingResponse(setting.getId(), setting.getSettingKey(), setting.getVersionNumber(),
                setting.getValue(), setting.getEffectiveFrom(), setting.getChangedBy(),
                setting.getChangeReason(), setting.getCreatedAt());
    }
}
