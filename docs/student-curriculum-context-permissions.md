# Student curriculum context permissions

The Permission Manager authorizes functions. The backend remains authoritative
for session identity, student/teacher IDs, subject, class, semester, assignment,
transfers and their explicit mappings, ownership, the 105-token hard limit and
atomic transactions. No payload restrictions, inferred assignments or student-ID
substitution are added by this change.

## Exhaustive route contract

Comparing Sprint 3 to Sprint 3½ on both published backend tracks discovers exactly
five new POST routes, no new GET routes and no new teacher-reader routes:

| Method / route | Permission | Default |
|---|---|---|
| POST `/my-curriculum-progress` | `curriculum_student_progress` | student, exact role |
| POST `/curriculum-students` | `curriculum_assign_context` | admin |
| POST `/assign-curriculum-context` | `curriculum_assign_context` | admin |
| POST `/curriculum-transfer-preview` | `curriculum_assign_context` | admin |
| POST `/transfer-curriculum-context` | `curriculum_assign_context` | admin |

Each route has exactly one permission owner, no generic overlap and no PUBLIC or
USER grant. GET requests to these paths are denied by the new method policy.
Existing `/curriculum-progress` remains staff-only under `curriculum_view` and the
backend's strengthened actor/assignment checks; it is not a new route.

The admin permission deliberately retains the exact capability name already
checked by the backend standard editor: `curriculum_assign_context`. Introducing
a second `curriculum_manage_student_context` name would leave that UI disabled
or require an out-of-scope backend edit. This one group covers minimal roster
reads, assignments, transfer preview and explicit confirmation; central-content
management remains independently configurable.

## Defaults and dependencies

| Principal | Own-progress default | Context administration default |
|---|---|---|
| Anonymous | denied | denied |
| Student | allowed | denied |
| Teacher | denied | denied |
| Admin | denied | allowed |

Neither new permission has dependencies or enables `require_dependencies`.
Student self-service must not require the teacher-default `curriculum_view`.
Administrative route authorization does not technically require curriculum reads.
The existing standard editor separately requires view to render its selectors
and check its capability; an admin who disables view can still use authorized
context APIs. `curriculum_manage_central` does not imply context administration.
No default role YAML changes are needed.

The upstream baseline's STUDENT default also activates teacher/admin nodes,
whereas canonical already uses exact student defaults. To preserve all existing
permissions on both tracks, only the new own-progress group opts into
`exact_default: true`. At **initial node creation**, this intersects the old
result with the exact STUDENT/TEACHER/ADMIN principal type. Existing stored nodes
are read first, never reset by this option. Absent/false preserves each track's
legacy behavior. The new admin group already has an exact admin default.

Explicit user/role grants retain the existing additive semantics. A false direct
node does not override an active role grant. Such grants can authorize a function
in PM but cannot make a teacher a backend administrator or a student session:
`studentProgress` explicitly rejects non-students, uses only the session ID and
rejects supplied student/teacher/class/grade overrides. Administrative operations
repeat their admin checks in the service. PM never examines those payload fields.

## Method policy compatibility

The existing PM matcher is path-only. New optional `allowed_methods: ["POST"]`
limits the two new groups using `HttpRequest.getRequestType()`. Absent policy
retains old matching; an empty policy denies every method; unknown/null request
types do not match the new policies. Existing five- and six-argument
`PermissionEffect` constructors remain available with their original defaults.
The new checks do not inspect request bodies. All existing permission definitions,
role definitions, restrictions and dependency behavior are unchanged.

## Source evidence and validation

Control main `3c7895d223e14636e0a94425ca09f13189060ff3` was verified against its
remote. Architecture, Arcanum working state and the referenced 10 September
Signage/SOL update were read. This sprint changes only permission-manager.

Backend route snapshots in `src/test/resources/contracts/student-context-routes.json`
were extracted from the complete GET/POST metadata at these immutable commits:

