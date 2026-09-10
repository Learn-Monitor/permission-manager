# Flexible curriculum permissions

The Permission Manager supplies the outer function/route authorization for the
flexible curriculum backend. The implementation uses four independently editable
flat permissions; no class/subject permission expansion is added.

## Route contract and defaults

| Permission | Default | Prerequisite | Routes |
|---|---|---|---|
| `curriculum_view` | teacher | none | GET `/curriculum.js`; POST `/curriculum-catalog`, `/curriculum-structure`, `/curriculum-budget`, `/flexible-tasks`, `/curriculum-progress` |
| `curriculum_manage_flexible` | teacher | `curriculum_view` | POST `/add-flexible-task`, `/edit-flexible-task` |
| `curriculum_complete_flexible` | teacher | `curriculum_view` | POST `/complete-flexible-task` |
| `curriculum_manage_central` | admin | `curriculum_view` | POST `/add-curriculum-topic`, `/rename-topic`, `/add-curriculum-task`, `/edit-task` |

`curriculum_view` additionally grants the existing GET `/get-permissions` self-query
so curriculum clients can read their own effective permissions. Upstream already
registers that handler and declares USER access in GET metadata, but previously
omitted it from the PM path allowlist. This grants no permission administration or
other user's information. The 13 new curriculum paths each have exactly one owner;
none is PUBLIC, USER, or part of a generic permission.

Teachers initially receive view, flexible management and flexible completion.
Administrators initially receive all four. Students and anonymous users receive
none. Existing default-level evaluation and `default-roles.yaml` are unchanged:
permission defaults already supply the required teacher/admin hierarchy. No
curriculum permission is hardwired into the bundled roles.

## Dependencies and administration

The existing `depends` field has historically been metadata without runtime
prerequisite enforcement. The new optional boolean `require_dependencies` enables
prerequisite enforcement for these four definitions only. Omitted/false preserves
existing behavior, including the five-argument `PermissionEffect` constructor.
This avoids silently changing access for existing permissions with legacy
unresolved or informational dependencies.

`UserEffect` resolves already-granted permissions iteratively. It does not grant
prerequisites or create/toggle nodes. Missing prerequisites and cycles fail closed;
reordered inputs and duplicate grants are safe. Effective permissions exposed to
JavaScript and used for routing come from the same resolved set. Disabling view
makes the three dependent functions ineffective but preserves their stored node
values; restoring view restores those grants.

Definitions are registered through the existing permission loader and are listed
by permission administration. Defaults are used only for missing user nodes;
stored nodes are read before insert. Repeated initialization upserts permission
definitions without resetting user node values. Synthetic SQLite tests cover a
second initialization and a simulated cold start with cleared registries/caches,
including manual false and true values and stable row counts.

Role grants retain the existing **additive** semantics. An inactive direct user
node is not an explicit deny against an active role granting the same permission.
To revoke effective access, remove all applicable role grants as well. Role grants
are deduplicated and must also satisfy opted-in prerequisites. The standard roles
do not grant the new permissions, so direct deactivation works with defaults.
The SQL schema, node persistence implementation and role configuration are unchanged.

## Backend boundary

All four permissions deliberately have empty `post_restrictions`. The generic
student/class/subject restrictions do not implement this API's `classId`,
`subjectId`, `taskId` or session-derived context contract and are not imposed here.
The backend remains authoritative for session identity, teacher ID, fresh
`teacher_classes` and `teacher_subjects` assignments, subject/class/semester,
ownership, student context and the 105-token limit. An explicit PM grant never
replaces backend role or ownership checks; a teacher granted central management
in PM still cannot pass the backend administrator check.

## JavaScript contract and frontend follow-up

The existing script provides `await hasPermission(name)`, waits for the initial
`/get-permissions` response and fails closed on request failure. Calling
`await loadCurrentPermissions()` refreshes the client snapshot. Three executable
Node tests exercise these behaviors with the actual shipped PM script.

The Sprint 1 standard editor was inspected at backend commit
`1165d1f423950dd4bfd06bdc581f399d8ac1c72c`. Its flexible forms are constructed
unconditionally after loading the context; central controls only check
`catalog.admin`. It never calls `hasPermission`. Therefore a user with view but
revoked management still sees edit/create controls. PM denies the corresponding
requests (tested); the backend remains an additional authorization boundary.
The editor also expects JSON errors, so PM access-denial presentation needs a
frontend integration test rather than assuming a useful error message.

Concrete follow-up in the backend frontend:

1. Await permission readiness before constructing the editor; support operation
   without the optional PM plugin using backend roles as the fallback contract.
2. Render read-only task labels when `curriculum_manage_flexible` is unavailable;
   hide/disable flexible create/edit forms. Gate central controls on both admin
   status and `curriculum_manage_central`.
3. Gate future completion controls independently on
   `curriculum_complete_flexible`, and gate editor loading on `curriculum_view`.
4. Refresh permissions when appropriate and handle denied requests cleanly,
   including non-JSON denial responses. Test revoked permissions in real DOM flows.

No backend or alternate frontend files are changed in this sprint. Existing
Results and overlay permissions retain their behavior. Their later consumption
of flexible progress must use explicit teacher/class/semester context; independent
teacher budgets must not be summed into an unscoped total. Definition edits require
cache invalidation; do not introduce awarded-token snapshots.

## Verification and build

```sh
./gradlew test build
node --test src/test/js/*.test.cjs
```

The Java tests use synthetic in-memory SQLite and mocked users/server boundaries;
no server listener or runtime database is needed. They exercise the real permission
loader, node lifecycle, SQL resources, effective permissions and GET/POST decisions.
Generics are inspected for route overlap but not expanded into real school data.
Existing flat routes are checked against their pre-existing additive grant behavior.

Initial upstream base: freshly fetched official main
`d8ae29288964312b6ec3db1208c2fb0a319bdfac` (10 September 2026).
On that base, `./gradlew test build --offline` passes 12 Java tests (4 existing,
8 new), plus 3 Node tests, with no failures/errors/skips. `build` produces the
plugin JAR and sources JAR; this project has no shadow-JAR task. The fixture needs
plugin-loader and SLF4J as test dependencies, matching existing compile-only
versions; production dependencies are unchanged. Node 22.14.0 was used locally.
No publishing, deployment, service changes or live database operations occur.
