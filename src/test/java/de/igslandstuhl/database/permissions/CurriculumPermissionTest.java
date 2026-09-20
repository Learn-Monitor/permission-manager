package de.igslandstuhl.database.permissions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.yaml.snakeyaml.Yaml;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.permissions.meta.PermissionsConfigLoader;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.resources.ResourceLocation;
import de.igslandstuhl.database.server.resources.ResourceManager;
import de.igslandstuhl.database.server.sql.*;
import de.igslandstuhl.database.server.webserver.access.*;
import de.igslandstuhl.database.server.webserver.requests.*;

@SuppressWarnings("unchecked")
class CurriculumPermissionTest {
    private static final Map<String, List<String>> ROUTES = Map.of(
        "curriculum_view", List.of("/curriculum.js", "/curriculum-catalog", "/curriculum-structure",
            "/curriculum-budget", "/flexible-tasks", "/curriculum-progress", "/curriculum-teacher-roster",
            "/flexible-curriculum-structure"),
        "curriculum_manage_flexible", List.of("/add-flexible-task", "/edit-flexible-task", "/add-flexible-topic", "/rename-flexible-topic"),
        "curriculum_complete_flexible", List.of("/complete-flexible-task"),
        "curriculum_manage_central", List.of("/add-curriculum-topic", "/rename-topic",
            "/add-curriculum-task", "/edit-task", "/central-curriculum-overview",
            "/preview-central-curriculum-import", "/import-central-curriculum"));

    private static final Map<String,List<String>> CONTEXT_ROUTES = Map.of(
        "curriculum_student_progress",List.of("/my-curriculum-progress","/my-curriculum-catalog","/my-curriculum-subjects"),
        "curriculum_assign_context",List.of("/curriculum-students","/assign-curriculum-context",
            "/curriculum-transfer-preview","/transfer-curriculum-context"));

    private Connection database;
    private PermissionManager manager;
    private ResourceManager resources;
    private MockedStatic<Server> servers;
    private MockedStatic<PermissionManager> managers;
    private MockedStatic<User> users;
    private User teacher, admin, student;

