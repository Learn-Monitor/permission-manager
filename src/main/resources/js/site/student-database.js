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
function loadRoleSection(role, permissions, users) {
    return createPanel(role.name, document.createElement("div"), async (header, body) => {
        header.textContent = role.name;
        body.innerHTML = `
<p>${role.description}</p>
<h3>Zugriffsberechtigungen</h2>

<div class="dlcontainer">
    <div class="dlist">
        <h4>Verfügbar</h3>
        <select class="available permission-select" multiple size="12"></select>
    </div>
    <div class="dlbuttons">
        <button class="to-right">&gt;</button>
        <button class="to-left">&lt;</button>
    </div>
    <div class="dlist">
        <h4>Ausgewählt</h3>
        <select class="selected permission-select" multiple size="12"></select>
    </div>
</div>

<h3>Zugewiesene Nutzer</h2>
<div class="dlcontainer">
    <div class="dlist">
        <h4>Verfügbar</h3>
        <select class="available user-select" multiple size="12"></select>
    </div>
    <div class="dlbuttons">
        <button class="to-right">&gt;</button>
        <button class="to-left">&lt;</button>
    </div>
    <div class="dlist">
        <h4>Ausgewählt</h3>
        <select class="selected user-select" multiple size="12"></select>
    </div>
</div>

<button onclick="deleteRole('${role.name}')">Rolle löschen</button>
        `;
        const permission_selects = Array.from(body.getElementsByClassName('permission-select'));
        permissions.forEach((p) => {
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
        body.querySelectorAll(".dlcontainer").forEach(container => {
            const available = container.querySelector(".available");
            const selected = container.querySelector(".selected");

            container.querySelector(".to-right").addEventListener("click", () => {
                moveSelected(available, selected);
            });

            container.querySelector(".to-left").addEventListener("click", () => {
                moveSelected(selected, available);
            });

            available.addEventListener("dblclick", () => {
                moveSelected(available, selected);
            });

            selected.addEventListener("dblclick", () => {
                moveSelected(selected, available);
            });
        });
    })
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
}