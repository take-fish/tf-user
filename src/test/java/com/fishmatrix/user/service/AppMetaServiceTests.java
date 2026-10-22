package com.fishmatrix.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fishmatrix.user.model.AppMeta;
import org.junit.jupiter.api.Test;

class AppMetaServiceTests {

    @Test
    void marksTestEnvironmentFromExplicitValue() {
        AppMeta meta = new AppMetaService(
                "test",
                "jdbc:mysql://db/fm_test_user?useUnicode=true",
                "fm-test",
                "test-build").current();

        assertEquals("test", meta.environment());
        assertEquals("测试环境", meta.environmentLabel());
        assertEquals("fm_test_user", meta.database());
        assertEquals("fm-test", meta.namespace());
    }

    @Test
    void infersTestEnvironmentFromDatabaseWhenValueMissing() {
        AppMeta meta = new AppMetaService(
                "",
                "jdbc:mysql://db/fm_test_user",
                "",
                "dev").current();

        assertEquals("test", meta.environment());
    }

    @Test
    void defaultsToProductionForProductionDatabase() {
        AppMeta meta = new AppMetaService(
                "production",
                "jdbc:mysql://db/fm_user",
                "fm-prod",
                "2026.10.22").current();

        assertEquals("production", meta.environment());
        assertEquals("生产环境", meta.environmentLabel());
        assertEquals("fm_user", meta.database());
    }
}