    @BeforeEach
    void setup() throws Exception {
        clearNodeCaches();
        database = DriverManager.getConnection("jdbc:sqlite::memory:");
        for (String table : List.of("permissions", "permnodes", "roles")) {
            database.createStatement().execute(resource("/sql/tables/" + table + ".sql"));
        }
        Server server = mock(Server.class);
        SQLiteConnection connection = mock(SQLiteConnection.class);
        resources = mock(ResourceManager.class);
        servers = mockStatic(Server.class);
        servers.when(Server::getInstance).thenReturn(server);
        when(server.getConnection()).thenReturn(connection);
        when(server.getResourceManager()).thenReturn(resources);
        when(resources.readResourceCompletely(any(ResourceLocation.class))).thenAnswer(call -> {
            ResourceLocation l = call.getArgument(0);
            return resource("/" + l.context() + "/" + l.namespace() + "/" + l.resource());
        });
        // Use the shipped SQL and real SQLHelper/SQLVoidProcess against synthetic SQLite only.
        doAnswer(call -> {
            SQLVoidProcess process = call.getArgument(0);
            process.execute(database::prepareStatement);
            return null;
        }).when(connection).executeVoidProcessSecure(any(SQLVoidProcess.class));
        doAnswer(call -> {
            Consumer<String[]> callback = call.getArgument(0);
            String query = call.getArgument(1);
            String[] fields = call.getArgument(2);
            String[] args = (String[]) call.getRawArguments()[3];
            try (var statement = database.prepareStatement(resource("/sql/queries/" + query + ".sql"))) {
                SQLHelper.insertArgs(statement, args);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        String[] values = new String[fields.length];
                        for (int i = 0; i < fields.length; i++) values[i] = rows.getString(fields[i]);
                        callback.accept(values);
                    }
                }
            }
            return null;
        }).when(server).processRequest(any(), anyString(), any(String[].class), any(String[].class));
        manager = mock(PermissionManager.class);
        managers = mockStatic(PermissionManager.class);
        managers.when(PermissionManager::getInstance).thenReturn(manager);
        when(manager.getLogger()).thenReturn(mock(org.slf4j.Logger.class));
        resetRegistries();
        teacher = user("teacher", false, true, false);
        admin = user("admin", false, false, true);
        student = user("student", true, false, false);
        users = mockStatic(User.class);
        users.when(User::getAllUsers).thenReturn(List.of(teacher, admin, student, User.ANONYMOUS));
        for (User user : List.of(teacher, admin, student)) {
            users.when(() -> User.getUser(user.getUsername())).thenReturn(user);
        }
        Map<String, Object> flatConfig = new LinkedHashMap<>(config());
        // No class/subject fixtures needed: curriculum uses flat function permissions only.
        flatConfig.put("generics", List.of());
        when(resources.readJsonResourceFullMerged(PermissionManager.PERMISSIONS_CONFIG)).thenReturn((Map) flatConfig);
        initialize();
    }

    @AfterEach
    void teardown() throws Exception {
        if (users != null) users.close();
        if (managers != null) managers.close();
        if (servers != null) servers.close();
        if (database != null) database.close();
        clearNodeCaches();
    }

    @Test
    void enrollmentIsAdminOnlyAndPublicationIsStaffOnlyWithViewDependency() {
        for(User user:List.of(admin,teacher,student,User.ANONYMOUS)) {
            for(String path:List.of("/curriculum-enrollment-catalog","/set-curriculum-subject-type","/assign-grade-curriculum","/curriculum-wpf-roster","/assign-curriculum-wpf","/create-curriculum-semester","/activate-curriculum-semester","/remove-grade-curriculum-subject"))assertContextAccess(user,path,RequestType.POST,user==admin);
            for(String path:List.of("/curriculum-releases","/set-curriculum-release"))assertContextAccess(user,path,RequestType.POST,user==admin||user==teacher);
        }
        node(admin,"curriculum_view").setActive(false);node(teacher,"curriculum_view").setActive(false);UserEffect.registerAll();
        assertContextAccess(admin,"/assign-grade-curriculum",RequestType.POST,false);assertContextAccess(teacher,"/set-curriculum-release",RequestType.POST,false);
    }

    @Test
    void retiredPersistedPermissionDoesNotBreakNewUserRegistration() throws Exception {
        Permission retired = new Permission("demo_retired_permission", "Synthetic retired definition");
        retired.register();
        assertNull(manager.permissionEffectRegistry().get(retired));

        assertDoesNotThrow(UserEffect::registerAll);
        assertNotNull(UserEffect.get(teacher));
        assertTrue(Arrays.stream(UserEffect.get(teacher).getPermissions())
            .anyMatch(p -> p.getName().equals("curriculum_view")));
        assertFalse(Arrays.asList(UserEffect.get(teacher).getPermissions()).contains(retired));
        try (var rows = database.createStatement().executeQuery(
                "SELECT COUNT(*) FROM permnodes WHERE permission='demo_retired_permission'")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
        }
        assertSame(retired, Permission.getByName("demo_retired_permission"));
    }

    @Test
    void everyCurriculumRouteHasExactlyOneFunctionPermissionAndNoScopeRestrictions() throws Exception {
        Map<String, Object> config = config();
        List<Map<String, Object>> all = Stream.of("flat", "generics")
            .flatMap(s -> ((List<Map<String, Object>>) config.get(s)).stream()).toList();
        ROUTES.forEach((name, paths) -> paths.forEach(path -> {
            var matches = all.stream().filter(p -> ((List<?>) p.get("paths")).contains(path)).toList();
            assertEquals(1, matches.size(), path);
            var permission = matches.get(0);
            assertEquals(name, permission.get("name"), path);
            assertEquals(name.equals("curriculum_manage_central") ? "admin" : "teacher", permission.get("default"));
            assertEquals(List.of(), permission.get("post_restrictions"));
            assertEquals(name.equals("curriculum_view") ? List.of() : List.of("curriculum_view"), permission.get("depends"));
            assertEquals(true, permission.get("require_dependencies"));
        }));
        for (String name : ROUTES.keySet()) {
            Permission permission = Permission.getByName(name);
            assertNotNull(permission);
            assertTrue(Permission.getAll().contains(permission), "Listed in permission administration");
            assertFalse(Arrays.asList(manager.permissionEffectRegistry().get(permission).depends()).contains(null));
        }
    }

    @Test
    void defaultUsersHaveExpectedNodesEffectivePermissionsAndGetAndPostAccess() {
        for (User user : List.of(teacher, admin, student, User.ANONYMOUS)) {
            ROUTES.forEach((name, paths) -> {
                boolean allowed = user == admin || user == teacher && !name.equals("curriculum_manage_central");
                if (user != User.ANONYMOUS) {
                    assertEquals(allowed, node(user, name).isActive(), user.getUsername() + ": " + name);
                }
                assertEquals(allowed, effectiveNames(user).contains(name));
                for (String path : paths) assertAccess(user, path, allowed);
            });
        }
        assertAccess(teacher, "/get-permissions", true);
        assertAccess(admin, "/get-permissions", true);
    }

    @Test
    void secondInitializationAndColdRestartPreserveManualFalseAndTrueWithoutDuplicates() throws Exception {
        int permissions = count("permissions"), nodes = count("permnodes");
        node(teacher, "curriculum_manage_flexible").setActive(false);
        node(teacher, "curriculum_complete_flexible").setActive(false);
        node(admin, "curriculum_manage_central").setActive(false);
        // A manual grant also survives; the backend still rejects teacher central mutations.
        node(teacher, "curriculum_manage_central").setActive(true);
        for (boolean cold : List.of(false, true)) {
            if (cold) { clearNodeCaches(); resetRegistries(); }
            initialize();
            assertEquals(permissions, count("permissions"));
            assertEquals(nodes, count("permnodes"));
            assertEquals(permissions, Permission.getAll().size());
            assertFalse(node(teacher, "curriculum_manage_flexible").isActive());
            assertFalse(node(teacher, "curriculum_complete_flexible").isActive());
            assertFalse(node(admin, "curriculum_manage_central").isActive());
            assertTrue(node(teacher, "curriculum_manage_central").isActive());
            assertAccess(teacher, "/edit-flexible-task", false);
            assertAccess(teacher, "/complete-flexible-task", false);
            assertAccess(admin, "/edit-task", false);
            assertAccess(teacher, "/curriculum.js", true);
        }
    }

    @Test
    void withdrawingViewBlocksDependantsWithoutRewritingTheirNodesAndRestoringViewRestoresAccess() {
        node(teacher, "curriculum_view").setActive(false);
        UserEffect.registerAll();
        ROUTES.values().forEach(paths -> paths.forEach(path -> assertAccess(teacher, path, false)));
        assertTrue(node(teacher, "curriculum_manage_flexible").isActive());
        assertTrue(node(teacher, "curriculum_complete_flexible").isActive());
        assertFalse(effectiveNames(teacher).contains("curriculum_manage_flexible"));
        node(teacher, "curriculum_view").setActive(true);
        UserEffect.registerAll();
        assertAccess(teacher, "/edit-flexible-task", true);
        assertAccess(teacher, "/complete-flexible-task", true);
    }

    @Test
    void rolesRemainAdditiveAndEffectivePermissionsAreDeduplicated() throws Exception {
        Role role = Role.getByNameOrCreate("curriculum_editor", "Curriculum editor");
        role.addPermissions(Permission.getByName("curriculum_view"), Permission.getByName("curriculum_manage_flexible"));
        database.createStatement().execute("INSERT INTO user_roles (username, role, active) VALUES ('teacher', 'curriculum_editor', 'true')");
        node(teacher, "curriculum_manage_flexible").setActive(false);
        UserEffect.registerAll();
        assertFalse(node(teacher, "curriculum_manage_flexible").isActive());
        assertAccess(teacher, "/edit-flexible-task", true); // Existing union-of-grants semantics.
        List<String> names = effectiveNames(teacher);
        assertEquals(new HashSet<>(names).size(), names.size());
        RoleNode.getRoleNode("teacher", role).setActive(false);
        UserEffect.registerAll();
        assertAccess(teacher, "/edit-flexible-task", false);
    }

    @Test
    void cyclesMissingDependenciesAndReorderedInputsTerminateWithoutCreatingNodes() throws Exception {
        int nodes = count("permnodes");
        Permission a = new Permission("a", "A"), b = new Permission("b", "B");
        new PermissionEffect(a, new String[]{"/cycle-a"}, new de.igslandstuhl.database.permissions.restrictions.PostRestriction[0],
            new Permission[]{b}, AccessLevel.TEACHER, true).register();
        new PermissionEffect(b, new String[]{"/cycle-b"}, new de.igslandstuhl.database.permissions.restrictions.PostRestriction[0],
            new Permission[]{a}, AccessLevel.TEACHER, true).register();
        assertEquals(0, new UserEffect(teacher, new Permission[]{a, b}).getPermissions().length);
        assertEquals(0, new UserEffect(teacher, new Permission[]{a}).getPermissions().length);
        Permission view = Permission.getByName("curriculum_view"), manage = Permission.getByName("curriculum_manage_flexible");
        assertEquals(2, new UserEffect(teacher, new Permission[]{manage, view, manage}).getPermissions().length);
        assertEquals(nodes, count("permnodes"));
    }

    @Test
    void legacyDependencyMetadataKeepsItsExistingNonEnforcingBehavior() {
        Permission permission = Permission.getByName("manage_permissions");
        UserEffect effect = new UserEffect(admin, new Permission[]{permission});
        assertEquals(AccessState.AUTHORIZED, effect.testAccess("/manage_permissions", mock(HttpRequest.class)));
    }

    @Test
    void adminPolishStudentManagementRoutesAreAdminOnlyAtRuntime() {
        for (String path : List.of(
                "/manage_students",
                "/manage_students.js",
                "/students",
                "/archived-students",
                "/edit-student-profile",
                "/admin-student-profile.js",
                "/archive-student",
                "/reactivate-student",
                "/admin-dashboard.js")) {
            assertAccess(admin, path, true);
            assertAccess(teacher, path, false);
            assertAccess(student, path, false);
        }

        for (User user : List.of(admin, teacher, student, User.ANONYMOUS)) {
            assertAccess(user, "/delete-student", false);
        }
    }

    @Test
    void allExistingFlatRoutesRetainTheirEffectiveRoleBoundaries() {
        var legacy = Permission.getAll().stream()
            .filter(p -> !ROUTES.containsKey(p.getName()) && !CONTEXT_ROUTES.containsKey(p.getName()) && !List.of("curriculum_publish","curriculum_manage_enrollment").contains(p.getName()))
            .map(manager.permissionEffectRegistry()::get).toList();
        for (User user : List.of(teacher, admin, student, User.ANONYMOUS)) {
            var active = legacy.stream().filter(e -> user == User.ANONYMOUS
                ? e.defaultLevel() == AccessLevel.PUBLIC : node(user, e.permission().getName()).isActive()).toList();
            legacy.stream().flatMap(e -> Arrays.stream(e.allowedPaths())).distinct()
                .filter(path -> !path.equals("/get-permissions")) // Explicit new self-query grant.
                .forEach(path -> {
                    boolean allowed = active.stream().anyMatch(e -> Arrays.asList(e.allowedPaths()).contains(path));
                    AccessState expected = user == User.ANONYMOUS
                        ? allowed ? AccessState.PERMITTED : AccessState.UNAUTHORIZED
                        : allowed ? AccessState.AUTHORIZED : AccessState.RESTRICTED;
                    assertEquals(expected, UserEffect.get(user).testAccess(path, mock(PostRequest.class)),
                        user.getUsername() + ": " + path);
                });
        }
    }

    @Test
    void contextDefaultsMatchBackendRolesWithoutTeacherOrAnonymousGrants() {
        for(User user:List.of(student,teacher,admin,User.ANONYMOUS)) {
            CONTEXT_ROUTES.forEach((name,paths)->{
                boolean allowed=name.equals("curriculum_student_progress") ? user==student : user==admin;
                if(user!=User.ANONYMOUS)assertEquals(allowed,node(user,name).isActive(),user.getUsername()+": "+name);
                assertEquals(allowed,effectiveNames(user).contains(name));
                paths.forEach(path->assertContextAccess(user,path,RequestType.POST,allowed));
            });
        }
        assertFalse(effectiveNames(student).contains("curriculum_view"));
    }

    @Test
    void studentCatalogUsesOwnPermissionAndPostOnlyWithRevocation() {
        String path="/my-curriculum-catalog";
        for(User user:List.of(student,teacher,admin,User.ANONYMOUS)) {
            assertContextAccess(user,path,RequestType.POST,user==student);
            assertContextAccess(user,path,RequestType.GET,false);
        }
        node(student,"curriculum_student_progress").setActive(false);
        UserEffect.registerAll();
        assertContextAccess(student,path,RequestType.POST,false);
        assertFalse(effectiveNames(student).contains("curriculum_view"));
    }

    @Test
    void discoveredBackendRoutesHaveExactlyOneCorrectOwnerOnBothTracks() throws Exception {
        Map<String,Map<String,Object>> tracks = new Yaml().load(resource("/contracts/student-context-routes.json"));
        assertEquals(Set.of("upstream","canonical-v2"),tracks.keySet());
        List<Map<String,Object>> permissions=Stream.of("flat","generics").flatMap(k->((List<Map<String,Object>>)configUnchecked().get(k)).stream()).toList();
        Set<String> expected=new HashSet<>();CONTEXT_ROUTES.values().forEach(expected::addAll);
        for(var track:tracks.values()) {
            Map<String,Map<String,String>> before=(Map)track.get("before"),after=(Map)track.get("after");
            Set<String> added=new HashSet<>(before.get("POST").keySet());
            added.retainAll(expected);
            for(String method:List.of("GET","POST")) {
                Set<String> paths=new HashSet<>(after.get(method).keySet());paths.removeAll(before.get(method).keySet());
                if(method.equals("GET"))assertTrue(paths.isEmpty());
                for(String path:paths) {
                    var owners=permissions.stream().filter(p->((List<?>)p.get("paths")).contains(path)).toList();
                    assertEquals(1,owners.size(),path);
                    var owner=owners.get(0);String name=(String)owner.get("name");
                    assertTrue(CONTEXT_ROUTES.getOrDefault(name,List.of()).contains(path));
                    assertEquals(after.get(method).get(path),owner.get("default"));
                    assertTrue(Set.of("student","admin").contains(owner.get("default")));
                    assertEquals(List.of("POST"),owner.get("allowed_methods"));
                    if(name.equals("curriculum_student_progress"))assertEquals(true,owner.get("exact_default"));
                    assertEquals(List.of(),owner.get("depends"));assertEquals(List.of(),owner.get("post_restrictions"));
                    added.add(path);
                }
            }
            assertEquals(expected,added);
        }
    }

    private Map<String,Object> configUnchecked() {
        try{return config();}catch(Exception e){throw new AssertionError(e);}
    }

    @Test
    void newRoutesRejectGetAndUnknownMethodsWhileLegacyRulesRemainUnchanged() {
        CONTEXT_ROUTES.forEach((name,paths)->{
            User user=name.equals("curriculum_student_progress")?student:admin;
            paths.forEach(path->{
                assertContextAccess(user,path,RequestType.POST,true);
                assertContextAccess(user,path,RequestType.GET,false);
                assertContextAccess(user,path,null,false);
            });
        });
        // Opt-in only: prior callers/configurations still have path-only behavior.
        for(String path:List.of("/curriculum-catalog","/curriculum.js"))
            assertContextAccess(teacher,path,RequestType.GET,true);
    }

    @Test
    void contextFunctionsDoNotDependOnTeacherViewOrCentralManagement() {
        node(admin,"curriculum_view").setActive(false);
        node(admin,"curriculum_manage_central").setActive(false);
        UserEffect.registerAll();
        CONTEXT_ROUTES.get("curriculum_assign_context").forEach(path->assertContextAccess(admin,path,RequestType.POST,true));
        assertContextAccess(student,"/my-curriculum-progress",RequestType.POST,true);
        for(String name:CONTEXT_ROUTES.keySet()) {
            var effect=manager.permissionEffectRegistry().get(Permission.getByName(name));
            assertEquals(0,effect.depends().length);
        }
    }

    @Test
    void studentAndAdminRevocationsSurviveRepeatedInitializationAndColdStart() throws Exception {
        int permissions=count("permissions"),nodes=count("permnodes");
        node(student,"curriculum_student_progress").setActive(false);
        node(admin,"curriculum_assign_context").setActive(false);
        node(teacher,"curriculum_student_progress").setActive(true);
        for(boolean cold:List.of(false,true)) {
            if(cold){clearNodeCaches();resetRegistries();}
            initialize();
            assertEquals(permissions,count("permissions"));assertEquals(nodes,count("permnodes"));
            assertEquals(permissions,Permission.getAll().size());
            assertFalse(node(student,"curriculum_student_progress").isActive());
            assertFalse(node(admin,"curriculum_assign_context").isActive());
            assertTrue(node(teacher,"curriculum_student_progress").isActive());
            assertContextAccess(teacher,"/my-curriculum-progress",RequestType.POST,true);
            assertContextAccess(student,"/my-curriculum-progress",RequestType.POST,false);
            CONTEXT_ROUTES.get("curriculum_assign_context").forEach(path->assertContextAccess(admin,path,RequestType.POST,false));
        }
    }

    @Test
    void roleGrantsForNewFunctionsRemainAdditiveAndDoNotGrantView() throws Exception {
        node(student,"curriculum_student_progress").setActive(false);
        Role role=Role.getByNameOrCreate("own_progress","Own progress");
        role.addPermissions(Permission.getByName("curriculum_student_progress"));
        database.createStatement().execute("INSERT INTO user_roles(username,role,active) VALUES('student','own_progress','true')");
        UserEffect.registerAll();
        assertContextAccess(student,"/my-curriculum-progress",RequestType.POST,true);
        assertFalse(effectiveNames(student).contains("curriculum_view"));
        RoleNode.getRoleNode("student",role).setActive(false);UserEffect.registerAll();
        assertContextAccess(student,"/my-curriculum-progress",RequestType.POST,false);
    }

    @Test
    void explicitGrantsOnlyAuthorizeTheFunctionAndNeverInterpretStudentIds() {
        node(teacher,"curriculum_student_progress").setActive(true);
        node(teacher,"curriculum_assign_context").setActive(true);UserEffect.registerAll();
        // Backend still rejects these wrong-role calls; PM does not impersonate a student or admin.
        CONTEXT_ROUTES.values().forEach(paths->paths.forEach(path->assertContextAccess(teacher,path,RequestType.POST,true)));
    }

    @Test
    void emptyMethodPolicyDeniesRatherThanFallingBackToLegacy() {
        Permission permission=new Permission("closed_method_policy","Closed");
        new PermissionEffect(permission,new String[]{"/closed"},new de.igslandstuhl.database.permissions.restrictions.PostRestriction[0],
            new Permission[0],AccessLevel.ADMIN,false,new RequestType[0]).register();
        var effect=new UserEffect(admin,new Permission[]{permission});
        HttpRequest request=mock(HttpRequest.class);when(request.getRequestType()).thenReturn(RequestType.POST);
        assertEquals(AccessState.RESTRICTED,effect.testAccess("/closed",request));
    }

    private static void assertContextAccess(User user,String path,RequestType method,boolean allowed) {
        HttpRequest request=method==RequestType.POST?mock(PostRequest.class):mock(HttpRequest.class);
        when(request.getRequestType()).thenReturn(method);
        AccessState expected=user==User.ANONYMOUS?AccessState.UNAUTHORIZED:allowed?AccessState.AUTHORIZED:AccessState.RESTRICTED;
        assertEquals(expected,UserEffect.get(user).testAccess(path,request),user.getUsername()+": "+method+" "+path);
        verify(request,atMostOnce()).getRequestType();verifyNoMoreInteractions(request);
    }

    private void initialize() {
        Permission.loadAll();
        PermissionsConfigLoader.getInstance().registerAllPermissionEffects();
        UserEffect.registerAll();
    }

    private void resetRegistries() {
        when(manager.permissionRegistry()).thenReturn(new Registry<>());
        when(manager.permissionEffectRegistry()).thenReturn(new Registry<>());
        when(manager.getRoles()).thenReturn(new HashMap<>());
    }

    private static void clearNodeCaches() throws Exception {
        for (Class<?> type : List.of(PermissionNode.class, RoleNode.class)) {
            Field cache = type.getDeclaredField("cache");
            cache.setAccessible(true);
            ((List<?>) cache.get(null)).clear();
        }
    }

    private int count(String table) throws Exception {
        try (var rows = database.createStatement().executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private PermissionNode node(User user, String name) {
        return PermissionNode.getPermissionNode(user.getUsername(), Permission.getByName(name));
    }

    private static List<String> effectiveNames(User user) {
        return Arrays.stream(UserEffect.get(user).getPermissions()).map(Permission::getName).toList();
    }

    private static void assertAccess(User user, String path, boolean allowed) {
        HttpRequest request = path.endsWith(".js") || path.equals("/get-permissions")
            ? mock(HttpRequest.class) : mock(PostRequest.class);
        AccessState expected = user == User.ANONYMOUS ? AccessState.UNAUTHORIZED
            : allowed ? AccessState.AUTHORIZED : AccessState.RESTRICTED;
        assertEquals(expected, UserEffect.get(user).testAccess(path, request), user.getUsername() + ": " + path);
        // Function authorization must not inspect classId, subjectId, taskId or other payload fields.
        verifyNoInteractions(request);
    }

    private static User user(String name, boolean student, boolean teacher, boolean admin) {
        User user = mock(User.class);
        when(user.getUsername()).thenReturn(name);
        when(user.isStudent()).thenReturn(student);
        when(user.isTeacher()).thenReturn(teacher);
        when(user.isAdmin()).thenReturn(admin);
        return user;
    }

    private static Map<String, Object> config() throws Exception {
        return new Yaml().load(resource("/meta/permission-manager/permissions.json"));
    }

    private static String resource(String path) throws Exception {
        try (InputStream stream = CurriculumPermissionTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
