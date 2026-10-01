package de.igslandstuhl.database.permissions;

import de.igslandstuhl.database.events.EventListener;
import de.igslandstuhl.database.events.EventType;
import de.igslandstuhl.database.events.ListenerPriority;
import de.igslandstuhl.database.events.UserAccountDeletedEvent;

/** Removes only the deleted login's in-memory authorization state. */
final class UserAccountDeletedListener extends EventListener<UserAccountDeletedEvent> {
    private static final UserAccountDeletedListener INSTANCE = new UserAccountDeletedListener();

    private UserAccountDeletedListener() { super(ListenerPriority.LOW); }

    static UserAccountDeletedListener getInstance() { return INSTANCE; }

    @Override public EventType<UserAccountDeletedEvent> getEventType() { return UserAccountDeletedEvent.TYPE; }

    @Override public void onEvent(UserAccountDeletedEvent event) {
        String username = event.getUsername();
        PermissionNode.invalidateUsername(username);
        RoleNode.invalidateUsername(username);
        UserEffect.invalidateUsername(username);
    }
}
