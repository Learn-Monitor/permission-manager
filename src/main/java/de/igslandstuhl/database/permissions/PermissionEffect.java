package de.igslandstuhl.database.permissions;

import java.util.Arrays;

import de.igslandstuhl.database.permissions.restrictions.PostRestriction;
import de.igslandstuhl.database.server.webserver.access.AccessLevel;
import de.igslandstuhl.database.server.webserver.requests.HttpRequest;
import de.igslandstuhl.database.server.webserver.requests.PostRequest;

public record PermissionEffect(Permission permission, String[] allowedPaths, PostRestriction[] postRestrictions, Permission[] depends, AccessLevel defaultLevel) {
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
