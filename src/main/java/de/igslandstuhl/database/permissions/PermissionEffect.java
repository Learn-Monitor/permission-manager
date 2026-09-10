package de.igslandstuhl.database.permissions;

import java.util.Arrays;
import de.igslandstuhl.database.api.User;

import de.igslandstuhl.database.permissions.restrictions.PostRestriction;
import de.igslandstuhl.database.server.webserver.access.AccessLevel;
import de.igslandstuhl.database.server.webserver.requests.HttpRequest;
import de.igslandstuhl.database.server.webserver.requests.PostRequest;
import de.igslandstuhl.database.server.webserver.requests.RequestType;

public record PermissionEffect(Permission permission, String[] allowedPaths, PostRestriction[] postRestrictions, Permission[] depends, AccessLevel defaultLevel, boolean requireDependencies, RequestType[] allowedMethods, boolean exactDefault) {
    // Absent method policy preserves existing path-only rules and callers.
    public PermissionEffect(Permission permission, String[] allowedPaths, PostRestriction[] postRestrictions,
                            Permission[] depends, AccessLevel defaultLevel, boolean requireDependencies) {
        this(permission, allowedPaths, postRestrictions, depends, defaultLevel, requireDependencies, null, false);
    }

    public PermissionEffect(Permission permission, String[] allowedPaths, PostRestriction[] postRestrictions,
                            Permission[] depends, AccessLevel defaultLevel, boolean requireDependencies, RequestType[] allowedMethods) {
        this(permission, allowedPaths, postRestrictions, depends, defaultLevel, requireDependencies, allowedMethods, false);
    }

    /** Restrict only newly created default nodes; existing explicit/role grants retain their semantics. */
    public boolean isDefaultEligible(User user) {
        if (!exactDefault) return true;
        if (user == null || user == User.ANONYMOUS) return false;
        return switch (defaultLevel) {
            case STUDENT -> user.isStudent();
            case TEACHER -> user.isTeacher();
            case ADMIN -> user.isAdmin();
            default -> false;
        };
    }

    public boolean testRequest(HttpRequest request) {
        return (allowedMethods == null || request != null && Arrays.asList(allowedMethods).contains(request.getRequestType()))
            && testPostRestrictions(request);
    }

    // Preserve legacy callers: dependency enforcement is explicitly opt-in.
    public PermissionEffect(Permission permission, String[] allowedPaths, PostRestriction[] postRestrictions,
                            Permission[] depends, AccessLevel defaultLevel) {
        this(permission, allowedPaths, postRestrictions, depends, defaultLevel, false);
    }

    public void register() {
        PermissionManager.getInstance().permissionEffectRegistry().register(permission, this);
    }
    public boolean testPostRestrictions(HttpRequest request) {
        if (request instanceof PostRequest postRequest) {
            return Arrays.stream(postRestrictions).allMatch((r) -> r.isPostAllowed(postRequest));
        } else {
            return true;
        }
    }
}
