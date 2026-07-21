package de.igslandstuhl.database.permissions;

import de.igslandstuhl.database.permissions.restrictions.PostRestriction;
import de.igslandstuhl.database.server.webserver.access.AccessLevel;

public record PermissionEffect(Permission permission, String[] allowedPaths, PostRestriction[] postRestrictions, Permission[] depends, AccessLevel defaultLevel) {
    public void register() {
        PermissionManager.getInstance().permissionEffectRegistry().register(permission, this);
    }
}
