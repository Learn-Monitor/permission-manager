package de.igslandstuhl.database.permissions;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import de.igslandstuhl.database.api.User;

/** Read-only comparison of persisted overrides with the current inherited result. */
final class PermissionAudit {
    private PermissionAudit() { }

    static String classify(User user, Permission permission) {
        Boolean override = PermissionNode.readOverride(user.getUsername(), permission);
        if (override == null) return "NO_OVERRIDE";
        boolean actual = has(effective(user, null), permission);
        boolean inherited = has(effective(user, permission), permission);
        if (actual == inherited) return "LEGACY_MATCH";
        return inherited ? "LEGACY_RESTRICTIVE_CONFLICT" : "LEGACY_PERMISSIVE_CONFLICT";
    }

    static Map<String, Object> report(User user, Permission permission) {
        UserEffect effect = effective(user, null);
        return Map.of(
            "classification", classify(user, permission),
            "securityConflict", effect.hasFreistellungSecurityConflict(),
            "securityStatus", effect.hasFreistellungSecurityConflict() ? "ORG_SL_EFFECTIVE_CONFLICT" : "NONE",
            "orgaSources", sources(user, "freistellung_orga"),
            "slSources", sources(user, "freistellung_sl"),
            "historicalOrgaRow", hasHistoricalRole(user, "ORGALEITER"),
            "historicalSlRow", hasHistoricalRole(user, "SCHULLEITUNG"));
    }

    private static UserEffect effective(User user, Permission inheritedInstead) {
        Set<Permission> granted = new LinkedHashSet<>();
        for (Permission permission : Permission.getAll()) {
            if (PermissionManager.getInstance().permissionEffectRegistry().get(permission) == null) continue;
            Boolean override = permission == inheritedInstead ? null : PermissionNode.readOverride(user.getUsername(), permission);
            if (override != null ? override : PermissionNode.defaultActive(user, permission)) granted.add(permission);
        }
        for (Role role : Role.getAll()) {
            if (RoleNode.readActive(user.getUsername(), role)) granted.addAll(role.getPermissions());
        }
        return new UserEffect(user, granted.toArray(Permission[]::new));
    }

    private static boolean has(UserEffect effect, Permission permission) {
        return Arrays.stream(effect.getPermissions()).anyMatch(p -> p.getName().equals(permission.getName()));
    }

    private static Set<String> sources(User user, String name) {
        Set<String> sources = new LinkedHashSet<>();
        Permission permission = Permission.getByName(name);
        if (permission == null) return sources;
        Boolean override = PermissionNode.readOverride(user.getUsername(), permission);
        if (Boolean.TRUE.equals(override)) sources.add("DIRECT_OR_LEGACY_OVERRIDE");
        if (override == null && PermissionNode.defaultActive(user, permission)) sources.add("INHERITED_DEFAULT");
        for (Role role : Role.getAll()) {
            if (RoleNode.readActive(user.getUsername(), role) && role.getPermissions().contains(permission))
                sources.add("ROLE:" + role.getName());
        }
        return sources;
    }

    private static boolean hasHistoricalRole(User user, String role) {
        var db = de.igslandstuhl.database.server.Server.getInstance().getConnection().getSQLConnection();
        try (var table = db.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name='freistellung_roles'");
             var found = table.executeQuery()) {
            if (!found.next()) return false;
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Failed to inspect historical roles", e);
        }
        try (var query = db.prepareStatement("SELECT 1 FROM freistellung_roles WHERE username=? AND role=?")) {
            query.setString(1, user.getUsername());
            query.setString(2, role);
            try (var found = query.executeQuery()) { return found.next(); }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Failed to inspect historical roles", e);
        }
    }
}
