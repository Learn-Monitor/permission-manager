package de.igslandstuhl.database.permissions;

import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.events.EventListener;
import de.igslandstuhl.database.events.EventType;
import de.igslandstuhl.database.events.ListenerPriority;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.webserver.access.AccessManagerEvent;

public class AccessListener extends EventListener<AccessManagerEvent> {
    private static final AccessListener instance = new AccessListener();

    public static AccessListener getInstance() {
        return instance;
    }

    private AccessListener() {
        super(ListenerPriority.HIGH);
    }
    @Override
    public EventType<AccessManagerEvent> getEventType() {
        return AccessManagerEvent.TYPE;
    }

    @Override
    public void onEvent(AccessManagerEvent event) {
        User currentUser = Server.getInstance().getWebServer().getSessionManager().getSessionUser(event.getRequest());
        if (currentUser == null) currentUser = User.ANONYMOUS;
        UserEffect effect = UserEffect.get(currentUser);
        event.changeAccessState(effect.testAccess(event.getPath(), event.getRequest()));
    }
    
}
