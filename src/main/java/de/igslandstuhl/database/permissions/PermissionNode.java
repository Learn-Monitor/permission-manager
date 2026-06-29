package de.igslandstuhl.database.permissions;

public class PermissionNode {
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
}
