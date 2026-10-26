package com.trekking.app.features.policy.repository;

import com.trekking.app.features.policy.entity.SystemSetting;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SystemSettingRepository extends Repository<SystemSetting, Long> {
    SystemSetting saveAndFlush(SystemSetting setting);

    Optional<SystemSetting> findFirstBySettingKeyOrderByVersionNumberDesc(String settingKey);

    List<SystemSetting> findAllBySettingKeyOrderByVersionNumberDesc(String settingKey);

    @Query(value = """
            select distinct on (setting_key) *
            from trekking.system_settings
            where effective_from <= :now
            order by setting_key, version_number desc
            """, nativeQuery = true)
    List<SystemSetting> findCurrent(@Param("now") OffsetDateTime now);

    @Query(value = "select pg_advisory_xact_lock(hashtext(:key))", nativeQuery = true)
    void lockSettingKey(@Param("key") String key);
}
