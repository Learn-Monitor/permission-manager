package de.igslandstuhl.database.permissions;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLHelper;

public class RoleNode {
    private static final List<RoleNode> cache = new LinkedList<>();

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
                SQLHelper.getUpdateObjectProcess("user_role", new String[]{String.valueOf(active), username, role.getName()})
            );
        } catch (SQLException e) {
            PermissionManager.getInstance().getLogger().error("Failed to persist RoleNode for user \"{}\" and role \"{}\" to database", username, role.getName(), e);
        }
    }

    public static RoleNode getRoleNode(String username, Role role) {
        RoleNode node = cache.stream()
            .filter((r) -> r.getUsername().equals(username) && r.getRole().equals(role))
            .findAny()
            .orElse(null);
        if (node != null) return node;

        AtomicReference<RoleNode> nodeRef = new AtomicReference<>();
        try {
            Server.getInstance().processRequest(
                fields -> {
                    boolean active = Boolean.parseBoolean(fields[0]);
                    nodeRef.set(new RoleNode(role, username, active));
                },
                "is_active_role_node",
                new String[] {"active"},
                username,
                role.getName())
            ;
        } catch (SQLException e) {
            PermissionManager.getInstance().getLogger().error("Failed to retrieve Role assignment from database", e);
        }
        node = nodeRef.get();

        if (node == null) {
            node = new RoleNode(role, username, false);
        }
        cache.add(node);
        return node;
    }

    public static List<Role> getUserRoles(String username) {
        List<Role> userRoles = new LinkedList<>();
        try {
            Server.getInstance().processRequest(
                fields -> {
                    Role role = Role.getByName(fields[0]);
                    if (role != null) {
                        userRoles.add(role);
                    }
                },
                "get_user_roles",
                new String[] {"role"},
                username)
            ;
        } catch (SQLException e) {
            PermissionManager.getInstance().getLogger().error("Failed to retrieve roles for user \"{}\"", username, e);
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
