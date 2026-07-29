async function fetchPermissions() {
    return await getJson('/list-permissions');
}
async function fetchRoleNames() {
    return await getJson('/list-roles');
}
async function fetchRole(roleName) {
    return await getJsonWithPost('/get-role', { name: roleName });
}
async function togglePermissionForRole(permission, roleName) {
    return await post('/toggle-role-permission', { permission, role: roleName });
}
let role_panels = {}
// For the dual list
function moveSelected(from, to) {
    [...from.selectedOptions].forEach(option => {
        to.appendChild(option);
        const [roleName, permission] = option.value.split('.');
        togglePermissionForRole(permission, roleName);
    });
}
function loadRoleSection(roleName, permissions) {
    return createPanel(roleName, document.createElement("div"), async (header, body) => {
        const role = await fetchPlugin(roleName);
        header.textContent = roleName;
        body.innerHTML = `
<h2>Zugriffsberechtigungen</h2>

<div class="dlcontainer">
    <div class="dlist">
        <h3>Verfügbar</h3>
        <select class="available" multiple size="12"></select>
    </div>
    <div class="dlbuttons">
        <button class="to-right">&gt;</button>
        <button class="to-left">&lt;</button>
    </div>
    <div class="dlist">
        <h3>Ausgewählt</h3>
        <select class="selected" multiple size="12"></select>
    </div>
</div>
        `;
        const select = body.getElementsByClassName(available)[0];
        permissions.forEach((p) => {
            const perm_option = document.createElement('option');
            perm_option.textContent = p.name;
            perm_option.setAttribute('title', p.description);
            perm_option.value = roleName + "." + p.name;
        });
    })
}
async function loadPluginsView(rolesContainer) {
    const roleNames = fetchRoleNames();
    const permissions = fetchPermissions();
    (await roleNames).forEach(async roleName => {
        const pluginSection = loadRoleSection(roleName, permissions);
        role_panels[roleName] = pluginSection;
        rolesContainer.appendChild(pluginSection);
    });
    document.querySelectorAll(".dlcontainer").forEach(container => {
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
}