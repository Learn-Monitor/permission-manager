# Sprint 2 – Integrationsnachweis vom 10.09.2026

Ausschließlich das Permission-Manager-Repository wurde geändert. Kein PR, kein
Deployment, keine Service-, Runtime-, Secret- oder Live-Datenbankoperation.
PROD und DEMO wurden weder verändert noch für Tests angesprochen.

## Quellen und Git-Basen

Vor Änderungen gelesen: `synchronierer/learn-monitor-control` auf
`3c7895d223e14636e0a94425ca09f13189060ff3`, identisch mit dem per `ls-remote`
geprüften aktuellen main. Insbesondere `docs/arcanum/ARBEITSSTAND.md`,
`docs/ARCHITECTURE.md`, `docs/arcanum/SIGNAGE-PARALLELHOST.md`, README und Workflow.
Die Git-Dateiliste enthielt keine weitere aktuelle Architektur-/Systemlandkarte.

Backend ausschließlich gelesen:

- Upstream-Feature `1165d1f423950dd4bfd06bdc581f399d8ac1c72c`, insbesondere
  `docs/flexible-curriculum.md`, Standard-Editor und Routenmetadaten.
- Canonical-Feature `819df09231ab86378f889c44ef6f01541a69c8ac`, insbesondere
  `docs/flexible-curriculum-integration-20260910.md` und Routenmetadaten.

Offizielles `Learn-Monitor/permission-manager:main` vor Beginn frisch gefetcht:
`d8ae29288964312b6ec3db1208c2fb0a319bdfac`.
Canonical-v2-Basis: `f21145b3ae4ba4bca48b476dcc55ce186c3bc9b8`.

| Stand | Branch | Feature-Commit |
|---|---|---|
| Upstream | `feature/flexible-curriculum-permissions-upstream-20260910` | `12fc737db4f39801e34c787e06898a3e851fe119` |
| Canonical v2 | `feature/flexible-curriculum-permissions-canonical-v2-20260910` | `09187ba232f13f7a02050471105091110e8a4235` |

Der Upstream-Feature-Commit ist zugleich der Upstream-HEAD. Dieser Bericht folgt
als eigener Dokumentationscommit auf den Canonical-Feature-Commit; der vollständige
Canonical-HEAD und die Remote-Verifikation werden nach dem Push im Abschluss
berichtet. Pushziel ist `origin` = `Learn-Monitor/permission-manager`, ausschließlich
die beiden neuen Branches, ohne Force-Push. Alte Canonical-Referenzen bleiben stehen.

Cherry-pick war konfliktfrei. `git range-diff` zeigt ausschließlich abweichenden
Kontext um dieselben zwei Test-Abhängigkeiten in `build.gradle.kts`; Feature-Code,
Permissions, Tests und allgemeine Dokumentation sind identisch. Das aktuelle
Upstream-main wurde nicht in die Canonical-Basis gemergt.

## Modell und Grenzen

Exakte Pfadzuordnung, Default-Matrix, Administrations- und UI-Vertrag:
[Flexible curriculum permissions](flexible-curriculum-permissions.md).

Vier Gruppen: `curriculum_view`, `curriculum_manage_flexible`,
`curriculum_complete_flexible` jeweils Default `teacher`,
`curriculum_manage_central` Default `admin`. Alle drei Bearbeitungs-/Abschlussgruppen
benötigen `curriculum_view`. Teacher erhalten die ersten drei, Admin alle vier,
Student und Anonymous keine.

Die 13 neuen Backend-Pfade sind jeweils exakt einer Gruppe zugeordnet; direkter
Abgleich gegen beide angegebenen Backend-Commits bestätigte vollständige Abdeckung
und übereinstimmende Default-Level. Keine neuen PUBLIC-/USER-Curriculum-Pfade.
Zusätzlich liegt die bestehende GET-Selbstabfrage `/get-permissions` in
`curriculum_view`. Auf Canonical ist sie bereits durch `core_user_compat` verfügbar;
diese bestehende alternative Freigabe bleibt erhalten. Sie ist kein neuer
Curriculum-Pfad und gewährt keine Administrationsrechte.

`require_dependencies: true` aktiviert die zuvor nicht durchgesetzten `depends`
nur für die neuen Gruppen. Fehlende Voraussetzungen/Zyklen führen zu keiner
Freigabe, ohne rekursive Node-Erzeugung oder automatische Reaktivierung.
Bestehende Gruppen behalten ihre bisherige Abhängigkeitssemantik.

`default-roles.yaml`: **unverändert**. Default-Level reichen aus und vermeiden
zusätzliche Rollenzuweisungen, die direkte Deaktivierungen überlagern könnten.
Rollen bleiben additive Grants: Bei einer individuell konfigurierten Rolle muss
auch deren Grant entfernt werden, wenn effektiver Zugriff entzogen werden soll.
Eine inaktive direkte Permission ist weiterhin kein rollenübergreifendes Deny.

Keine neuen POST-Restrictions: PM prüft die Funktion; das Backend prüft
Session/Teacher-ID, frische Klassen- und Fachzuweisungen, Fach, Klasse, Halbjahr,
Ownership, Schülerkontext und das 105-Münzen-Limit. PM implementiert keine zweite
Scope- oder Budget-Engine.

## Tests und Artefakte

| Prüfung | Upstream | Canonical v2 |
|---|---:|---:|
| Bestehende Java-Tests | 4 bestanden | 11 bestanden |
| Neue Java-Tests | 8 bestanden | 8 bestanden |
| Java gesamt | 12, keine Fehler/Skips | 19, keine Fehler/Skips |
| Ausführbare JavaScript-Vertragstests | 3 bestanden | 3 bestanden |
| Plugin-JAR und Sources-JAR | gebaut | gebaut |

