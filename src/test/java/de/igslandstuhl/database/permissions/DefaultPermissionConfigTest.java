package de.igslandstuhl.database.permissions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class DefaultPermissionConfigTest {
    @Test
    void taskRoutesKeepTheirBackendAccessLevels() throws IOException {
        Map<String, Object> config = config();
        List<Map<String, Object>> permissions = allPermissions(config);

        assertTrue(defaultsForPath(permissions, "/begin-task").contains("user"));
        assertTrue(defaultsForPath(permissions, "/cancel-task").contains("user"));
        assertFalse(defaultsForPath(permissions, "/cancel-task").stream()
            .anyMatch(level -> level.equals("student")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> config() throws IOException {
        try (InputStream stream = DefaultPermissionConfigTest.class
                .getResourceAsStream("/meta/permission-manager/permissions.json")) {
            if (stream == null) throw new IOException("Missing default permissions configuration");
            return new Yaml().load(stream);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> allPermissions(Map<String, Object> config) {
        return List.of("generics", "flat").stream()
            .flatMap(section -> ((List<Map<String, Object>>) config.get(section)).stream())
            .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<String> defaultsForPath(List<Map<String, Object>> permissions, String path) {
        return permissions.stream()
            .filter(permission -> ((List<String>) permission.get("paths")).contains(path))
            .map(permission -> (String) permission.get("default"))
            .toList();
    }
}
