package de.igslandstuhl.database.permissions.generics;

import java.util.List;

import de.igslandstuhl.database.api.Subject;
import de.igslandstuhl.database.permissions.PermissionManager;

public class SubjectGeneric extends PermissionGeneric {
    private static final SubjectGeneric instance = new SubjectGeneric();

    public static SubjectGeneric getInstance() {
        return instance;
    }

    private SubjectGeneric() {
        super("$subject");
    }

    @Override
    public List<String> getAllReplacements() {
        return Subject.getAll().stream().map((s) -> s.getName()).map(PermissionManager::convertName).toList();
    }
    public Subject getSubject(String name) {
        return Subject.getAll().stream()
        .filter((c) -> PermissionManager.convertName(c.getName()).equals(name))
        .findAny().orElse(null);
    }
}
