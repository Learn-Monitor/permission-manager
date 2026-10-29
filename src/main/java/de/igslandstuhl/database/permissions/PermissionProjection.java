package de.igslandstuhl.database.permissions;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import de.igslandstuhl.database.api.User;

/** Calculates a proposed permission set without persisting a proposed mutation. */
final class PermissionProjection {
    private PermissionProjection() { }

    static UserEffect project(User user, Map<String, Boolean> direct,
            Role changedRole, Boolean roleAssigned, Permission changedRolePermission,
            Boolean rolePermissionEnabled) {
        if (user == null || user == User.ANONYMOUS) return new UserEffect(User.ANONYMOUS, new Permission[0]);
        Set<Permission> relevant = new LinkedHashSet<>();
        addWithDependencies(relevant, Permission.getByName("freistellung_orga"));
        addWithDependencies(relevant, Permission.getByName("freistellung_sl"));
        Set<Permission> granted = new LinkedHashSet<>();
        for (Permission permission : relevant) {
            if (PermissionManager.getInstance().permissionEffectRegistry().get(permission) == null) continue;
            Boolean proposed = direct.get(permission.getName());
            Boolean stored = proposed == null ? PermissionNode.readOverride(user.getUsername(), permission) : null;
            boolean active = proposed != null ? proposed : stored != null ? stored : PermissionNode.defaultActive(user, permission);
            if (active) granted.add(permission);
        }
        for (Role role : Role.getAll()) {
            boolean assigned = role == changedRole && roleAssigned != null
                ? roleAssigned : RoleNode.readActive(user.getUsername(), role);
            if (!assigned) continue;
            Set<Permission> roleGrants = new LinkedHashSet<>(role.getPermissions());
            if (role == changedRole && changedRolePermission != null && rolePermissionEnabled != null) {
                if (rolePermissionEnabled) {
                    roleGrants.add(changedRolePermission);
                    String name = changedRolePermission.getName();
                    if ("freistellung_orga".equals(name) || "freistellung_sl".equals(name)) {
                        Permission apply = Permission.getByName("freistellung_apply");
                        if (apply != null) roleGrants.add(apply);
                    }
                } else {
                    roleGrants.remove(changedRolePermission);
                }
            }
            roleGrants.retainAll(relevant);
            granted.addAll(roleGrants);
        }
        return new UserEffect(user, granted.toArray(Permission[]::new));
    }

    private static void addWithDependencies(Set<Permission> relevant, Permission permission) {
        if (permission == null) throw new IllegalStateException("Missing processing permission definition");
        if (!relevant.add(permission)) return;
        PermissionEffect effect = PermissionManager.getInstance().permissionEffectRegistry().get(permission);
        if (effect == null) throw new IllegalStateException("Missing processing permission effect");
        if (effect.requireDependencies()) {
            for (Permission dependency : effect.depends()) addWithDependencies(relevant, dependency);
        }
    }

    static boolean wouldCreateConflict(User user, Map<String, Boolean> direct,
            Role role, Boolean assigned, Permission rolePermission, Boolean enabled) {
        UserEffect before = project(user, Map.of(), null, null, null, null);
        UserEffect after = project(user, direct, role, assigned, rolePermission, enabled);
        return !before.hasFreistellungSecurityConflict() && after.hasFreistellungSecurityConflict();
    }

    static boolean conflictAfter(User user, Map<String, Boolean> direct,
            Role role, Boolean assigned, Permission rolePermission, Boolean enabled) {
        return project(user, direct, role, assigned, rolePermission, enabled).hasFreistellungSecurityConflict();
    }
}
