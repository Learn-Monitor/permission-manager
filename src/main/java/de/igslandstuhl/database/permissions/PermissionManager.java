package de.igslandstuhl.database.permissions;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.api.SchoolClass;
import de.igslandstuhl.database.api.Subject;
import de.igslandstuhl.database.permissions.meta.PermissionManagerConfig;
import de.igslandstuhl.database.permissions.meta.PermissionsConfigLoader;
import de.igslandstuhl.database.plugins.Plugin;
import de.igslandstuhl.database.server.resources.ResourceLocation;

public class PermissionManager extends Plugin {
    public static final ResourceLocation PERMISSIONS_CONFIG = new ResourceLocation("meta", "permission-manager", "permissions.json");

    private static PermissionManager instance;
    private final PermissionManagerConfig config = new PermissionManagerConfig(this);

    private final Registry<String, Permission> permissionRegistry = new Registry<>();
    private final Registry<Permission, PermissionEffect> permissionEffectRegistry = new Registry<>();

    public PermissionManager() {
        instance = this;
    }

    public static PermissionManager getInstance() {
        return instance;
    }

    @Override
    public PermissionManagerConfig getConfig() {
        return config;
    }

    public Registry<String, Permission> permissionRegistry() {
        return permissionRegistry;
    }
    public Registry<Permission, PermissionEffect> permissionEffectRegistry() {
        return permissionEffectRegistry;
    }

    @Override
    protected void onDisable() {
        getLogger().warn("Permission Manager plugin disabled. This can cause problems while running, better remove it instead.");
    }

    @Override
    protected void onEnable() {
        getLogger().info("Successfully enabled Permission Manager");
    }

    public String studentViewPermission(SchoolClass schoolClass) {
        return "view_student_data_" + schoolClass.getLabel();
    }
    public String studentViewPermission(SchoolClass schoolClass, Subject subject) {
        return "view_student_data_" + schoolClass.getLabel() + "_" + subject.getName();
    }
    public String studentWritePermission(SchoolClass schoolClass) {
        return "write_student_data_" + schoolClass.getLabel();
    }
    public String studentWritePermission(SchoolClass schoolClass, Subject subject) {
        return "write_student_data_" + schoolClass.getLabel() + "_" + subject.getName();
    }

    @Override
    protected void onLoad() {
        getLogger().info("Loading permission manager...");
        getLogger().info("Loading permissions...");
        Permission.loadAll();
        getLogger().info("Registering permission effects...");
        PermissionsConfigLoader.getInstance().registerAllPermissionEffects();
        getLogger().info("permission-manager successfully loaded.");
    }
}
