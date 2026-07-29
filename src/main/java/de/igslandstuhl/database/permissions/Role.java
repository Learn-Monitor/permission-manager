package de.igslandstuhl.database.permissions;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class Role {
    private final String name;
    private final String description;
    private List<Permission> permissions = new LinkedList<>();

    public Role(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<Permission> getPermissions() {
        return new LinkedList<>(permissions);
    }

    public void addPermission(Permission permission) {
        if (!permissions.contains(permission)) {
            permissions.add(permission);
        }
    }

    public void addPermissions(Permission... permissions) {
        for (Permission permission : permissions) {
            addPermission(permission);
        }
    }

    public void addPermissions(Collection<Permission> permissions) {
        for (Permission permission : permissions) {
            addPermission(permission);
        }
    }

    public void togglePermission(Permission permission) {
        if (permissions.contains(permission)) {
            permissions.remove(permission);
        } else {
            permissions.add(permission);
        }
    }

    public void register() {
        PermissionManager permissionManager = PermissionManager.getInstance();
        permissionManager.roleRegistry().register(getName(), this);
    }

    public static void registerAll(Role... roles) {
        for (Role role : roles) {
            role.register();
        }
    }

    public static void registerAll(Collection<Role> roles) {
        for (Role role : roles) {
            role.register();
        }
    }

    public static Role getByName(String name) {
        return PermissionManager.getInstance().roleRegistry().get(name);
    }

    public static Role getByNameOrCreate(String name, String description) {
        Role role = getByName(name);
        if (role == null) {
            role = new Role(name, description);
            role.register();
        }
        return role;
    }

    public static List<Role> getAll() {
        return PermissionManager.getInstance().roleRegistry().keyStream().map(Role::getByName).toList();
    }

    @Override
    public String toString() {
        return new StringBuilder("{\"name\":")
        .append('"').append(getName()).append("\",")
        .append("\"description\":")
        .append('"').append(getDescription()).append("\",")
        .append("\"permissions\":")
        .append(permissions.toString())
        .append("}")
        .toString();
    }
}
