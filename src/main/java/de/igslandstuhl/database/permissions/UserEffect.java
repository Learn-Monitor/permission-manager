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
    private static final Object MUTATION_LOCK = new Object();
    static Object mutationLock() { return MUTATION_LOCK; }

    private final User user;
    
    private final PermissionEffect[] effects;
    private final boolean freistellungSecurityConflict;

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
        // A historical or otherwise inconsistent union must never authorize
        // either processing function. Keep the independent application right.
        freistellungSecurityConflict = resolved.stream().anyMatch(p -> "freistellung_orga".equals(p.getName()))
            && resolved.stream().anyMatch(p -> "freistellung_sl".equals(p.getName()));
        if (freistellungSecurityConflict) {
            pending.removeIf(e -> "freistellung_orga".equals(e.permission().getName())
                || "freistellung_sl".equals(e.permission().getName()));
            resolved.clear();
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
        }
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

    public boolean hasFreistellungSecurityConflict() { return freistellungSecurityConflict; }

    public void register() {
        synchronized (MUTATION_LOCK) {
            synchronized (UserEffect.class) { registry.register(user, this); }
        }
    }

    public static void registerAll() {
        synchronized (MUTATION_LOCK) {
            synchronized (UserEffect.class) {
                try {
                    PermissionNode.loadSnapshot();
                    Permission[] allPermissions = Permission.getAll().toArray(new Permission[0]);
                    User.getAllUsers().forEach((u) -> registerUser(u, allPermissions));
                } catch (RuntimeException failure) {
                    // Do not retain any old effect if a revocation rebuild fails.
                    registry.close();
                    throw failure;
                }
            }
        }
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
                    .filter((p) -> PermissionNode.getPermissionNode(user.getUsername(), p).isActive(user))
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
        synchronized (MUTATION_LOCK) {
            synchronized (UserEffect.class) {
                try {
                    if (user == null) user = User.ANONYMOUS;
                    if (user == User.ANONYMOUS) return registry.get(user);
                    User persistedUser = User.getUser(user.getUsername());
                    if (persistedUser == null || persistedUser == User.ANONYMOUS) return null;
                    UserEffect effect = registry.get(persistedUser);
                    if (effect == null) {
                        PermissionNode.loadSnapshot();
                        registerUser(persistedUser, Permission.getAll().toArray(new Permission[0]));
                        effect = registry.get(persistedUser);
                    }
                    return effect;
                } catch (RuntimeException failure) {
                    org.slf4j.LoggerFactory.getLogger(UserEffect.class).error("Permission effect lookup failed; access denied", failure);
                    return null;
                }
            }
        }
    }

    static void invalidateUsername(String username) {
        synchronized (MUTATION_LOCK) {
            synchronized (UserEffect.class) {
                registry.keyStream().filter(user -> username.equals(user.getUsername())).toList().forEach(registry::unregister);
            }
        }
    }
}
