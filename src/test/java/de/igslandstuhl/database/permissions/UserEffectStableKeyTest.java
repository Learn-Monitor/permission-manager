package de.igslandstuhl.database.permissions;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import de.igslandstuhl.database.api.User;
import org.junit.jupiter.api.Test;

class UserEffectStableKeyTest {
    @Test
    void lookupSurvivesMutationOfUsersHashCode() {
        MutableHashUser user = new MutableHashUser("mutable-hash-user@example.invalid");
        PermissionManager manager = mock(PermissionManager.class);

        try (var managerStatic = mockStatic(PermissionManager.class)) {
            managerStatic.when(PermissionManager::getInstance).thenReturn(manager);
            when(manager.permissionEffectRegistry()).thenReturn(new de.igslandstuhl.database.Registry<>());

            UserEffect effect = new UserEffect(user, new Permission[0]);
            effect.register();
            assertSame(effect, UserEffect.get(user));

            user.setHashCode(987654321);

            assertSame(effect, UserEffect.get(user));
            UserEffect.invalidateUsername(user.getUsername());
            assertSame(null, UserEffect.get(user));
        }
    }

    private static final class MutableHashUser extends User {
        private final String username;
        private int hashCode;

        private MutableHashUser(String username) {
            this.username = username;
        }

        private void setHashCode(int hashCode) {
            this.hashCode = hashCode;
        }

        @Override public boolean isTeacher() { return false; }
        @Override public boolean isStudent() { return true; }
        @Override public boolean isAdmin() { return false; }
        @Override public String getPasswordHash() { return "hash"; }
        @Override public String toJSON() { return "{}"; }
        @Override public User setPassword(String password) { return this; }
        @Override public String getUsername() { return username; }
        @Override public int hashCode() { return hashCode; }
    }
}
