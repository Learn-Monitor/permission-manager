package de.igslandstuhl.database.permissions.meta;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;

import de.igslandstuhl.database.permissions.Permission;
import de.igslandstuhl.database.permissions.PermissionManager;
import de.igslandstuhl.database.permissions.Role;
import de.igslandstuhl.database.server.resources.ResourceLocation;
import de.igslandstuhl.database.server.Server;

public class RolesYamlConfigLoader {
    private static RolesYamlConfigLoader instance = new RolesYamlConfigLoader();
    private static final String ROLES_CONFIG_DIR = "config/permission-manager";
    private static final String ROLES_CONFIG_FILE = "roles.yaml";
    private static final ResourceLocation DEFAULT_ROLES_RESOURCE = new ResourceLocation("meta", "permission-manager", "default-roles.yaml");

    public static RolesYamlConfigLoader getInstance() {
        return instance;
    }

    private Path getConfigPath() {
        return Paths.get(ROLES_CONFIG_DIR, ROLES_CONFIG_FILE);
    }

    private void ensureConfigFileExists() throws IOException {
        Path configPath = getConfigPath();
        Path configDir = configPath.getParent();

        // Create directories if they don't exist
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir);
        }

        // Create default config file if it doesn't exist
        if (!Files.exists(configPath)) {
            createDefaultConfigFile(configPath);
        }
    }

    private void createDefaultConfigFile(Path configPath) throws IOException {
        try {
            String defaultConfig = Server.getInstance().getResourceManager().readResourceCompletely(DEFAULT_ROLES_RESOURCE);
            try (FileWriter writer = new FileWriter(configPath.toFile())) {
                writer.write(defaultConfig);
            }
        } catch (Exception e) {
            PermissionManager.getInstance().getLogger().warn("Could not load default roles from resource, creating empty config", e);
            try (FileWriter writer = new FileWriter(configPath.toFile())) {
                writer.write("# Permission Manager Roles Configuration\n# Define roles and their associated permissions here\n\nroles: []\n");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Role getRole(Map<String, ?> roleMap) {
        String name = (String) roleMap.get("name");
        String description = (String) roleMap.get("description");
        List<String> permissionNames = (List<String>) roleMap.get("permissions");

        if (name == null || name.isBlank()) {
            PermissionManager.getInstance().getLogger().warn("Role configuration missing required 'name' field");
            return null;
        }

        Role role = Role.getByNameOrCreate(name, description != null ? description : "");

        if (permissionNames != null) {
            Permission[] permissions = permissionNames.stream()
                .map(Permission::getByName)
                .filter(p -> p != null)
                .toArray(Permission[]::new);
            role.addPermissions(permissions);
        }

        return role;
    }

    @SuppressWarnings("unchecked")
    public void registerAllRoles() {
        try {
            ensureConfigFileExists();

            Yaml yaml = new Yaml();
            Path configPath = getConfigPath();

            try (FileInputStream fis = new FileInputStream(configPath.toFile())) {
                Map<String, ?> rolesMap = yaml.load(fis);

                if (rolesMap != null) {
                    List<Map<String, ?>> rolesList = (List<Map<String, ?>>) rolesMap.get("roles");

                    if (rolesList != null && !rolesList.isEmpty()) {
                        PermissionManager.getInstance().getLogger().info("Loading {} role(s) from {}", rolesList.size(), configPath);
                        rolesList.stream()
                            .map(this::getRole)
                            .filter(role -> role != null)
                            .forEach(role -> {
                                PermissionManager.getInstance().getLogger().info("Loaded role: {}", role.getName());
                            });
                    } else {
                        PermissionManager.getInstance().getLogger().warn("No roles defined in configuration file: {}", configPath);
                    }
                }
            }
        } catch (IOException e) {
            PermissionManager.getInstance().getLogger().error("Failed to load roles configuration from YAML", e);
        }
    }

    public void saveRoles() {
        try {
            Path configPath = getConfigPath();
            Path configDir = configPath.getParent();

            // Ensure directory exists
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            // Create YAML representation of all roles
            List<Map<String, Object>> rolesList = Role.getAll().stream()
                .map(role -> {
                    Map<String, Object> roleMap = new LinkedHashMap<>();
                    roleMap.put("name", role.getName());
                    roleMap.put("description", role.getDescription());
                    roleMap.put("permissions", role.getPermissions().stream()
                        .map(Permission::getName)
                        .collect(Collectors.toList()));
                    return roleMap;
                })
                .collect(Collectors.toList());

            Map<String, Object> config = new LinkedHashMap<>();
            config.put("roles", rolesList);

            // Write to file
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            Yaml yaml = new Yaml(options);

            try (FileWriter writer = new FileWriter(configPath.toFile())) {
                yaml.dump(config, writer);
            }

            PermissionManager.getInstance().getLogger().info("Saved {} role(s) to {}", rolesList.size(), configPath);
        } catch (IOException e) {
            PermissionManager.getInstance().getLogger().error("Failed to save roles configuration to YAML", e);
        }
    }

    public Path getConfigFilePath() {
        return getConfigPath();
    }
}
