package de.igslandstuhl.database.permissions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLiteConnection;
import de.igslandstuhl.database.server.sql.SQLHelper;
import de.igslandstuhl.database.server.sql.SQLVoidProcess;

class RoleNodePersistenceTest {
    @Test
    void insertIntoDatabasePassesUsernameRoleAndActiveInSqlOrder() throws Exception {
        assertInsertParameters(false);
        assertInsertParameters(true);
    }

    @Test
    void addUserRoleSqlPersistsAndUpdatesActiveColumn() throws SQLException, IOException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            connection.createStatement().execute(resource("/sql/tables/roles.sql"));

            writeRole(connection, "student", "learner", false);
            assertFalse(readActive(connection, "student", "learner"));

            writeRole(connection, "student", "learner", true);
            assertTrue(readActive(connection, "student", "learner"));

            writeRole(connection, "teacher", "educator", true);
            assertTrue(readActive(connection, "teacher", "educator"));
        }
    }

    private static void assertInsertParameters(boolean active) throws Exception {
        Role role = new Role("student", "Student role");
        RoleNode node = new RoleNode(role, "learner", active);
        Server server = mock(Server.class);
        SQLiteConnection connection = mock(SQLiteConnection.class);
        SQLVoidProcess process = mock(SQLVoidProcess.class);
        String activeParameter = String.valueOf(active);

        try (MockedStatic<Server> serverMock = mockStatic(Server.class);
             MockedStatic<SQLHelper> sqlHelperMock = mockStatic(SQLHelper.class)) {
            serverMock.when(Server::getInstance).thenReturn(server);
            when(server.getConnection()).thenReturn(connection);
            sqlHelperMock.when(() -> SQLHelper.getAddObjectProcess(
                    "user_role", "learner", "student", activeParameter))
                .thenReturn(process);

            Method insert = RoleNode.class.getDeclaredMethod("insertIntoDatabase");
            insert.setAccessible(true);
            insert.invoke(node);

            sqlHelperMock.verify(() -> SQLHelper.getAddObjectProcess(
                "user_role", "learner", "student", activeParameter));
            verify(connection).executeVoidProcessSecure(process);
        }
    }

    private static void writeRole(Connection connection, String role, String username, boolean active)
            throws SQLException, IOException {
        try (PreparedStatement statement = connection.prepareStatement(resource("/sql/pushes/add_user_role.sql"))) {
            statement.setString(1, username);
            statement.setString(2, role);
            statement.setBoolean(3, active);
            statement.executeUpdate();
        }
    }

    private static boolean readActive(Connection connection, String role, String username)
            throws SQLException, IOException {
        try (PreparedStatement statement = connection.prepareStatement(resource("/sql/queries/is_active_role_node.sql"))) {
            statement.setString(1, username);
            statement.setString(2, role);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getBoolean("active");
            }
        }
    }

    private static String resource(String path) throws IOException {
        try (InputStream stream = RoleNodePersistenceTest.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing test resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
