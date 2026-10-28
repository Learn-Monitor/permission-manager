package de.igslandstuhl.database.permissions;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.server.webserver.access.AccessLevel;
import de.igslandstuhl.database.server.webserver.access.AccessState;
import de.igslandstuhl.database.server.webserver.requests.HttpRequest;

public class UserEffect {
    private static final Registry<User, UserEffect> registry = new Registry<>();

    private final User user;
    
    private final PermissionEffect[] effects;

    public UserEffect(User user, Permission[] permissions) {
        this.user = user;
        // Resolve only opt-in prerequisites. Legacy "depends" metadata keeps its behavior.
        Set<PermissionEffect> pending = new LinkedHashSet<>();
        Arrays.stream(permissions)
            .map(PermissionManager.getInstance().permissionEffectRegistry()::get)
            .filter(Objects::nonNull)
            .forEach(pending::add);
        Set<Permission> resolved = new LinkedHashSet<>();
        boolean changed;
        do {
            changed = false;
            for (PermissionEffect effect : pending) {
                if (!resolved.contains(effect.permission()) &&
                    (!effect.requireDependencies() ||
                     Arrays.stream(effect.depends()).allMatch(resolved::contains))) {
                    changed |= resolved.add(effect.permission());
                }
            }
        } while (changed);
        // Missing prerequisites and cycles fail closed, without creating or activating nodes.
        effects = pending.stream().filter(e -> resolved.contains(e.permission()))
            .toArray(PermissionEffect[]::new);
    }

    public AccessState testAccess(String path, HttpRequest request) {
        boolean access = Arrays.stream(effects)
        .filter((e) -> Arrays.stream(e.allowedPaths()).anyMatch(path::equals))
        .anyMatch((e) -> e.testRequest(request));

        if (user == null || user == User.ANONYMOUS) {
            return access ? AccessState.PERMITTED : AccessState.UNAUTHORIZED;
        } else {
            return access ? AccessState.AUTHORIZED : AccessState.RESTRICTED;
        }
    }
    public Permission[] getPermissions() {
        return Arrays.stream(effects)
            .map(PermissionEffect::permission)
            .toArray(Permission[]::new);
    }

    public void register() {
        registry.register(user, this);
    }

    public static void registerAll() {
        PermissionNode.loadSnapshot();
        // Permission metadata is immutable for the duration of this rebuild. Reusing the
        // snapshot avoids rescanning the registry once per user and preserves lookup order.
        Permission[] allPermissions = Permission.getAll().toArray(new Permission[0]);
        User.getAllUsers().forEach((u) -> {
            registerUser(u, allPermissions);
        });
    }

    /** Builds one effect from the same persisted permission and role sources as registerAll(). */
    private static void registerUser(User user, Permission[] allPermissions) {
        Permission[] activePermissions;
        if (user == User.ANONYMOUS) {
            activePermissions = Arrays.stream(allPermissions)
                .map(PermissionManager.getInstance().permissionEffectRegistry()::get)
                .filter(Objects::nonNull)
                .filter((e) -> e.defaultLevel() == AccessLevel.PUBLIC)
                .map((e) -> e.permission())
                .toArray((a) -> new Permission[a]);
        } else {
            Set<Permission> permissions = new LinkedHashSet<>(
                Arrays.stream(allPermissions)
                    .filter(p -> PermissionManager.getInstance().permissionEffectRegistry().get(p) != null)
                    .filter((p) -> PermissionNode.getPermissionNode(user.getUsername(), p).isActive())
                    .toList()
            );
            RoleNode.getUserRoles(user.getUsername()).stream()
                .filter((r) -> RoleNode.getRoleNode(user.getUsername(), r).isActive())
                .flatMap((r) -> r.getPermissions().stream())
                .forEach(permissions::add);
            activePermissions = permissions.toArray(new Permission[0]);
        }
        new UserEffect(user, activePermissions).register();
    }

    public static UserEffect get(User user) {
        if (user == null) user = User.ANONYMOUS;
        UserEffect effect = registry.get(user);
        if (effect == null && user != User.ANONYMOUS) {
            // Users created after plugin startup are not part of the initial rebuild.
            // Materialize only this authenticated user from the canonical persisted sources;
            // an absent/unknown user still resolves to ANONYMOUS and remains fail-closed.
            User persistedUser = User.getUser(user.getUsername());
            if (persistedUser == null) return null;
            PermissionNode.loadSnapshot();
            registerUser(persistedUser, Permission.getAll().toArray(new Permission[0]));
            effect = registry.get(persistedUser);
        }
        return effect;
    }

    static void invalidateUsername(String username) {
        registry.keyStream().filter(user -> username.equals(user.getUsername())).toList().forEach(registry::unregister);
    }
}
