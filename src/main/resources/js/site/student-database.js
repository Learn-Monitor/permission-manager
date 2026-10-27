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
function moveSelected(from, to) {
    [...from.selectedOptions].forEach(option => {
        to.appendChild(option);
        if (option.toggleCallback) option.toggleCallback();
    });
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
                togglePermissionForRole(p.name, role.name);
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
                toggleRoleForUser(user_name, role.name);
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
    section.append(createTextElement('p', 'Bearbeitungsfunktion: genau eine Auswahl. Orga-Leitung und Schulleitung schließen sich aus; beide Bearbeitungsrechte enthalten automatisch das Antragsrecht.'));
    const table = document.createElement('table');
    const head = document.createElement('tr');
    ['Konto', 'Anträge stellen', 'Bearbeitungsfunktion'].forEach(text => head.append(createTextElement('th', text)));
    table.append(head);
    (rights || []).forEach(item => {
        const row = document.createElement('tr');
        row.append(createTextElement('td', item.username));
        const applyCell = document.createElement('td');
        const apply = document.createElement('input');
        apply.type = 'checkbox'; apply.checked = !!item.apply; apply.setAttribute('aria-label', `${item.username}: Anträge stellen`);
        apply.addEventListener('change', async () => {
            if (item.orga || item.sl) { apply.checked = true; return; }
            await post('/set-freistellung-right', { user: item.username, right: apply.checked ? 'apply' : 'none' });
        });
        applyCell.append(apply); row.append(applyCell);
        const functionCell = document.createElement('td');
        [['none', 'keine'], ['orga', 'Orga-Leitung'], ['sl', 'Schulleitung']].forEach(([value, label]) => {
            const radio = document.createElement('input'); radio.type = 'radio'; radio.name = `freistellung-${item.username}`; radio.value = value;
            radio.checked = value === (item.orga ? 'orga' : item.sl ? 'sl' : 'none');
            radio.addEventListener('change', async () => {
                const response = await post('/set-freistellung-right', { user: item.username, right: value });
                if (!response.ok) { await loadFreistellungRights(rolesContainer); }
                else { apply.checked = value !== 'none'; }
            });
            const labelNode = document.createElement('label'); labelNode.append(radio, document.createTextNode(` ${label} `)); functionCell.append(labelNode);
        });
        row.append(functionCell); table.append(row);
    });
    section.append(table); rolesContainer.prepend(section);
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
