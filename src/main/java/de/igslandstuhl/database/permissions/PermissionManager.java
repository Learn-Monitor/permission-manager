package de.igslandstuhl.database.permissions;

import java.util.ArrayList;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.api.SchoolClass;
import de.igslandstuhl.database.api.Subject;
import de.igslandstuhl.database.permissions.meta.PermissionManagerConfig;
import de.igslandstuhl.database.plugins.Plugin;

public class PermissionManager extends Plugin {
    private static PermissionManager instance;
    private final PermissionManagerConfig config = new PermissionManagerConfig(this);

    private final Registry<String, Permission> permissionRegistry = new Registry<>();

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
        for (SchoolClass schoolClass : SchoolClass.getAll()) {
            ArrayList<Permission> classDependedPerms = new ArrayList<>();
            Permission viewPermission = new Permission(studentViewPermission(schoolClass), "Viewing student data for this school class (auto-generated on start)");
            classDependedPerms.add(viewPermission);
            Permission writePermission = new Permission(studentWritePermission(schoolClass), "Writing student data for this school class (auto-generated on start)");
            classDependedPerms.add(writePermission);
            for (Subject subject : Subject.getAll()) {
                Permission subjectViewPermission = new Permission(studentViewPermission(schoolClass, subject), "Viewing a student's " + subject.getName() + " data for this school class (auto-generated on start)");
                classDependedPerms.add(subjectViewPermission);
                Permission subjectWritePermission = new Permission(studentWritePermission(schoolClass, subject), "Writing a student's " + subject.getName() + " data for this school class (auto-generated on start)");
                classDependedPerms.add(subjectWritePermission);
            }
            Permission.registerAll(classDependedPerms);
        }
        getLogger().info("Loaded class depended perms");
    }
    
}
