package de.igslandstuhl.database.permissions.meta;

import java.util.List;
import java.util.Map;

import de.igslandstuhl.database.permissions.Permission;
import de.igslandstuhl.database.permissions.PermissionEffect;
import de.igslandstuhl.database.permissions.PermissionManager;
import de.igslandstuhl.database.permissions.generics.PermissionGeneric;
import de.igslandstuhl.database.permissions.restrictions.PostRestriction;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.webserver.access.AccessLevel;

public class PermissionsConfigLoader {
    private static PermissionsConfigLoader instance = new PermissionsConfigLoader();

    public static PermissionsConfigLoader getInstance() {
        return instance;
    }

    @SuppressWarnings("unchecked")
    private PermissionEffect getEffect(Map<String, ?> permissionJSON) {
        String name = (String) permissionJSON.get("name");
        String description = (String) permissionJSON.get("description");
        List<String> allowedPaths = (List<String>) permissionJSON.get("paths");
        List<Map<String, String>> restrictionConfigs = (List<Map<String, String>>) permissionJSON.get("post_restrictions");
        List<String> depends = (List<String>) permissionJSON.get("depends");

        Permission permission = Permission.getByNameOrCreate(name, description);
        PostRestriction[] restrictions = restrictionConfigs.stream()
        .map((m) -> PostRestriction.get(m.get("type"), m.get("restriction"), m.get("field_missing_behavior")))
        .toArray((a) -> new PostRestriction[a]);

        AccessLevel defaultLevel = AccessLevel.valueOf(((String) permissionJSON.get("default")).toUpperCase());
        return new PermissionEffect(permission, allowedPaths.toArray(new String[allowedPaths.size()]), restrictions, depends.stream().map(Permission::getByName).toArray((a) -> new Permission[a]), defaultLevel);
    }
    private void registerGenerics(List<Map<String, ?>> genericList) {
        genericList.stream()
        .flatMap(PermissionGeneric::applyAll)
        .map(this::getEffect)
        .forEach((e) -> e.register());
    }
    public void registerAllPermissionEffects() {
        Map<String, ?> permissionsMap = Server.getInstance().getResourceManager().readJsonResourceMerged(PermissionManager.PERMISSIONS_CONFIG);

        @SuppressWarnings("unchecked")
        List<Map<String, ?>> generics = (List<Map<String, ?>>) permissionsMap.get("generics");

        registerGenerics(generics);
    }
}
