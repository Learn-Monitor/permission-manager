package de.igslandstuhl.database.permissions;

import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.events.EventListener;
import de.igslandstuhl.database.events.EventType;
import de.igslandstuhl.database.events.ListenerPriority;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.webserver.access.AccessManager;
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
        // PUBLIC is already a complete access decision.  A logged-in user's
        // permission snapshot must not turn shared CSS/JS/assets into 403s,
        // especially while a dual-role session is on the mode-selection page.
        if (event.getAccessState() == de.igslandstuhl.database.server.webserver.access.AccessState.PERMITTED)
            return;
        User currentUser = Server.getInstance().getWebServer().getSessionManager().getSessionUser(event.getRequest());
        if (currentUser == null) currentUser = User.ANONYMOUS;

        UserEffect effect = UserEffect.get(currentUser);
        if (effect == null) {
            event.changeAccessState(currentUser == User.ANONYMOUS
                    ? de.igslandstuhl.database.server.webserver.access.AccessState.UNAUTHORIZED
                    : de.igslandstuhl.database.server.webserver.access.AccessState.RESTRICTED);
            return;
        }

        // AccessLevel.ADMIN is the account-level authorization boundary for
        // administrator routes. Preserve an already authorized decision only
        // when the identity really has the admin role and this request is in
        // the server-side admin mode. Teacher mode therefore remains denied,
        // while stale fine-grained permission snapshots cannot deny the
        // visible Admin role from using its admin workspace. Missing effects
        // still fail closed above.
        if (event.getAccessState() == de.igslandstuhl.database.server.webserver.access.AccessState.AUTHORIZED
                && AccessManager.getInstance().isAdminRouteAuthorized(currentUser, event.getPath(), event.getRequest()))
            return;

        event.changeAccessState(effect.testAccess(event.getRequest().getPath(), event.getRequest()));
    }
    
}
