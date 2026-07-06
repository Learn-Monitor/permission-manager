package de.igslandstuhl.database.permissions;

import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLHelper;

public class Permission {
    private static final String[] SQL_FIELDS = {"name", "description"};
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

    private void writeToDatabase() throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(
            SQLHelper.getAddObjectProcess("permission", name, description)
        );
    }

    public void register() {
        PermissionManager permissionManager = PermissionManager.getInstance();
        permissionManager.permissionRegistry().register(getName(), this);
        try {
            writeToDatabase();
        } catch (SQLException e) {
            PermissionManager.getInstance().getLogger().error("Failed to write Permission \"{}\" to database", name, e);
        }
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

    public static void loadAll() {
        List<Permission> permissions = new LinkedList<>();
        try {
            Server.getInstance().processRequest(
                fields -> {
                    permissions.add(new Permission(fields[0], fields[1]));
                },
                "get_all_permissions",
                SQL_FIELDS)
            ;
        } catch (SQLException e) {
            PermissionManager.getInstance().getLogger().error("Failed to retrieve Permissions from database", e);
        }
        registerAll(permissions);
    }
}
