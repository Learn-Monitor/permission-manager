package de.igslandstuhl.database.permissions.meta;

import java.util.List;

import de.igslandstuhl.database.permissions.PermissionManager;
import de.igslandstuhl.database.plugins.config.PluginConfig;

public class PermissionManagerConfig extends PluginConfig<PermissionManager> {
    public PermissionManagerConfig(PermissionManager plugin) {
        super(plugin, List.of());
    }
}