| Track | Sprint 3 | Sprint 3½, verified published HEAD |
|---|---|---|
| Upstream | `8dbe2b35f9d60c0309eb48b127a51c23b4109b39` | `205f8d853c7f4011f722c621f8c306243a3bf23b` |
| Canonical v2 | `f4f22abcdeff32c839cb21d61bbae7cd1f55940b` | `739bbccadebabf2acc69911a273cfcda4f9bb665` |

Read alongside the metadata: `docs/student-curriculum-contexts.md`,
`CurriculumRequestHandler`, `Curriculum.studentProgress`/admin operations,
`curriculum.js`, Java context tests and JS capability tests. The backend handler,
UI and feature tests are identical between the two tracks. No backend build or
backend source change is required for PM validation.

Run:

```sh
./gradlew test build --offline
node --test src/test/js/*.test.cjs
python3 scripts/check_student_context_routes.py --backend /path/to/student-database
git diff --check
```

The audit script reads pinned backend Git objects without checkout or mutation,
recomputes both complete route deltas, verifies fixture integrity and one-owner
method/default policies, and compares the current PM's old groups and default
roles to its exact Sprint-2 base. JUnit also derives new routes from both snapshots,
so normal tests do not require a sibling checkout or network access.

Java tests exercise the real loader, permission/node/role logic and shipped SQL
against synthetic in-memory SQLite. They cover defaults, denied GET/unknown
methods, view independence, route coverage, preserved role/dependency semantics,
manual false and true nodes, repeated initialization and a simulated cold start
with cleared node caches and fresh registries over the same SQL rows. Counts
remain stable; no duplicate definitions/nodes. No live database or service is used.
The unchanged PM JavaScript helper is covered by five executable contract tests,
including the exact admin capability and independent student own-progress grant.

## Results follow-up

A Results-only sprint should use `/my-curriculum-progress` for student self-service
and the assigned staff API for authorized reporting. Never accept a student ID
from the student client or sum independent teacher contexts. Reflect missing or
conflicting assignments explicitly, invalidate cached progress after definition
edits/transfers, and retain current definition values rather than award snapshots.
Plan combined browser/backend/PM validation separately; this sprint does not
claim a deployed browser acceptance test or change Results/Overlay permissions.

## Dashboard topic/catalog follow-up (11 September 2026)

The curriculum dashboard backend adds four routes. `/flexible-curriculum-structure`
uses `curriculum_view`; `/add-flexible-topic` and `/rename-flexible-topic` use
`curriculum_manage_flexible` and its existing view dependency. The student-only
POST `/my-curriculum-catalog` uses `curriculum_student_progress`, retaining exact
student defaults and POST-only access. Core session/context checks remain required
regardless of custom PM grants. No new permissions or default node migration.

Validation: 28 Java tests pass against the locally built dashboard-catalog backend
JAR. The role matrix includes new staff routes, and a dedicated catalog test covers
student-only defaults, GET denial and revocation after rebuilding UserEffects
(the existing PM update workflow). No deployment or runtime changes.

### Demo-Abnahme: veraltete Berechtigungsdefinitionen (12.09.2026)

Bestehende Datenbanken können Berechtigungen enthalten, deren Definition nicht
mehr in der aktuellen Konfiguration vorkommt. Bei neuen Benutzern führte das
Erzeugen eines Standard-PermissionNode für solche Einträge zu einer
NullPointerException beim Start. `UserEffect.registerAll()` berücksichtigt nun
nur Berechtigungen mit einem aktuellen PermissionEffect, analog zur bereits
vorhandenen Filterung im UserEffect-Konstruktor. Historische Daten bleiben
bestehen; veraltete Definitionen erteilen keinen Zugriff.

Der Regressionstest `retiredPersistedPermissionDoesNotBreakNewUserRegistration`
prüft die Registrierung neuer Benutzer, weiterhin wirksame Curriculum-Rechte,
fehlende aktive Altrechte und den Erhalt der historischen Definition. 29 Tests
bestehen. Zusätzlich wurde der Start mit einer synthetischen veralteten
Definition im vollständigen Plugin-Verbund und mit allen drei Dashboard-Rollen
geprüft.
