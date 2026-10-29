package de.igslandstuhl.database.permissions;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLHelper;

public class RoleNode {
    private static final List<RoleNode> cache = new CopyOnWriteArrayList<>();
    private static final Map<CacheKey, RoleNode> index = new ConcurrentHashMap<>();

    private record CacheKey(String username, String roleName) {}

    static void invalidateUsername(String username) {
        cache.removeIf(node -> node.username.equals(username));
        index.keySet().removeIf(key -> key.username().equals(username));
    }

    private final Role role;
    private final String username;
    private boolean active;

    public RoleNode(Role role, String username, boolean active) {
        this.role = role;
        this.username = username;
        this.active = active;
    }

    public Role getRole() {
        return role;
    }

    public String getUsername() {
        return username;
    }

    public boolean isActive() {
        return active;
    }

    /** Read a role assignment without creating a node or modifying caches. */
    static boolean readActive(String username, Role role) {
        try {
            Boolean value = Server.getInstance().processSingleRequest(fields -> PermissionNode.parseActive(fields[0]),
                "is_active_role_node", new String[] {"active"}, username, role.getName());
            return Boolean.TRUE.equals(value);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read role assignment", e);
        }
    }

    public void setActive(boolean active) {
        boolean previous = this.active;
        this.active = active;
        try {
            insertIntoDatabase();
            invalidateUsername(username);
            cache.add(this);
            index.put(new CacheKey(username, role.getName()), this);
        } catch (RuntimeException e) {
            this.active = previous;
            throw e;
        }
    }

    public void toggleActive() {
        setActive(!active);
    }

    private void insertIntoDatabase() {
        try {
            Server.getInstance().getConnection().executeVoidProcessSecure(
                SQLHelper.getAddObjectProcess("user_role", username, role.getName(), String.valueOf(active))
            );
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist role assignment", e);
        }
    }

    public static RoleNode getRoleNode(String username, Role role) {
        CacheKey key = new CacheKey(username, role.getName());
        if (cache.isEmpty()) index.clear();
        RoleNode node = index.get(key);
        if (node != null) return node;

        node = new RoleNode(role, username, readActive(username, role));
        cache.add(node);
        index.put(key, node);
        return node;
    }

    public static List<Role> getUserRoles(String username) {
        List<Role> userRoles = new LinkedList<>();
        // Build roles from cached RoleNodes to avoid asynchronous DB callback ordering issues.
        for (Role role : Role.getAll()) {
            RoleNode node = getRoleNode(username, role);
            if (node != null && node.isActive()) {
                userRoles.add(role);
            }
        }
        return userRoles;
    }

    @Override
    public String toString() {
        return new StringBuilder("{")
        .append("\"role\":").append(getRole()).append(",")
        .append("\"username\":\"").append(getUsername()).append("\",")
        .append("\"active\":").append(isActive())
        .append("}")
        .toString();
    }
}
