package de.igslandstuhl.database.permissions;

import java.util.Arrays;
import java.util.Objects;

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
        effects = Arrays.stream(permissions)
        .map(PermissionManager.getInstance().permissionEffectRegistry()::get)
        .filter(Objects::nonNull)
        .toArray((a) -> new PermissionEffect[a]);
    }

    public AccessState testAccess(String path, HttpRequest request) {
        boolean access = Arrays.stream(effects)
        .filter((e) -> Arrays.stream(e.allowedPaths()).anyMatch(path::equals))
        .anyMatch((e) -> e.testPostRestrictions(request));

        if (user == null || user == User.ANONYMOUS) {
            return access ? AccessState.PERMITTED : AccessState.UNAUTHORIZED;
        } else {
            return access ? AccessState.AUTHORIZED : AccessState.RESTRICTED;
        }
    }
    public void register() {
        registry.register(user, this);
    }

    public static void registerAll() {
        User.getAllUsers().forEach((u) -> {
            Permission[] activePermissions;
            if (u == User.ANONYMOUS) {
                activePermissions = Permission.getAll().stream()
                .map(PermissionManager.getInstance().permissionEffectRegistry()::get)
                .filter(Objects::nonNull)
                .filter((e) -> e.defaultLevel() == AccessLevel.PUBLIC)
                .map((e) -> e.permission())
                .toArray((a) -> new Permission[a]);
            } else{
                activePermissions = Permission.getAll().stream()
                .filter((p) -> PermissionNode.getPermissionNode(u.getUsername(), p).isActive())
                .toArray((a) -> new Permission[a]);
            }
            new UserEffect(u, activePermissions).register();
        });
    }
    public static UserEffect get(User user) {
        if (user == null) user = User.ANONYMOUS;
        return registry.get(user);
    }
}
