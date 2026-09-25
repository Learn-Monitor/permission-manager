package de.igslandstuhl.database.permissions;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLHelper;
import de.igslandstuhl.database.server.webserver.access.AccessLevel;

public class PermissionNode {
    // Keep the list as the resettable cache state used by existing integrations; the map
    // makes normal lookups independent of the number of users and permissions.
    private static final List<PermissionNode> cache = new LinkedList<>();
    private static final Map<CacheKey, PermissionNode> index = new HashMap<>();
    private static boolean snapshotLoaded;

    private record CacheKey(String username, String permissionName) {}

    private final Permission permission;
    private final String username;
    private boolean active;

    public PermissionNode(Permission permission, String username, boolean active) {
        this.permission = permission;
        this.username = username;
        this.active = active;
    }

    public Permission getPermission() {
        return permission;
    }

    public String getUsername() {
        return username;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        persistToDatabase();
    }

    public void toggleActive() {
        setActive(!active);
    }

    private void persistToDatabase() {
        try {
            Server.getInstance().getConnection().executeVoidProcessSecure(
                SQLHelper.getUpdateObjectProcess("permission_node", new String[]{String.valueOf(active), permission.getName(), username})
            );
        } catch (SQLException e) {
            PermissionManager.getInstance().getLogger().error("Failed to persist PermissionNode for user \"{}\" and permission \"{}\" to database", username, permission.getName(), e);
        }
    }

    private void insertIntoDatabase() {
        try {
            Server.getInstance().getConnection().executeVoidProcessSecure(
                SQLHelper.getAddObjectProcess("permission_node", permission.getName(), username, String.valueOf(active))
            );
        } catch (SQLException e) {
            PermissionManager.getInstance().getLogger().error("Failed to insert PermissionNode for user \"{}\" and permission \"{}\" to database", username, permission.getName(), e);
        }
    }

    public static PermissionNode getPermissionNode(String username, Permission permission) {
        CacheKey key = new CacheKey(username, permission.getName());
        if (cache.isEmpty() && !index.isEmpty()) {
            index.clear();
            snapshotLoaded = false;
        }
        PermissionNode node = index.get(key);
        if (node != null) return node;

        if (snapshotLoaded) {
            AccessLevel defaultLevel = PermissionManager.getInstance().permissionEffectRegistry().get(permission).defaultLevel();
            User user = User.getUser(username);
            boolean active = isDefaultActive(defaultLevel, user);
            active = active && PermissionManager.getInstance().permissionEffectRegistry().get(permission).isDefaultEligible(user);
            node = new PermissionNode(permission, username, active);
            node.insertIntoDatabase();
            cache.add(node);
            index.put(key, node);
            return node;
        }

        AtomicReference<PermissionNode> nodeRef = new AtomicReference<>();
        try {
            Server.getInstance().processRequest(
                fields -> {
                    boolean active = Boolean.parseBoolean(fields[0]);
                    nodeRef.set(new PermissionNode(permission, username, active));
                },
                "is_active_node",
                new String[] {"active"},
                permission.getName(),
                username)
            ;
        } catch (SQLException e) {
            PermissionManager.getInstance().getLogger().error("Failed to retrieve Permissions from database", e);
        }
        node = nodeRef.get();

        if (node == null) {
            AccessLevel defaultLevel = PermissionManager.getInstance().permissionEffectRegistry().get(permission).defaultLevel();
            User user = User.getUser(username);
            boolean active = isDefaultActive(defaultLevel, user);
            active = active && PermissionManager.getInstance().permissionEffectRegistry().get(permission).isDefaultEligible(user);
            node = new PermissionNode(permission, username, active);
            node.insertIntoDatabase();
        }
        cache.add(node);
        index.put(key, node);
        return node;
    }

    /** Load persistent node state once for a rebuild; failures retain legacy lookup behavior. */
    public static boolean loadSnapshot() {
        cache.clear();
        index.clear();
        snapshotLoaded = false;
        try {
            Server.getInstance().processRequest(
                fields -> {
                    Permission permission = Permission.getByName(fields[0]);
                    if (permission != null) {
                        PermissionNode node = new PermissionNode(permission, fields[1], Boolean.parseBoolean(fields[2]));
                        cache.add(node);
                        index.put(new CacheKey(fields[1], fields[0]), node);
                    }
                },
                "get_all_permission_nodes",
                new String[] {"permission", "username", "active"});
            snapshotLoaded = true;
            return true;
        } catch (SQLException e) {
            PermissionManager.getInstance().getLogger().error("Failed to load PermissionNode snapshot", e);
            return false;
        }
    }

    static boolean isDefaultActive(AccessLevel defaultLevel, User user) {
        return switch (defaultLevel) {
            case ADMIN -> user.isAdmin();
            case TEACHER -> user.isTeacher() || user.isAdmin();
            case STUDENT -> user.isStudent();
            case USER -> user.isStudent() || user.isTeacher() || user.isAdmin();
            case PUBLIC -> true;
            default -> false;
        };
    }

    @Override
    public String toString() {
        return new StringBuilder("{")
        .append("\"permission\":").append(getPermission()).append(",")
        .append("\"username\":\"").append(getUsername()).append("\",")
        .append("\"active\":").append(isActive())
        .append("}")
        .toString();
    }
}
