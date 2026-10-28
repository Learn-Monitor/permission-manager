package de.igslandstuhl.database.permissions;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.api.SchoolClass;
import de.igslandstuhl.database.api.Subject;
import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.api.Admin;
import de.igslandstuhl.database.api.Teacher;
import de.igslandstuhl.database.server.resources.ResourceLocation;
import de.igslandstuhl.database.server.webserver.ContentType;
import de.igslandstuhl.database.server.webserver.access.AccessLevel;
import de.igslandstuhl.database.server.webserver.handlers.HttpHandler;
import de.igslandstuhl.database.server.webserver.responses.PostResponse;
import de.igslandstuhl.database.permissions.generics.*;
import de.igslandstuhl.database.permissions.meta.*;
import de.igslandstuhl.database.permissions.restrictions.RestrictionTypeHelper;
import de.igslandstuhl.database.plugins.Plugin;

public class PermissionManager extends Plugin {
    public static final ResourceLocation PERMISSIONS_CONFIG = new ResourceLocation("meta", "permission-manager", "permissions.json");

    private static PermissionManager instance;
    private final PermissionManagerConfig config = new PermissionManagerConfig(this);

    private final Registry<String, Permission> permissionRegistry = new Registry<>();
    private final Registry<Permission, PermissionEffect> permissionEffectRegistry = new Registry<>();
    private final Map<String, Role> roles = new HashMap<>();
    private static final Set<String> FREISTELLUNG_PROCESSING = Set.of("freistellung_orga", "freistellung_sl");
    private static final String FREISTELLUNG_APPLY = "freistellung_apply";

    public PermissionManager() {
        instance = this;
    }