Befehle: `./gradlew test build` (Upstream),
`./gradlew test build --offline` (beide),
`node --test src/test/js/*.test.cjs` (beide, Node 22.14.0).
Die Test-Suites liefen tatsächlich; keine alleinige Wertung eines UP-TO-DATE-Laufs.

Zusätzlich Canonical gegen die bereits vorhandene lokale Sprint-1-Backend-Fat-JAR
`student-database-v2.0.0-fat.jar` getestet, über den bereits in der Basis
vorhandenen Gradle-Parameter `-PstudentDatabaseJar=...`: 19 Java-Tests und Build
bestanden. Ein erster Offline-Versuch konnte Gson 2.13.1 nicht aus dem Cache
auflösen; der reguläre Lauf lud diese öffentliche Abhängigkeit und bestand.
Das Backend wurde dafür nicht gebaut oder verändert. Die zusätzliche Prüfung
belegt PM-Kompatibilität mit dieser lokalen JAR, keinen deployten Ende-zu-Ende-Test.

Die neuen SQLite-Tests verwenden ausschließlich eine synthetische In-Memory-DB.
Sie testen tatsächlichen Config-Loader, SQL-Ressourcen, PermissionNode-Lifecycle
und UserEffect-Auswertung mit gemockten Benutzer-/Servergrenzen:

- vollständige Pfadzuordnung, Default-Matrix und GET-/POST-Entscheidungen;
- zweite Initialisierung und simulierter Kaltstart mit geleerten Caches/Registries;
- unveränderte Anzahl Permission-Definitionen und Benutzer-Nodes;
- persistierte manuelle false- **und** true-Werte bleiben erhalten;
- Entzug von view sperrt abhängige Funktionen ohne Umschreiben ihrer Nodes;
- aktive/inaktive Rollen, additive Grants, Deduplizierung;
- fehlende Voraussetzungen, Zyklen und umgekehrte Auswertungsreihenfolge;
- bestehende flache Pfade behalten ihre wirksamen Rollengrenzen.

Generische und flache Bestandsdefinitionen wurden zusätzlich vollständig als
geparstes JSON gegen die jeweilige Basis verglichen: identisch, nachdem nur die
vier neuen Gruppen entfernt wurden. Auf Canonical betrifft dies ausdrücklich
Core-Compat, Arcanum, Attendance einschließlich Signage, Results und
`permission_manager_admin_compat`. Vorhandene Canonical-Regressionstests bestehen.
`git diff --check` besteht. SQL und PermissionNode/RoleNode-Produktionscode wurden
nicht geändert. Zwei Test-Abhängigkeiten ergänzen den Zugriff auf den vorhandenen
Plugin- und Logger-Typ; keine Produktionsabhängigkeit wurde geändert.

Getrennte lokale Artefakte und XML-Testergebnisse liegen im ignorierten
`build/sprint2/upstream/` und `build/sprint2/canonical-v2/`. Kein Artefakt wurde in
eine Runtime kopiert. Das Projekt hat keinen Shadow-JAR-Task; keine Veröffentlichung
oder Signierung wurde ausgeführt. Übliche JVM-Warnung zum Mockito-Class-Sharing,
keine Testfehler.

## Geänderte Dateien

Beide Feature-Commits:

- `build.gradle.kts`
- `docs/flexible-curriculum-permissions.md`
- `src/main/java/de/igslandstuhl/database/permissions/PermissionEffect.java`
- `src/main/java/de/igslandstuhl/database/permissions/UserEffect.java`
- `src/main/java/de/igslandstuhl/database/permissions/meta/PermissionsConfigLoader.java`
- `src/main/resources/meta/permission-manager/permissions.json`
- `src/test/java/de/igslandstuhl/database/permissions/CurriculumPermissionTest.java`
- `src/test/js/curriculum-permissions.test.cjs`

Nur Canonical zusätzlich: dieser Integrationsbericht.

## UI-Lücke, Results/Overlay und Sprint 3

**UI-Lücke vorhanden:** Sprint 1 rendert flexible Erstell-/Bearbeitungsformulare
unabhängig von den PM-Permissions; zentrale Controls prüfen nur `catalog.admin`.
Die bestehende PM-JavaScript-API kann effektive Berechtigungen bereits liefern;
deren Laden, Warten, Aktualisieren und Fehlerverhalten sind ausführbar getestet.
Die Requests werden nach effektivem Rechteentzug durch PM blockiert. Die
Button-Steuerung und hilfreiche Darstellung von Zugriffsfehlern benötigen einen
separaten Frontend-Commit in student-database; hier wurde dort nichts verändert.

Results/Overlay: keine Änderungen an ihren Rechten oder Komponenten. Sie verwenden
flexible Fortschritte damit noch nicht automatisch. Für die Integration muss
expliziter Teacher-/Klassen-/Halbjahreskontext erhalten bleiben; Werte verschiedener
Lehrkräfte nicht zu einem kontextlosen Gesamtbudget addieren und Cache-Invalidierung
bei Definitionsänderung berücksichtigen. Keine Award-Snapshots einführen.

Empfehlung Sprint 3: genau ein Zielrepository wählen, zunächst `student-database`
für den Standard-UI-Permission-Vertrag samt DOM-Tests für entzogene view/manage/
complete/central-Rechte und verständliche Fehler. Results und Overlay danach in
jeweils eigenen Sprints auf den scoped Progress-Vertrag vorbereiten. Das vollständige
Komponentenpaket erst nach separater Freigabe integrieren und deployen.
