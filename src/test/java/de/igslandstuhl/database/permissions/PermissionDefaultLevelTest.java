package de.igslandstuhl.database.permissions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.server.webserver.access.AccessLevel;

class PermissionDefaultLevelTest {
    @Test
    void studentAndUserDefaultsRemainDistinct() {
        User student = user(true, false, false);
        User teacher = user(false, true, false);
        User admin = user(false, false, true);

        assertTrue(PermissionNode.isDefaultActive(AccessLevel.STUDENT, student));
        assertFalse(PermissionNode.isDefaultActive(AccessLevel.STUDENT, teacher));
        assertFalse(PermissionNode.isDefaultActive(AccessLevel.STUDENT, admin));

        assertTrue(PermissionNode.isDefaultActive(AccessLevel.USER, student));
        assertTrue(PermissionNode.isDefaultActive(AccessLevel.USER, teacher));
        assertTrue(PermissionNode.isDefaultActive(AccessLevel.USER, admin));
    }

    @Test
    void teacherAndAdminDefaultsFollowRoleHierarchy() {
        User student = user(true, false, false);
        User teacher = user(false, true, false);
        User admin = user(false, false, true);

        assertFalse(PermissionNode.isDefaultActive(AccessLevel.TEACHER, student));
        assertTrue(PermissionNode.isDefaultActive(AccessLevel.TEACHER, teacher));
        assertTrue(PermissionNode.isDefaultActive(AccessLevel.TEACHER, admin));

        assertFalse(PermissionNode.isDefaultActive(AccessLevel.ADMIN, student));
        assertFalse(PermissionNode.isDefaultActive(AccessLevel.ADMIN, teacher));
        assertTrue(PermissionNode.isDefaultActive(AccessLevel.ADMIN, admin));
    }

    private static User user(boolean student, boolean teacher, boolean admin) {
        User user = mock(User.class);
        when(user.isStudent()).thenReturn(student);
        when(user.isTeacher()).thenReturn(teacher);
        when(user.isAdmin()).thenReturn(admin);
        return user;
    }
}
