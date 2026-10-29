let currentPermissions = [];

async function fetchCurrentPermissions() {
    return await getJson('/get-permissions');
}

async function loadCurrentPermissions() {
    try {
        currentPermissions = await fetchCurrentPermissions();
    } catch (error) {
        console.error('Failed to load current user permissions', error);
        currentPermissions = [];
    }
    return currentPermissions;
}

const permissionsLoaded = loadCurrentPermissions();

async function hasPermission(permission) {
    await permissionsLoaded;
    return currentPermissions.some((p) => p.name === permission);
}

async function fetchPermissions() {
    return await getJson('/list-permissions');
}
async function fetchRoles() {
    return await getJson('/list-roles');
}
async function fetchUsers() {
    return await getJson('/list-users');
}
async function fetchRole(roleName) {
    return await getJsonWithPost('/get-role', { name: roleName });
}
async function fetchRoleNode(roleName, user) {
    return await getJsonWithPost('/get-role-node', { user, role: roleName });
}
async function toggleRoleForUser(user, role) {
    return await post('/toggle-role', {user, role});
}
async function togglePermissionForRole(permission, roleName) {
    return await post('/toggle-role-permission', { permission, role: roleName });
}
async function deleteRole(roleName) {
    const res = await post('/delete-role', { role: roleName });
    window.location.reload();
    return res;
}
let role_panels = {}
// For the dual list
async function moveSelected(from, to) {
    const container = from.closest('.dlcontainer');
    let message = container.querySelector('[data-role-error]');
    if (!message) { message = document.createElement('p'); message.dataset.roleError = 'true'; message.setAttribute('role', 'alert'); container.append(message); }
    message.textContent = '';
    for (const option of [...from.selectedOptions]) {
        try {
            const response = option.toggleCallback ? await option.toggleCallback() : null;
            if (response && !response.ok) throw new Error(`HTTP ${response.status}`);
            to.appendChild(option);
        } catch (_) { message.textContent = 'Änderung abgelehnt. Die bisherige Zuordnung bleibt erhalten.'; }
    }
}

function createTextElement(tagName, text) {
    const element = document.createElement(tagName);
    element.textContent = text ?? '';
    return element;
}

function createDualList(selectClass) {
    const container = document.createElement('div');
    container.className = 'dlcontainer';

    const availableList = document.createElement('div');
    availableList.className = 'dlist';
    availableList.append(
        createTextElement('h5', 'Verfügbar'),
        Object.assign(document.createElement('select'), {
            className: `available ${selectClass}`,
            multiple: true,
            size: 12
        })
    );

    const buttons = document.createElement('div');
    buttons.className = 'dlbuttons';
    buttons.append(
        Object.assign(createTextElement('button', '>'), {type: 'button', className: 'to-right'}),
        Object.assign(createTextElement('button', '<'), {type: 'button', className: 'to-left'})
    );

    const selectedList = document.createElement('div');
    selectedList.className = 'dlist';
    selectedList.append(
        createTextElement('h5', 'Ausgewählt'),
        Object.assign(document.createElement('select'), {
            className: `selected ${selectClass}`,
            multiple: true,
            size: 12
        })
    );

    container.append(availableList, buttons, selectedList);
    return container;
}

function registerDualListHandlers(container) {
    const available = container.querySelector('.available');
    const selected = container.querySelector('.selected');
    container.querySelector('.to-right').addEventListener('click', () => {
        moveSelected(available, selected);
    });
    container.querySelector('.to-left').addEventListener('click', () => {
        moveSelected(selected, available);
    });
    available.addEventListener('dblclick', () => {
        moveSelected(available, selected);
    });
    selected.addEventListener('dblclick', () => {
        moveSelected(selected, available);
    });
}

