package com.lifeadmin.admin.settings;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
    Optional<AppSetting> findByKey(String key);
}
