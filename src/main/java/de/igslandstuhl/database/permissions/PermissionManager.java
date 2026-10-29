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
    private static final Object MUTATION_LOCK = UserEffect.mutationLock();

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
            synchronized (MUTATION_LOCK) {
            User user = User.getUser(rq.getString("user"));
            Permission permission = Permission.getByName(rq.getString("permission"));

            if (user != null && user != User.ANONYMOUS && permission != null) {
                if (isFreistellung(permission.getName())) {
                    return toggleFreistellung(user, permission, rq);
                }
                boolean next = !PermissionNode.readDirectActive(user.getUsername(), permission);
                if (next ? PermissionProjection.conflictAfter(user, Map.of(permission.getName(), next), null, null, null, null)
                    : PermissionProjection.wouldCreateConflict(user, Map.of(permission.getName(), next), null, null, null, null))
                    return PostResponse.badRequest("Orga-Leitung und Schulleitung schließen sich aus", rq);
                PermissionNode.getPermissionNode(user.getUsername(), permission).setActive(next);
                UserEffect.registerAll();
                return PostResponse.ok("Successfully toggled permission for user " + user.getUsername(), ContentType.TEXT_PLAIN, rq);
            } else {
                return PostResponse.badRequest("User or permission does not exist", rq);
            }
            }
        });
        HttpHandler.registerPostRequestHandler("/toggle-role", AccessLevel.ADMIN, (rq) -> {
            synchronized (MUTATION_LOCK) {
            User user = User.getUser(rq.getString("user"));
            Role role = Role.getByName(rq.getString("role"));

            if (user != null && user != User.ANONYMOUS && role != null) {
                boolean next = !RoleNode.readActive(user.getUsername(), role);
                if (next ? PermissionProjection.conflictAfter(user, Map.of(), role, next, null, null)
                    : PermissionProjection.wouldCreateConflict(user, Map.of(), role, next, null, null))
                    return PostResponse.badRequest("Orga-Leitung und Schulleitung schließen sich aus", rq);
                RoleNode.getRoleNode(user.getUsername(), role).setActive(next);
                UserEffect.registerAll();
                return PostResponse.ok("Successfully toggled role for user " + user.getUsername(), ContentType.TEXT_PLAIN, rq);
            } else {
                return PostResponse.badRequest("User or role does not exist", rq);
            }
            }
        });
        HttpHandler.registerPostRequestHandler("/toggle-role-permission", AccessLevel.ADMIN, (rq) -> {
            synchronized (MUTATION_LOCK) {
            Permission permission = Permission.getByName(rq.getString("permission"));
            Role role = Role.getByName(rq.getString("role"));

            if (permission != null && role != null) {
                if (isFreistellung(permission.getName()) && !toggleRoleFreistellung(role, permission)) {
                    return PostResponse.badRequest("Freistellungsrechte: Antrag, Orga-Leitung und Schulleitung müssen konsistent bleiben", rq);
                }
                boolean next = !role.getPermissions().contains(permission);
                for (User candidate : User.getAllUsers()) {
                    if (candidate == null || candidate == User.ANONYMOUS) continue;
                    User canonical = User.getUser(candidate.getUsername());
                    if (canonical != null && (next ? PermissionProjection.conflictAfter(canonical, Map.of(), role, null, permission, next)
                        : PermissionProjection.wouldCreateConflict(canonical, Map.of(), role, null, permission, next)))
                        return PostResponse.badRequest("Orga-Leitung und Schulleitung schließen sich aus", rq);
                }
                boolean addedApply = next && FREISTELLUNG_PROCESSING.contains(permission.getName())
                    && role.getPermissions().stream().noneMatch(p -> FREISTELLUNG_APPLY.equals(p.getName()));
                if (addedApply) role.addPermission(Permission.getByName(FREISTELLUNG_APPLY));
                role.togglePermission(permission);
                try {
                    RolesYamlConfigLoader.getInstance().saveRoles();
                } catch (RuntimeException e) {
                    role.togglePermission(permission);
                    if (addedApply) role.togglePermission(Permission.getByName(FREISTELLUNG_APPLY));
                    throw e;
                }
                UserEffect.registerAll();
                return PostResponse.ok("Successfully toggled permission for role " + role.getName(), ContentType.TEXT_PLAIN, rq);
            } else {
                return PostResponse.badRequest("Permission or role does not exist", rq);
            }
            }
        });
        HttpHandler.registerPostRequestHandler("/get-freistellung-rights", AccessLevel.ADMIN, (rq) -> {
            var users = User.getAllUsers().stream().filter(user -> user != User.ANONYMOUS).collect(java.util.stream.Collectors.toMap(User::getUsername, user -> user, (first, second) -> second.isTeacher() ? second : first)).values().stream().map(user -> {
                UserEffect effect = UserEffect.get(user);
                Set<String> effective = effect == null ? Set.of() : Arrays.stream(effect.getPermissions()).map(Permission::getName).collect(java.util.stream.Collectors.toSet());
                return Map.of(
                    "username", user.getUsername(),
                    "teacher", user.isTeacher(),
                    "admin", user.isAdmin(),
                    "apply", effective.contains(FREISTELLUNG_APPLY),
                    "orga", effective.contains("freistellung_orga"),
                    "sl", effective.contains("freistellung_sl"),
                    "securityConflict", effect != null && effect.hasFreistellungSecurityConflict()
                );
            }).toList();
            return PostResponse.json(users, rq);
        });
        HttpHandler.registerPostRequestHandler("/set-admin-role", AccessLevel.ADMIN, (rq) -> {
            synchronized (MUTATION_LOCK) {
            User user = User.getUser(rq.getString("user"));
            boolean enabled = rq.getBoolean("enabled");
            if (!(user instanceof Teacher teacher)) return PostResponse.badRequest("Nur Lehrkräfte können die Admin-Rolle erhalten", rq);
            Map<String, Boolean> defaults = new HashMap<>();
            for (Permission permission : Permission.getAll()) {
                if (PermissionManager.getInstance().permissionEffectRegistry().get(permission) != null
                    && PermissionNode.readOverride(teacher.getUsername(), permission) == null)
                    defaults.put(permission.getName(), PermissionNode.defaultActiveWithAdmin(teacher, permission, enabled));
            }
            if (PermissionProjection.wouldCreateConflict(teacher, defaults, null, null, null, null))
                return PostResponse.badRequest("Orga-Leitung und Schulleitung schließen sich aus", rq);
            if (enabled) {
                if (!teacher.isAdmin()) Admin.createWithPasswordHash(teacher.getUsername(), teacher.getPasswordHash());
            } else {
                Admin admin = Admin.get(teacher.getUsername());
                if (admin != null) admin.delete();
            }
            // The teacher object is the session identity for dual accounts. Rebuild its
            // effect immediately so the mode selected in the same running process sees the
            // changed admin role without a restart or re-login.
            UserEffect.registerAll();
            return PostResponse.ok("Organisationsrolle gespeichert", ContentType.TEXT_PLAIN, rq);
            }
        });
        HttpHandler.registerPostRequestHandler("/set-freistellung-right", AccessLevel.ADMIN, (rq) -> {
            synchronized (MUTATION_LOCK) {
            User user = User.getUser(rq.getString("user"));
            String right = rq.getString("right");
            if (user == null || user == User.ANONYMOUS || right == null || !Set.of("none", "apply", "orga", "sl").contains(right)) {
                return PostResponse.badRequest("Konto oder Freistellungsrecht ungültig", rq);
            }
            Map<String, Boolean> proposed = Map.of(
                FREISTELLUNG_APPLY, !right.equals("none"),
                "freistellung_orga", right.equals("orga"),
                "freistellung_sl", right.equals("sl"));
            boolean addsGrant = proposed.entrySet().stream().anyMatch(entry -> entry.getValue()
                && !PermissionNode.readDirectActive(user.getUsername(), Permission.getByName(entry.getKey())));
            if (addsGrant ? PermissionProjection.conflictAfter(user, proposed, null, null, null, null)
                : PermissionProjection.wouldCreateConflict(user, proposed, null, null, null, null))
                return PostResponse.badRequest("Orga-Leitung und Schulleitung schließen sich aus", rq);
            var sql = de.igslandstuhl.database.server.Server.getInstance().getConnection();
            sql.executeVoidProcessSecure(supplier -> {
                var connection = sql.getSQLConnection();
                boolean autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try (var statement = connection.prepareStatement(
                        "INSERT INTO permnodes(permission,username,active) VALUES(?,?,?) "
                        + "ON CONFLICT(permission,username) DO UPDATE SET active=excluded.active")) {
                    for (var entry : proposed.entrySet()) {
                        statement.setString(1, entry.getKey());
                        statement.setString(2, user.getUsername());
                        statement.setString(3, Boolean.toString(entry.getValue()));
                        statement.executeUpdate();
                    }
                    connection.commit();
                } catch (java.sql.SQLException | RuntimeException e) {
                    try { connection.rollback(); } catch (java.sql.SQLException rollback) { e.addSuppressed(rollback); }
                    throw e;
                } finally {
                    connection.setAutoCommit(autoCommit);
                }
            });
            PermissionNode.invalidateUsername(user.getUsername());
            UserEffect.registerAll();
            return PostResponse.ok("Freistellungsrecht gespeichert", ContentType.TEXT_PLAIN, rq);
            }
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
        HttpHandler.registerPostRequestHandler("/permission-override-audit", AccessLevel.ADMIN, (rq) -> {
            User user = User.getUser(rq.getString("user"));
            Permission permission = Permission.getByName(rq.getString("permission"));
            if (user == null || user == User.ANONYMOUS || permission == null
                || permissionEffectRegistry.get(permission) == null)
                return PostResponse.badRequest("Benutzer oder Berechtigung unbekannt", rq);
            return PostResponse.json(PermissionAudit.report(user, permission), rq);
        });
        HttpHandler.registerPostRequestHandler("/set-permission-override", AccessLevel.ADMIN, (rq) -> {
            synchronized (MUTATION_LOCK) {
                User user = User.getUser(rq.getString("user"));
                Permission permission = Permission.getByName(rq.getString("permission"));
                String state = rq.getString("state");
                if (user == null || user == User.ANONYMOUS || permission == null
                    || permissionEffectRegistry.get(permission) == null || state == null
                    || !Set.of("inherit", "allow", "deny").contains(state))
                    return PostResponse.badRequest("Ungültiger Benutzer, Berechtigung oder Zustand", rq);
                boolean proposed = state.equals("inherit") ? PermissionNode.defaultActive(user.getUsername(), permission) : state.equals("allow");
                boolean addsGrant = proposed && !PermissionNode.readDirectActive(user.getUsername(), permission);
                if (addsGrant ? PermissionProjection.conflictAfter(user, Map.of(permission.getName(), proposed), null, null, null, null)
                    : PermissionProjection.wouldCreateConflict(user, Map.of(permission.getName(), proposed), null, null, null, null))
                    return PostResponse.badRequest("Orga-Leitung und Schulleitung schließen sich aus", rq);
                PermissionNode node = PermissionNode.getPermissionNode(user.getUsername(), permission);
                if (state.equals("inherit")) {
                    if (node.hasOverride()) node.resetToInherited();
                } else {
                    node.setActive(proposed);
                }
                UserEffect.registerAll();
                return PostResponse.ok("Berechtigungszustand gespeichert", ContentType.TEXT_PLAIN, rq);
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
            synchronized (MUTATION_LOCK) {
            String name = rq.getString("name");
            String description = rq.getString("description");

            if (name != null && description != null) {
                boolean created = Role.getByName(name) == null;
                Role role = Role.getByNameOrCreate(name, description);
                try { RolesYamlConfigLoader.getInstance().saveRoles(); }
                catch (RuntimeException e) { if (created) role.delete(); throw e; }
                UserEffect.registerAll();
                return PostResponse.redirect("/manage_permissions", rq);
            } else {
                return PostResponse.badRequest("Either name or description are not present.", rq);
            }
            }
        });
        HttpHandler.registerPostRequestHandler("/delete-role", AccessLevel.ADMIN, (rq) -> {
            synchronized (MUTATION_LOCK) {
            Role role = Role.getByName(rq.getString("role"));

            if (role != null) {
                role.delete();
                try { RolesYamlConfigLoader.getInstance().saveRoles(); }
                catch (RuntimeException e) { role.register(); throw e; }
                UserEffect.registerAll();
                return PostResponse.ok("Successfully deleted role", ContentType.TEXT_PLAIN, rq);
            } else {
                return PostResponse.badRequest("Role does not exist", rq);
            }
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
        boolean next = !PermissionNode.readDirectActive(user.getUsername(), permission);
        if (next ? PermissionProjection.conflictAfter(user, Map.of(permission.getName(), next), null, null, null, null)
            : PermissionProjection.wouldCreateConflict(user, Map.of(permission.getName(), next), null, null, null, null))
            return PostResponse.badRequest("Orga-Leitung und Schulleitung schließen sich aus", rq);
        Permission apply = Permission.getByName(FREISTELLUNG_APPLY);
        Permission orga = Permission.getByName("freistellung_orga");
        Permission sl = Permission.getByName("freistellung_sl");
        if (next && "freistellung_orga".equals(permission.getName()) && PermissionNode.readDirectActive(user.getUsername(), sl))
            return PostResponse.badRequest("Orga-Leitung und Schulleitung schließen sich aus", rq);
        if (next && "freistellung_sl".equals(permission.getName()) && PermissionNode.readDirectActive(user.getUsername(), orga))
            return PostResponse.badRequest("Orga-Leitung und Schulleitung schließen sich aus", rq);
        if (!next && FREISTELLUNG_APPLY.equals(permission.getName()) &&
            (PermissionNode.readDirectActive(user.getUsername(), orga) || PermissionNode.readDirectActive(user.getUsername(), sl)))
            return PostResponse.badRequest("Das Antragsrecht ist für die Bearbeitungsfunktion erforderlich", rq);
        PermissionNode.getPermissionNode(user.getUsername(), permission).setActive(next);
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
        return true;
    }
}
