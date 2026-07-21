package de.igslandstuhl.database.permissions;

import java.util.Arrays;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.api.User;
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
}
