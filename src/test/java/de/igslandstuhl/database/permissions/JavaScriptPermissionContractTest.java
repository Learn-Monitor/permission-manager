package de.igslandstuhl.database.permissions;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class JavaScriptPermissionContractTest {

    @Test
    void getPermissionsPathIsAvailableToLoggedInUsers() throws IOException {
        String paths = resource("/meta/paths/get_paths.json");

        assertTrue(paths.contains("\"/get-permissions\""));
        assertTrue(paths.contains("\"access_level\": \"user\""));
    }

    @Test
    void javascriptLoadsAndExposesCurrentPermissions() throws IOException {
        String javascript = resource("/js/site/student-database.js");

        assertTrue(javascript.contains("getJson('/get-permissions')"));
        assertTrue(javascript.contains("const permissionsLoaded = loadCurrentPermissions();"));
        assertTrue(javascript.contains("async function hasPermission(permission)"));
        assertTrue(javascript.contains("await permissionsLoaded;"));
    }

    private static String resource(String path) throws IOException {
        try (InputStream stream =
                 JavaScriptPermissionContractTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Missing resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
