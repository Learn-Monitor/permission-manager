package de.igslandstuhl.database.permissions;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLiteConnection;
import de.igslandstuhl.database.server.sql.SQLHelper;
import de.igslandstuhl.database.server.sql.SQLVoidProcess;

class RoleNodePersistenceTest {
    @Test
    void insertIntoDatabasePersistsTheActualInactiveState() throws Exception {
        Role role = new Role("student", "Student role");
        RoleNode node = new RoleNode(role, "Anton.Apfel", false);
        Server server = mock(Server.class);
        SQLiteConnection connection = mock(SQLiteConnection.class);
        SQLVoidProcess process = mock(SQLVoidProcess.class);

        try (MockedStatic<Server> serverMock = mockStatic(Server.class);
             MockedStatic<SQLHelper> sqlHelperMock = mockStatic(SQLHelper.class)) {
            serverMock.when(Server::getInstance).thenReturn(server);
            when(server.getConnection()).thenReturn(connection);
            sqlHelperMock.when(() -> SQLHelper.getAddObjectProcess(
                    "user_role", "Anton.Apfel", "student", "false"))
                .thenReturn(process);

            Method insert = RoleNode.class.getDeclaredMethod("insertIntoDatabase");
            insert.setAccessible(true);
            insert.invoke(node);

            sqlHelperMock.verify(() -> SQLHelper.getAddObjectProcess(
                "user_role", "Anton.Apfel", "student", "false"));
            verify(connection).executeVoidProcessSecure(process);
        }
    }
}
