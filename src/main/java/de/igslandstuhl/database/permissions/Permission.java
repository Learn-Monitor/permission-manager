package de.igslandstuhl.database.permissions;

import java.util.Collection;

public class Permission {
    private final String name;
    private final String description;

    public Permission(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void register() {
        PermissionManager permissionManager = PermissionManager.getInstance();
        permissionManager.permissionRegistry().register(getName(), this);
    }
    public static void registerAll(Permission... permissions) {
        for (Permission permission : permissions) {
            permission.register();
        }
    }
    public static void registerAll(Collection<Permission> permissions) {
        for (Permission permission : permissions) {
            permission.register();
        }
    }
}
