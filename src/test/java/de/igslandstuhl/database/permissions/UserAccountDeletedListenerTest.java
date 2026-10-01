package de.igslandstuhl.database.permissions;

import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.events.UserAccountDeletedEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAccountDeletedListenerTest {
    @SuppressWarnings("unchecked")
    @Test void deletedLoginInvalidatesOnlyItsPermissionRoleAndUserEffectCaches() throws Exception {
        String deleted = "deleted-a2@example.invalid";
        String other = "other-a2@example.invalid";
        Permission permission = new Permission("a2-cache-test", "cache test");
        Role role = new Role("a2-cache-test", "cache test");

        addCacheEntry(PermissionNode.class, "cache", "index", "CacheKey", deleted, "a2-cache-test",
            new PermissionNode(permission, deleted, true));
        addCacheEntry(PermissionNode.class, "cache", "index", "CacheKey", other, "a2-cache-test",
            new PermissionNode(permission, other, true));
        addCacheEntry(RoleNode.class, "cache", "index", "CacheKey", deleted, "a2-cache-test",
            new RoleNode(role, deleted, true));
        addCacheEntry(RoleNode.class, "cache", "index", "CacheKey", other, "a2-cache-test",
            new RoleNode(role, other, true));

        User deletedUser = mock(User.class);
        User otherUser = mock(User.class);
        when(deletedUser.getUsername()).thenReturn(deleted);
        when(otherUser.getUsername()).thenReturn(other);
        new UserEffect(deletedUser, new Permission[0]).register();
        new UserEffect(otherUser, new Permission[0]).register();

        UserAccountDeletedListener.getInstance().onEvent(new UserAccountDeletedEvent(deleted));

        assertNull(UserEffect.get(deletedUser));
        assertNotNull(UserEffect.get(otherUser));
        assertEquals(List.of(other), usernames(PermissionNode.class));
        assertEquals(List.of(other), usernames(RoleNode.class));
        assertNoCachedIndexEntry(PermissionNode.class, deleted);
        assertNoCachedIndexEntry(RoleNode.class, deleted);
        UserAccountDeletedListener.getInstance().onEvent(new UserAccountDeletedEvent(other));
    }

    @SuppressWarnings("unchecked")
    private static void assertNoCachedIndexEntry(Class<?> nodeType, String username) throws Exception {
        Field index = nodeType.getDeclaredField("index"); index.setAccessible(true);
        Map<Object, Object> values = (Map<Object, Object>) index.get(null);
        assertTrue(values.keySet().stream().noneMatch(key -> key.toString().contains(username)));
    }

    @SuppressWarnings("unchecked")
    private static void addCacheEntry(Class<?> nodeType, String listField, String mapField, String keyClass,
                                      String username, String name, Object value) throws Exception {
        Field list = nodeType.getDeclaredField(listField); list.setAccessible(true);
        ((List<Object>) list.get(null)).add(value);
        Field index = nodeType.getDeclaredField(mapField); index.setAccessible(true);
        Class<?> keyType = List.of(nodeType.getDeclaredClasses()).stream()
            .filter(type -> type.getSimpleName().equals(keyClass)).findFirst().orElseThrow();
        Constructor<?> constructor = keyType.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        Object key = constructor.newInstance(username, name);
        ((Map<Object, Object>) index.get(null)).put(key, value);
    }

    private static List<String> usernames(Class<?> nodeType) throws Exception {
        Field list = nodeType.getDeclaredField("cache"); list.setAccessible(true);
        @SuppressWarnings("unchecked") List<Object> nodes = (List<Object>) list.get(null);
        return nodes.stream().map(node -> {
            try {
                return String.valueOf(nodeType.getMethod("getUsername").invoke(node));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }).filter(name -> name.endsWith("-a2@example.invalid")).sorted().toList();
    }
}