function loadRoleSection(role, permissions, users) {
    return createPanel(role.name, document.createElement("div"), async (header, body) => {
        header.textContent = role.name;
        const description = createTextElement('p', role.description);
        const permissionHeading = createTextElement('h4', 'Zugriffsberechtigungen');
        const permissionLists = createDualList('permission-select');
        const userHeading = createTextElement('h4', 'Zugewiesene Nutzer');
        const userLists = createDualList('user-select');
        const deleteButton = createTextElement('button', 'Rolle löschen');
        deleteButton.type = 'button';
        deleteButton.dataset.action = 'delete-role';
        deleteButton.dataset.role = role.name;
        deleteButton.addEventListener('click', () => deleteRole(deleteButton.dataset.role));
        body.replaceChildren(description, permissionHeading, permissionLists, userHeading, userLists, deleteButton);

        const permission_selects = Array.from(body.getElementsByClassName('permission-select'));
        permissions.forEach((p) => {
            if (['freistellung_apply', 'freistellung_orga', 'freistellung_sl'].includes(p.name)) return;
            const perm_option = document.createElement('option');
            perm_option.textContent = p.name;
            perm_option.setAttribute('title', p.description);
            perm_option.value = p.name;
            perm_option.toggleCallback = () => {
                return togglePermissionForRole(p.name, role.name);
            }
            if (role.permissions.some((p1) => p1.name == p.name)) {
                permission_selects[1].appendChild(perm_option);
            } else {
                permission_selects[0].appendChild(perm_option);
            }
        });
        const user_selects = Array.from(body.getElementsByClassName('user-select'));
        users.forEach(async (user_name) => {
            const user_option = document.createElement('option');
            user_option.textContent = user_name;
            user_option.value = user_name;
            user_option.toggleCallback = () => {
                return toggleRoleForUser(user_name, role.name);
            }
            const node = await fetchRoleNode(role.name, user_name);
            if (node && node.active) {
                user_selects[1].appendChild(user_option);
            } else {
                user_selects[0].appendChild(user_option);
            }
        });
        body.querySelectorAll('.dlcontainer').forEach(registerDualListHandlers);
    })
}
async function loadFreistellungRights(rolesContainer) {
    const rights = await post('/get-freistellung-rights', {});
    const section = document.createElement('section');
    section.className = 'freistellung-rights';
    section.append(createTextElement('h2', 'Freistellungsrechte'));
    section.append(createTextElement('p', 'Lehrkraft ist fest. Admin, Orga-Leitung und Schulleitung sind zusätzliche Organisationsrollen; Orga-Leitung und Schulleitung schließen sich aus.'));
    const table = document.createElement('table');
    const head = document.createElement('tr');
    ['Konto', 'Grundrolle', 'Admin', 'Anträge stellen', 'Bearbeitungsfunktion'].forEach(text => head.append(createTextElement('th', text)));
    table.append(head);
    (rights || []).forEach(item => {
        const row = document.createElement('tr');
        row.append(createTextElement('td', item.username));
        row.append(createTextElement('td', item.teacher ? 'Lehrkraft fest' : 'Schüler'));
        const adminCell = document.createElement('td');
        if (item.teacher) {
            const admin = document.createElement('input'); admin.type = 'checkbox'; admin.checked = !!item.admin; admin.setAttribute('aria-label', `${item.username}: Admin`);
            const adminError = createTextElement('span', ''); adminError.setAttribute('role', 'alert');
            admin.addEventListener('change', async () => {
                try { const response = await post('/set-admin-role', {user: item.username, enabled: admin.checked}); if (!response.ok) throw new Error(`HTTP ${response.status}`); adminError.textContent = ''; }
                catch (_) { admin.checked = !admin.checked; adminError.textContent = 'Änderung abgelehnt.'; }
            });
            adminCell.append(admin, adminError);
        } else adminCell.textContent = '—';
        row.append(adminCell);
        const applyCell = document.createElement('td');
        const apply = document.createElement('input');
        const applyError = createTextElement('span', ''); applyError.setAttribute('role', 'alert');
        apply.type = 'checkbox'; apply.checked = !!item.apply; apply.setAttribute('aria-label', `${item.username}: Anträge stellen`);
        apply.addEventListener('change', async () => {
            if (item.orga || item.sl) { apply.checked = true; return; }
            try {
                const response = await post('/set-freistellung-right', { user: item.username, right: apply.checked ? 'apply' : 'none' });
                if (!response.ok) throw new Error(`HTTP ${response.status}`);
                applyError.textContent = '';
            } catch (_) { apply.checked = !apply.checked; applyError.textContent = 'Änderung abgelehnt.'; }
        });
        applyCell.append(apply, applyError); row.append(applyCell);
        const functionCell = document.createElement('td');
        if (item.securityConflict) functionCell.append(createTextElement('p', 'Sicherheitskonflikt: Orga und Schulleitung sind bis zur Auflösung gesperrt.'));
        const functionError = createTextElement('span', ''); functionError.setAttribute('role', 'alert');
        const previousFunction = item.orga ? 'orga' : item.sl ? 'sl' : 'none';
        [['none', 'keine'], ['orga', 'Orga-Leitung'], ['sl', 'Schulleitung']].forEach(([value, label]) => {
            const radio = document.createElement('input'); radio.type = 'radio'; radio.name = `freistellung-${item.username}`; radio.value = value;
            radio.checked = value === previousFunction;
            radio.addEventListener('change', async () => {
                try {
                    const response = await post('/set-freistellung-right', { user: item.username, right: value });
                    if (!response.ok) throw new Error(`HTTP ${response.status}`);
                    section.remove(); await loadFreistellungRights(rolesContainer);
                } catch (_) {
                    functionCell.querySelectorAll('input[type="radio"]').forEach(input => { input.checked = input.value === previousFunction; });
                    functionError.textContent = 'Änderung abgelehnt; gespeicherter Zustand unverändert.';
                }
            });
            const labelNode = document.createElement('label'); labelNode.append(radio, document.createTextNode(` ${label} `)); functionCell.append(labelNode);
        });
        functionCell.append(functionError); row.append(functionCell); table.append(row);
    });
    section.append(table); rolesContainer.prepend(section);
}
async function loadIndividualPermissionControls(container, permissions, users) {
    const section = document.createElement('section');
    section.append(createTextElement('h2', 'Individuelle Berechtigung'));
    section.append(createTextElement('p', 'Standard folgt der aktuellen Rolle. Individuell entzogen entfernt nur die direkte Freigabe; Rollen können weiterhin Zugriff gewähren.'));
    const user = document.createElement('select'); user.setAttribute('aria-label', 'Benutzer');
    const permission = document.createElement('select'); permission.setAttribute('aria-label', 'Berechtigung');
    const state = document.createElement('select'); state.setAttribute('aria-label', 'Individueller Zustand');
    for (const value of users) { const option = createTextElement('option', value); option.value = value; user.append(option); }
    for (const value of permissions) { const option = createTextElement('option', value.name); option.value = value.name; permission.append(option); }
    for (const [value, label] of [['inherit', 'Standard / vererbt'], ['allow', 'Individuell erlaubt'], ['deny', 'Individuell entzogen']]) {
        const option = createTextElement('option', label); option.value = value; state.append(option);
    }
    const message = createTextElement('p', ''); message.setAttribute('role', 'status');
    const save = createTextElement('button', 'Berechtigung speichern'); save.type = 'button';
    async function refresh() {
        if (!user.value || !permission.value) return;
        try {
            const node = await getJsonWithPost('/get-permission-node', {user: user.value, permission: permission.value});
            const audit = await getJsonWithPost('/permission-override-audit', {user: user.value, permission: permission.value});
            state.value = node.override;
            const descriptions = {
                NO_OVERRIDE: 'Standard / vererbt',
                LEGACY_MATCH: 'Individuelle Einstellung entspricht dem Standard',
                LEGACY_RESTRICTIVE_CONFLICT: 'Individuelle Einstellung schränkt den Standard ein',
                LEGACY_PERMISSIVE_CONFLICT: 'Individuelle Einstellung erlaubt mehr als der Standard',
            };
            const describeSource = source => source === 'DIRECT_OR_LEGACY_OVERRIDE' ? 'individuelle oder frühere Zuweisung'
                : source === 'INHERITED_DEFAULT' ? 'Standard' : source.startsWith('ROLE:') ? `Rolle: ${source.slice(5)}` : source;
            message.textContent = `${descriptions[audit.classification] || 'Berechtigungszustand unbekannt'}${audit.securityConflict
                ? ` — Sicherheitskonflikt: Orga und Schulleitung gesperrt. Orga-Quellen: ${(audit.orgaSources || []).map(describeSource).join(', ')}. SL-Quellen: ${(audit.slSources || []).map(describeSource).join(', ')}.`
                : ''}${audit.historicalOrgaRow || audit.historicalSlRow
                ? ' — Historischer Rolleneintrag vorhanden; er wird nicht automatisch als Recht übernommen.' : ''}`;
        } catch (_) { message.textContent = 'Berechtigungszustand konnte nicht geladen werden.'; }
    }
    user.addEventListener('change', refresh); permission.addEventListener('change', refresh);
    save.addEventListener('click', async () => {
        try {
            const response = await post('/set-permission-override', {user: user.value, permission: permission.value, state: state.value});
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            await refresh();
            message.textContent += ' — gespeichert.';
        } catch (_) { await refresh(); message.textContent = 'Änderung abgelehnt. Der gespeicherte Zustand bleibt unverändert.'; }
    });
    section.append(user, permission, state, save, message); container.prepend(section); await refresh();
}
async function loadRolesView(rolesContainer) {
    const roles = fetchRoles();
    const permissions = fetchPermissions();
    const users = fetchUsers();
    (await roles).forEach(async role => {
        const roleSection = loadRoleSection(role, await permissions, await users);
        role_panels[role.name] = roleSection;
        rolesContainer.appendChild(roleSection);
    });
    try { await loadFreistellungRights(rolesContainer); } catch (error) { console.error('Failed to load Freistellung rights', error); }
    try { await loadIndividualPermissionControls(rolesContainer, await permissions, await users); } catch (error) { console.error('Failed to load permission overrides', error); }
}

function initializePermissionManager() {
    const rolesContainer = document.getElementById('roles');
    if (rolesContainer) loadRolesView(rolesContainer);
}

if (typeof document !== 'undefined') {
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initializePermissionManager);
    } else {
        initializePermissionManager();
    }
}