    public static PermissionManager getInstance() {
        return instance;
    }
    public static String convertName(String name) {
        return name.toLowerCase().replace(" ", "_");
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
    public Map<String, Role> getRoles() {
        return roles;
    }

    @Override
    protected void onDisable() {
        getLogger().info("Saving role configuration...");
        RolesYamlConfigLoader.getInstance().saveRoles();
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
        getLogger().info("Loading roles from YAML configuration...");
        RolesYamlConfigLoader.getInstance().registerAllRoles();
        getLogger().info("Registering permission generics...");
        SchoolClassGeneric.getInstance().register();
        SubjectGeneric.getInstance().register();
        getLogger().info("Registering restriction types...");
        RestrictionTypeHelper.registerDefaults();
        getLogger().info("Registering permission effects...");
        PermissionsConfigLoader.getInstance().registerAllPermissionEffects();
        getLogger().info("Registering user effects...");
        UserEffect.registerAll();
        UserAccountDeletedListener.getInstance().register();
        getLogger().info("Registering request handlers...");
        Registry.sqlRequestHandlerRegistry().register("list-permissions", (u) -> {
            return Permission.getAll()
            .toString();
        });
        Registry.sqlRequestHandlerRegistry().register("list-roles", (u) -> {
            return Role.getAll()
            .toString();
        });
        Registry.sqlRequestHandlerRegistry().register("list-users", (u) -> {
            return User.getAllUsers()
            .stream()
            .map((u1) -> '"' + u1.getUsername() + '"')
            .toList().toString();
        });
        Registry.sqlRequestHandlerRegistry().register("get-permissions", (u) -> {
            UserEffect effect = UserEffect.get(u);
            if (effect == null) {
                return "[]";
            }
            return Arrays.stream(effect.getPermissions())
                .map(Permission::toString)
                .toList()
                .toString();
        });
        HttpHandler.registerPostRequestHandler("/toggle-permission", AccessLevel.ADMIN, (rq) -> {
            User user = User.getUser(rq.getString("user"));
            Permission permission = Permission.getByName(rq.getString("permission"));

            if (user != null && permission != null) {
                if (isFreistellung(permission.getName())) {
                    return toggleFreistellung(user, permission, rq);
                }
                PermissionNode.getPermissionNode(user.getUsername(), permission).toggleActive();
                UserEffect.registerAll();
                return PostResponse.ok("Successfully toggled permission for user " + user.getUsername(), ContentType.TEXT_PLAIN, rq);
            } else {
                return PostResponse.badRequest("User or permission does not exist", rq);
            }
        });
        HttpHandler.registerPostRequestHandler("/toggle-role", AccessLevel.ADMIN, (rq) -> {
            User user = User.getUser(rq.getString("user"));
            Role role = Role.getByName(rq.getString("role"));

            if (user != null && role != null) {
                RoleNode.getRoleNode(user.getUsername(), role).toggleActive();
                UserEffect.registerAll();
                return PostResponse.ok("Successfully toggled role for user " + user.getUsername(), ContentType.TEXT_PLAIN, rq);
            } else {
                return PostResponse.badRequest("User or role does not exist", rq);
            }
        });
        HttpHandler.registerPostRequestHandler("/toggle-role-permission", AccessLevel.ADMIN, (rq) -> {
            Permission permission = Permission.getByName(rq.getString("permission"));
            Role role = Role.getByName(rq.getString("role"));

            if (permission != null && role != null) {
                if (isFreistellung(permission.getName()) && !toggleRoleFreistellung(role, permission)) {
                    return PostResponse.badRequest("Freistellungsrechte: Antrag, Orga-Leitung und Schulleitung müssen konsistent bleiben", rq);
                }
                role.togglePermission(permission);
                UserEffect.registerAll();
                return PostResponse.ok("Successfully toggled permission for role " + role.getName(), ContentType.TEXT_PLAIN, rq);
            } else {
                return PostResponse.badRequest("Permission or role does not exist", rq);
            }
        });
        HttpHandler.registerPostRequestHandler("/get-freistellung-rights", AccessLevel.ADMIN, (rq) -> {
            var users = User.getAllUsers().stream().filter(user -> user != User.ANONYMOUS).collect(java.util.stream.Collectors.toMap(User::getUsername, user -> user, (first, second) -> second.isTeacher() ? second : first)).values().stream().map(user -> {
                Permission apply = Permission.getByName(FREISTELLUNG_APPLY);
                Permission orga = Permission.getByName("freistellung_orga");
                Permission sl = Permission.getByName("freistellung_sl");
                return Map.of(
                    "username", user.getUsername(),
                    "teacher", user.isTeacher(),
                    "admin", user.isAdmin(),
                    "apply", PermissionNode.getPermissionNode(user.getUsername(), apply).isActive(),
                    "orga", PermissionNode.getPermissionNode(user.getUsername(), orga).isActive(),
                    "sl", PermissionNode.getPermissionNode(user.getUsername(), sl).isActive()
                );
            }).toList();
            return PostResponse.json(users, rq);
        });
        HttpHandler.registerPostRequestHandler("/set-admin-role", AccessLevel.ADMIN, (rq) -> {
            User user = User.getUser(rq.getString("user"));
            boolean enabled = rq.getBoolean("enabled");
            if (!(user instanceof Teacher teacher)) return PostResponse.badRequest("Nur Lehrkräfte können die Admin-Rolle erhalten", rq);
            if (enabled) {
                if (!teacher.isAdmin()) Admin.createWithPasswordHash(teacher.getUsername(), teacher.getPasswordHash());
            } else {
                Admin admin = Admin.get(teacher.getUsername());
                if (admin != null) admin.delete();
            }
            return PostResponse.ok("Organisationsrolle gespeichert", ContentType.TEXT_PLAIN, rq);
        });
        HttpHandler.registerPostRequestHandler("/set-freistellung-right", AccessLevel.ADMIN, (rq) -> {
            User user = User.getUser(rq.getString("user"));
            String right = rq.getString("right");
            if (user == null || right == null || !Set.of("none", "apply", "orga", "sl").contains(right)) {
                return PostResponse.badRequest("Konto oder Freistellungsrecht ungültig", rq);
            }
            Permission apply = Permission.getByName(FREISTELLUNG_APPLY);
            Permission orga = Permission.getByName("freistellung_orga");
            Permission sl = Permission.getByName("freistellung_sl");
            PermissionNode applyNode = PermissionNode.getPermissionNode(user.getUsername(), apply);
            PermissionNode orgaNode = PermissionNode.getPermissionNode(user.getUsername(), orga);
            PermissionNode slNode = PermissionNode.getPermissionNode(user.getUsername(), sl);
            applyNode.setActive(right.equals("apply") || right.equals("orga") || right.equals("sl"));
            orgaNode.setActive(right.equals("orga"));
            slNode.setActive(right.equals("sl"));
            UserEffect.registerAll();
            return PostResponse.ok("Freistellungsrecht gespeichert", ContentType.TEXT_PLAIN, rq);
        });
        HttpHandler.registerPostRequestHandler("/get-role", AccessLevel.ADMIN, (rq) -> {
            Role role = Role.getByName(rq.getString("role"));

            if (role != null) {
                return PostResponse.ok(role.toString(), ContentType.JSON, rq);
            } else {
                return PostResponse.badRequest("Role does not exist", rq);
            }
        });
        HttpHandler.registerPostRequestHandler("/get-permission-node", AccessLevel.ADMIN, (rq) -> {
            User user = User.getUser(rq.getString("user"));
            Permission permission = Permission.getByName(rq.getString("permission"));

            if (user != null && permission != null) {
                PermissionNode node = PermissionNode.getPermissionNode(user.getUsername(), permission);
                return PostResponse.ok(node.toString(), ContentType.JSON, rq);
            } else {
                return PostResponse.badRequest("User or permission does not exist", rq);
            }
        });
        HttpHandler.registerPostRequestHandler("/get-role-node", AccessLevel.ADMIN, (rq) -> {
            User user = User.getUser(rq.getString("user"));
            Role role = Role.getByName(rq.getString("role"));

            if (user != null && role != null) {
                RoleNode node = RoleNode.getRoleNode(user.getUsername(), role);
                return PostResponse.ok(node.toString(), ContentType.JSON, rq);
            } else {
                return PostResponse.badRequest("User or role does not exist", rq);
            }
        });
        HttpHandler.registerPostRequestHandler("/add-role", AccessLevel.ADMIN, (rq) -> {
            String name = rq.getString("name");
            String description = rq.getString("description");

            if (name != null && description != null) {
                Role.getByNameOrCreate(name, description);
                return PostResponse.redirect("/manage_permissions", rq);
            } else {
                return PostResponse.badRequest("Either name or description are not present.", rq);
            }
        });
        HttpHandler.registerPostRequestHandler("/delete-role", AccessLevel.ADMIN, (rq) -> {
            Role role = Role.getByName(rq.getString("role"));

            if (role != null) {
                role.delete();
                return PostResponse.ok("Successfully deleted role", ContentType.TEXT_PLAIN, rq);
            } else {
                return PostResponse.badRequest("Role does not exist", rq);
            }
        });
        getLogger().info("Adding access listener...");
        AccessListener.getInstance().register();
        getLogger().info("permission-manager successfully loaded.");
    }

    private static boolean isFreistellung(String name) {
        return FREISTELLUNG_APPLY.equals(name) || FREISTELLUNG_PROCESSING.contains(name);
    }

    private static PostResponse toggleFreistellung(User user, Permission permission, de.igslandstuhl.database.server.webserver.requests.APIPostRequest rq) {
        PermissionNode node = PermissionNode.getPermissionNode(user.getUsername(), permission);
        boolean next = !node.isActive();
        Permission apply = Permission.getByName(FREISTELLUNG_APPLY);
        Permission orga = Permission.getByName("freistellung_orga");
        Permission sl = Permission.getByName("freistellung_sl");
        if (next && "freistellung_orga".equals(permission.getName()) && PermissionNode.getPermissionNode(user.getUsername(), sl).isActive())
            return PostResponse.badRequest("Orga-Leitung und Schulleitung schließen sich aus", rq);
        if (next && "freistellung_sl".equals(permission.getName()) && PermissionNode.getPermissionNode(user.getUsername(), orga).isActive())
            return PostResponse.badRequest("Orga-Leitung und Schulleitung schließen sich aus", rq);
        if (!next && FREISTELLUNG_APPLY.equals(permission.getName()) &&
            (PermissionNode.getPermissionNode(user.getUsername(), orga).isActive() || PermissionNode.getPermissionNode(user.getUsername(), sl).isActive()))
            return PostResponse.badRequest("Das Antragsrecht ist für die Bearbeitungsfunktion erforderlich", rq);
        node.setActive(next);
        UserEffect.registerAll();
        return PostResponse.ok("Freistellungsrecht gespeichert", ContentType.TEXT_PLAIN, rq);
    }

    private static boolean toggleRoleFreistellung(Role role, Permission permission) {
        boolean enabled = role.getPermissions().stream().anyMatch(p -> p.getName().equals(permission.getName()));
        if (!enabled && FREISTELLUNG_PROCESSING.contains(permission.getName())) {
            String other = "freistellung_orga".equals(permission.getName()) ? "freistellung_sl" : "freistellung_orga";
            if (role.getPermissions().stream().anyMatch(p -> p.getName().equals(other))) return false;
        }
        if (enabled && FREISTELLUNG_APPLY.equals(permission.getName()) && role.getPermissions().stream().anyMatch(p -> FREISTELLUNG_PROCESSING.contains(p.getName()))) return false;
        if (!enabled && FREISTELLUNG_PROCESSING.contains(permission.getName()) && role.getPermissions().stream().noneMatch(p -> p.getName().equals(FREISTELLUNG_APPLY))) {
            role.addPermission(Permission.getByName(FREISTELLUNG_APPLY));
        }
        return true;
    }
}
