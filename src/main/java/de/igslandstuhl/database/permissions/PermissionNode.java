package de.igslandstuhl.database.permissions;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLHelper;
import de.igslandstuhl.database.server.webserver.access.AccessLevel;

public class PermissionNode {
    // Keep the list as the resettable cache state used by existing integrations; the map
    // makes normal lookups independent of the number of users and permissions.
    private static final List<PermissionNode> cache = new CopyOnWriteArrayList<>();
    private static final Map<CacheKey, PermissionNode> index = new ConcurrentHashMap<>();
    private static volatile boolean snapshotLoaded;

    private record CacheKey(String username, String permissionName) {}

    static void invalidateUsername(String username) {
        cache.removeIf(node -> node.username.equals(username));
        index.keySet().removeIf(key -> key.username().equals(username));
    }

    private static void invalidatePermission(String username, String permissionName) {
        cache.removeIf(node -> node.username.equals(username) && node.permission.getName().equals(permissionName));
        index.remove(new CacheKey(username, permissionName));
    }

    private final Permission permission;
    private final String username;
    private boolean active;
    private boolean override;

    public PermissionNode(Permission permission, String username, boolean active) {
        this(permission, username, active, true);
    }

    private PermissionNode(Permission permission, String username, boolean active, boolean override) {
        this.permission = permission;
        this.username = username;
        this.active = active;
        this.override = override;
    }

    public Permission getPermission() {
        return permission;
    }

    public String getUsername() {
        return username;
    }

    public boolean isActive() {
        return override ? active : defaultActive(username, permission);
    }

    /** Use the already resolved canonical user while rebuilding a permission set. */
    boolean isActive(User user) {
        return override ? active : defaultActive(user, permission);
    }

    public void setActive(boolean active) {
        boolean previous = this.active;
        this.active = active;
        try {
            insertIntoDatabase();
            override = true;
            invalidatePermission(username, permission.getName());
            cache.add(this);
            index.put(new CacheKey(username, permission.getName()), this);
        } catch (RuntimeException e) {
            this.active = previous;
            throw e;
        }
    }

    public void toggleActive() {
        setActive(!isActive());
    }

    /** Removes the individual override and resumes the current identity default. */
    public void resetToInherited() {
        try {
            Server.getInstance().getConnection().executeVoidProcessSecure(
                SQLHelper.getDeleteObjectProcess("permission_node", permission.getName(), username));
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to reset permission override", e);
        }
        override = false;
        invalidatePermission(username, permission.getName());
    }

    public boolean hasOverride() { return override; }

    /** Read the persisted override without materializing defaults or modifying caches. */
    static Boolean readOverride(String username, Permission permission) {
        try {
            return Server.getInstance().processSingleRequest(fields -> parseActive(fields[0]),
                "is_active_node", new String[] {"active"}, permission.getName(), username);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read permission override", e);
        }
    }

    static boolean readDirectActive(String username, Permission permission) {
        Boolean stored = readOverride(username, permission);
        return stored != null ? stored : defaultActive(username, permission);
    }

    private void insertIntoDatabase() {
        try {
            Server.getInstance().getConnection().executeVoidProcessSecure(
                SQLHelper.getAddObjectProcess("permission_node", permission.getName(), username, String.valueOf(active))
            );
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist permission override", e);
        }
    }

    public static synchronized PermissionNode getPermissionNode(String username, Permission permission) {
        CacheKey key = new CacheKey(username, permission.getName());
        if (cache.isEmpty() && !index.isEmpty()) {
            index.clear();
            snapshotLoaded = false;
        }
        PermissionNode node = index.get(key);
        if (node != null) return node;

        if (snapshotLoaded) {
            return new PermissionNode(permission, username, false, false);
        }

        Boolean persisted;
        try {
            persisted = readOverride(username, permission);
        } catch (IllegalStateException e) {
            PermissionManager.getInstance().getLogger().error("Failed to retrieve Permissions from database", e);
            return new PermissionNode(permission, username, false, true);
        }
        if (persisted == null) {
            return new PermissionNode(permission, username, false, false);
        }
        node = new PermissionNode(permission, username, persisted);
        cache.add(node);
        index.put(key, node);
        return node;
    }

    /** Load persistent node state once for a rebuild; failures retain legacy lookup behavior. */
    public static synchronized boolean loadSnapshot() {
        List<PermissionNode> loaded = new java.util.ArrayList<>();
        try (var statement = Server.getInstance().getConnection().getSQLConnection()
                .prepareStatement("SELECT permission, username, active FROM permnodes");
             var rows = statement.executeQuery()) {
            while (rows.next()) {
                Permission permission = Permission.getByName(rows.getString("permission"));
                if (permission != null) loaded.add(new PermissionNode(permission, rows.getString("username"), parseActive(rows.getString("active"))));
            }
            cache.clear();
            index.clear();
            for (PermissionNode node : loaded) {
                cache.add(node);
                index.put(new CacheKey(node.username, node.permission.getName()), node);
            }
            snapshotLoaded = true;
            return true;
        } catch (SQLException e) {
            cache.clear();
            index.clear();
            snapshotLoaded = false;
            PermissionManager.getInstance().getLogger().error("Failed to load PermissionNode snapshot", e);
            return false;
        }
    }

    static boolean isDefaultActive(AccessLevel defaultLevel, User user) {
        if (user == null || user == User.ANONYMOUS || defaultLevel == null) return false;
        return switch (defaultLevel) {
            case ADMIN -> user.isAdmin();
            case TEACHER -> user.isTeacher() || user.isAdmin();
            case STUDENT -> user.isStudent();
            case USER -> user.isStudent() || user.isTeacher() || user.isAdmin();
            case PUBLIC -> true;
            default -> false;
        };
    }

    static boolean parseActive(String value) {
        // Preserve the historical interpretation of existing stored values;
        // treating legacy numeric 1 as a new grant would silently escalate access.
        return "true".equalsIgnoreCase(value);
    }

    static boolean defaultActive(String username, Permission permission) {
        return defaultActive(User.getUser(username), permission);
    }

    static boolean defaultActive(User user, Permission permission) {
        PermissionEffect effect = PermissionManager.getInstance().permissionEffectRegistry().get(permission);
        return effect != null && isDefaultActive(effect.defaultLevel(), user) && effect.isDefaultEligible(user);
    }

    static boolean defaultActiveWithAdmin(User user, Permission permission, boolean admin) {
        PermissionEffect effect = PermissionManager.getInstance().permissionEffectRegistry().get(permission);
        if (effect == null || user == null || user == User.ANONYMOUS) return false;
        boolean student = user.isStudent(), teacher = user.isTeacher();
        boolean active = switch (effect.defaultLevel()) {
            case ADMIN -> admin;
            case TEACHER -> teacher || admin;
            case STUDENT -> student;
            case USER -> student || teacher || admin;
            case PUBLIC -> true;
            default -> false;
        };
        if (!effect.exactDefault()) return active;
        return active && switch (effect.defaultLevel()) {
            case STUDENT -> student;
            case TEACHER -> teacher;
            case ADMIN -> admin;
            default -> false;
        };
    }

    @Override
    public String toString() {
        return new StringBuilder("{")
        .append("\"permission\":").append(getPermission()).append(",")
        .append("\"username\":\"").append(getUsername()).append("\",")
        .append("\"active\":").append(isActive()).append(",")
        .append("\"override\":\"").append(override ? (active ? "allow" : "deny") : "inherit").append("\"")
        .append("}")
        .toString();
    }
}
