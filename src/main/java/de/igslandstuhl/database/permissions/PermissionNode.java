package de.igslandstuhl.database.permissions;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.webserver.access.AccessLevel;

public class PermissionNode {
    private static final List<PermissionNode> cache = new LinkedList<>();

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
    }
    public void toggleActive() {
        setActive(!active);
    }

    public static PermissionNode getPermissionNode(String username, Permission permission) {
        PermissionNode node = cache.stream().filter((p) -> p.getUsername().equals(username) && p.getPermission().equals(permission)).findAny().orElse(null);
        if (node != null) return node;

        AtomicReference<PermissionNode> nodeRef = new AtomicReference<>();
        try {
            Server.getInstance().processRequest(
                fields -> {
                    boolean active = Boolean.parseBoolean(fields[0]);
                    nodeRef.set(new PermissionNode(permission, username, active));
                },
                "is_active_node",
                new String[] {"active"})
            ;
        } catch (SQLException e) {
            PermissionManager.getInstance().getLogger().error("Failed to retrieve Permissions from database", e);
        }
        node = nodeRef.get();

        if (node == null) {
            AccessLevel defaultLevel = PermissionManager.getInstance().permissionEffectRegistry().get(permission).defaultLevel();
            User user = User.getUser(username);
            boolean active = false;
            switch (defaultLevel) {
                case ADMIN:
                    if (!user.isAdmin()) break;
                case TEACHER:
                    if (!(user.isTeacher() || user.isAdmin())) break;
                case STUDENT:
                case USER:
                    if (!(user.isStudent() || user.isTeacher() || user.isAdmin())) break;
                case PUBLIC:
                    active = true;
                    break;
                default:
                    break;
            }
            node = new PermissionNode(permission, username, active);
        }
        cache.add(node);
        return node;
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
