package de.igslandstuhl.database.permissions.generics;

import java.util.List;

import de.igslandstuhl.database.api.SchoolClass;
import de.igslandstuhl.database.permissions.PermissionManager;

public class SchoolClassGeneric extends PermissionGeneric {
    private static final SchoolClassGeneric instance = new SchoolClassGeneric();
    
    public static SchoolClassGeneric getInstance() {
        return instance;
    }

    private SchoolClassGeneric() {
        super("$schoolClass");
    }

    @Override
    public List<String> getAllReplacements() {
        return SchoolClass.getAll().stream().map((c) -> c.getLabel()).map(PermissionManager::convertName).toList();
    }
    public SchoolClass getSchoolClass(String name) {
        return SchoolClass.getAll().stream()
        .filter((c) -> PermissionManager.convertName(c.getLabel()).equals(name))
        .findAny().orElse(null);
    }
}
