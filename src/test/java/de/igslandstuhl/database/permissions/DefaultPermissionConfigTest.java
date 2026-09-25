package de.igslandstuhl.database.permissions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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
        assertTrue(defaultsForPath(permissions, "/complete-task").contains("teacher"));
        assertFalse(defaultsForPath(permissions, "/complete-task").stream()
            .anyMatch(level -> level.equals("student") || level.equals("user")));
        assertEquals(List.of("teacher"), defaultsForPath(permissions, "/complete-flexible-task"));
        assertFalse(defaultsForPath(permissions, "/cancel-task").stream()
            .anyMatch(level -> level.equals("student")));
    }

    @Test
    void arcanumRoutesPreserveRoleBoundaries() throws IOException {
        List<Map<String, Object>> permissions = allPermissions(config());

        assertTrue(defaultsForPath(permissions, "/results").contains("student"));
        assertTrue(defaultsForPath(permissions, "/search-partner").contains("student"));
        assertTrue(defaultsForPath(permissions, "/cancel-task").contains("user"));

        assertEquals(List.of("teacher"), defaultsForPath(permissions, "/attendance"));
        assertEquals(List.of("admin"), defaultsForPath(permissions, "/attendance-admin"));
        assertFalse(defaultsForPath(permissions, "/attendance-admin").contains("teacher"));
        assertFalse(defaultsForPath(permissions, "/attendance").stream()
            .anyMatch(level -> level.equals("student") || level.equals("user") || level.equals("public")));
    }

    @Test
    void attendanceCheckinAndSignageRemainPublic() throws IOException {
        List<Map<String, Object>> permissions = allPermissions(config());

        assertEquals(List.of("public"), defaultsForPath(permissions, "/attendance-checkin"));
        assertEquals(List.of("public"), defaultsForPath(permissions, "/attendance-display"));
        assertEquals(List.of("public"), defaultsForPath(permissions, "/attendance-display-token"));
        assertEquals(List.of("public"), defaultsForPath(permissions, "/attendance-signage-token"));
        assertTrue(defaultsForPath(permissions, "/attendance-pending").isEmpty());
        assertTrue(defaultsForPath(permissions, "/attendance-resume").isEmpty());
    }

    @Test
    void teacherNavigationScriptRemainsPublic() throws IOException {
        List<Map<String, Object>> permissions = allPermissions(config());
        Map<String, Object> standardFiles = permissionByName(permissions, "standard_files");

        assertEquals("public", standardFiles.get("default"));
        assertTrue(paths(standardFiles).contains("/teacher-navigation.js"));
        assertEquals(List.of("public"), defaultsForPath(permissions, "/teacher-navigation.js"));
    }

    @Test
    void adminPolishStudentManagementRoutesAreAdminOnlyAndDropHardDelete() throws IOException {
        List<Map<String, Object>> permissions = allPermissions(config());
        Map<String, Object> manageStudents = permissionByName(permissions, "manage_students");

        assertEquals("admin", manageStudents.get("default"));
        for (String path : List.of(
                "/manage_students",
                "/manage_students.js",
                "/students",
                "/archived-students",
                "/add-students",
                "/edit-student-profile",
                "/admin-student-profile.js",
                "/archive-student",
                "/reactivate-student")) {
            assertTrue(paths(manageStudents).contains(path), path);
            assertAdminOnlyDefault(permissions, path);
        }

        assertFalse(allPaths(permissions).contains("/delete-student"));
    }

    @Test
    void adminDashboardScriptIsAdminOnly() throws IOException {
        List<Map<String, Object>> permissions = allPermissions(config());
        Map<String, Object> coreAdmin = permissionByName(permissions, "core_admin_compat");

        assertEquals("admin", coreAdmin.get("default"));
        assertTrue(paths(coreAdmin).contains("/admin-dashboard.js"));
        assertAdminOnlyDefault(permissions, "/admin-dashboard.js");
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

    private static Map<String, Object> permissionByName(List<Map<String, Object>> permissions, String name) {
        return permissions.stream()
            .filter(permission -> name.equals(permission.get("name")))
            .findFirst()
            .orElseThrow();
    }

    private static List<String> allPaths(List<Map<String, Object>> permissions) {
        return permissions.stream()
            .flatMap(permission -> paths(permission).stream())
            .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<String> paths(Map<String, Object> permission) {
        return new ArrayList<>((List<String>) permission.get("paths"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> defaultsForPath(List<Map<String, Object>> permissions, String path) {
        return permissions.stream()
            .filter(permission -> ((List<String>) permission.get("paths")).contains(path))
            .map(permission -> (String) permission.get("default"))
            .toList();
    }

    private static void assertAdminOnlyDefault(List<Map<String, Object>> permissions, String path) {
        List<String> defaults = defaultsForPath(permissions, path);

        assertTrue(defaults.contains("admin"), path);
        assertFalse(defaults.stream()
            .anyMatch(level -> level.equals("teacher") || level.equals("student") || level.equals("user") || level.equals("public")),
            path);
    }
}
