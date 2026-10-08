package de.igslandstuhl.database.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.WebServer;
import de.igslandstuhl.database.server.webserver.access.AccessManagerEvent;
import de.igslandstuhl.database.server.webserver.access.AccessState;
import de.igslandstuhl.database.server.webserver.requests.HttpRequest;
import de.igslandstuhl.database.server.webserver.sessions.SessionManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class AccessListenerFailClosedTest {
    @Test
    void missingAnonymousEffectIsUnauthorized() {
        assertMissingEffectIs(AccessState.UNAUTHORIZED, User.ANONYMOUS);
    }

    @Test
    void missingAuthenticatedEffectIsRestricted() {
        User user = mock(User.class);
        when(user.getUsername()).thenReturn("missing-effect-user@example.invalid");
        when(user.isStudent()).thenReturn(true);
        assertMissingEffectIs(AccessState.RESTRICTED, user);
    }

    private static void assertMissingEffectIs(AccessState expected, User sessionUser) {
        Server server = mock(Server.class);
        WebServer webServer = mock(WebServer.class);
        SessionManager sessions = mock(SessionManager.class);
        HttpRequest request = mock(HttpRequest.class);
        PermissionManager manager = mock(PermissionManager.class);
        when(server.getWebServer()).thenReturn(webServer);
        when(webServer.getSessionManager()).thenReturn(sessions);
        when(sessions.getSessionUser(request)).thenReturn(sessionUser);
        when(request.getPath()).thenReturn("/protected");

        try (MockedStatic<Server> serverStatic = mockStatic(Server.class);
             MockedStatic<PermissionManager> managerStatic = mockStatic(PermissionManager.class)) {
            serverStatic.when(Server::getInstance).thenReturn(server);
            managerStatic.when(PermissionManager::getInstance).thenReturn(manager);
            when(manager.getLogger()).thenReturn(mock(org.slf4j.Logger.class));

            AccessManagerEvent event = new AccessManagerEvent(AccessState.AUTHORIZED, "/protected", request);
            AccessListener.getInstance().onEvent(event);

            assertEquals(expected, event.getChangedAccessState().orElseThrow());
        }
    }
}
