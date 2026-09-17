package com.lifeadmin.admin.doctype;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTypeConfigRepository extends JpaRepository<DocumentTypeConfig, String> {
    List<DocumentTypeConfig> findByEnabledTrueOrderBySortOrderAsc();
    List<DocumentTypeConfig> findAllByOrderBySortOrderAsc();
}
