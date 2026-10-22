package com.fishmatrix.user.service;

import com.fishmatrix.user.model.AppMeta;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AppMetaService {

    private final AppMeta meta;

    public AppMetaService(
            @Value("${app.environment:production}") String environment,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${NACOS_NAMESPACE:}") String namespace,
            @Value("${app.version:dev}") String version) {
        String database = databaseName(datasourceUrl);
        String normalized = normalize(environment, database, namespace);
        this.meta = new AppMeta(
                normalized,
                label(normalized),
                database,
                namespace,
                version);
    }

    public AppMeta current() {
        return meta;
    }

    private static String normalize(String environment, String database, String namespace) {
        String value = environment == null ? "" : environment.trim().toLowerCase();
        String db = database == null ? "" : database.toLowerCase();
        String ns = namespace == null ? "" : namespace.toLowerCase();
        if (value.contains("test") || db.contains("test") || ns.contains("test")) {
            return "test";
        }
        if (value.contains("prod") || value.contains("production")) {
            return "production";
        }
        return "production";
    }

    private static String label(String environment) {
        return switch (environment) {
            case "test" -> "测试环境";
            case "production" -> "生产环境";
            default -> "未知环境";
        };
    }

    private static String databaseName(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        int query = url.indexOf('?');
        String base = query < 0 ? url : url.substring(0, query);
        int slash = base.lastIndexOf('/');
        if (slash < 0 || slash == base.length() - 1) {
            return "";
        }
        return base.substring(slash + 1);
    }
}
