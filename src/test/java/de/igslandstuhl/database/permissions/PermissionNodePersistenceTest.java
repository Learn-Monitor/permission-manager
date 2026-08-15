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

class PermissionNodePersistenceTest {
    @Test
    void insertIntoDatabasePassesPermissionUsernameAndActiveInSqlOrder() throws Exception {
        PermissionNode node = new PermissionNode(new Permission("view_grades", "View grades"), "student", false);
        Server server = mock(Server.class);
        SQLiteConnection connection = mock(SQLiteConnection.class);
        SQLVoidProcess process = mock(SQLVoidProcess.class);

        try (MockedStatic<Server> serverMock = mockStatic(Server.class);
             MockedStatic<SQLHelper> sqlHelperMock = mockStatic(SQLHelper.class)) {
            serverMock.when(Server::getInstance).thenReturn(server);
            when(server.getConnection()).thenReturn(connection);
            sqlHelperMock.when(() -> SQLHelper.getAddObjectProcess(
                    "permission_node", "view_grades", "student", "false"))
                .thenReturn(process);

            Method insertIntoDatabase = PermissionNode.class.getDeclaredMethod("insertIntoDatabase");
            insertIntoDatabase.setAccessible(true);
            insertIntoDatabase.invoke(node);

            sqlHelperMock.verify(() -> SQLHelper.getAddObjectProcess(
                "permission_node", "view_grades", "student", "false"));
            verify(connection).executeVoidProcessSecure(process);
        }
    }

    @Test
    void addPermissionNodeSqlPersistsAndUpdatesActiveColumn() throws SQLException, IOException {
        // This complements the Java-level test above by exercising only the SQL resources.
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            connection.createStatement().execute("CREATE TABLE permissions (name VARCHAR(255) PRIMARY KEY)");
            connection.createStatement().execute("INSERT INTO permissions (name) VALUES ('view_grades')");
            connection.createStatement().execute(resource("/sql/tables/permnodes.sql"));

            writeNode(connection, "view_grades", "student", false);
            assertFalse(readActive(connection, "view_grades", "student"));

            writeNode(connection, "view_grades", "student", true);
            assertTrue(readActive(connection, "view_grades", "student"));
        }
    }

    private static void writeNode(Connection connection, String permission, String username, boolean active)
            throws SQLException, IOException {
        try (PreparedStatement statement = connection.prepareStatement(resource("/sql/pushes/add_permission_node.sql"))) {
            statement.setString(1, permission);
            statement.setString(2, username);
            statement.setBoolean(3, active);
            statement.executeUpdate();
        }
    }

    private static boolean readActive(Connection connection, String permission, String username)
            throws SQLException, IOException {
        try (PreparedStatement statement = connection.prepareStatement(resource("/sql/queries/is_active_node.sql"))) {
            statement.setString(1, permission);
            statement.setString(2, username);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getBoolean("active");
            }
        }
    }

    private static String resource(String path) throws IOException {
        try (InputStream stream = PermissionNodePersistenceTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Missing test resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
