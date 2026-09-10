const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const {readFileSync} = require('node:fs');
const {resolve} = require('node:path');

const source = readFileSync(resolve(__dirname, '../../main/resources/js/site/student-database.js'), 'utf8');
function client(getJson) {
    const context = vm.createContext({getJson, console: {error() {}}});
    vm.runInContext(source, context);
    return context;
}

test('hasPermission waits for the current effective curriculum permissions', async () => {
    let finish;
    const context = client(path => {
        assert.equal(path, '/get-permissions');
        return new Promise(resolve => { finish = resolve; });
    });
    const viewing = context.hasPermission('curriculum_view');
    const editing = context.hasPermission('curriculum_manage_flexible');
    finish([{name: 'curriculum_view'}, {name: 'curriculum_complete_flexible'}]);
    assert.equal(await viewing, true);
    assert.equal(await editing, false);
    assert.equal(await context.hasPermission('curriculum_complete_flexible'), true);
    assert.equal(await context.hasPermission('curriculum_manage_central'), false);
});

test('all admin areas can be exposed and a refresh reflects revoked permissions', async () => {
    let permissions = ['curriculum_view', 'curriculum_manage_flexible',
        'curriculum_complete_flexible', 'curriculum_manage_central'];
    const context = client(async () => permissions.map(name => ({name})));
    for (const permission of permissions) assert.equal(await context.hasPermission(permission), true);
    permissions = [];
    await context.loadCurrentPermissions();
    assert.equal(await context.hasPermission('curriculum_view'), false);
    assert.equal(await context.hasPermission('curriculum_manage_flexible'), false);
});

test('an unavailable self-permission endpoint fails closed in the UI helper', async () => {
    const context = client(async () => { throw Error('Forbidden'); });
    assert.equal(await context.hasPermission('curriculum_view'), false);
    assert.equal(await context.hasPermission('curriculum_manage_flexible'), false);
});

test('context capability matches the standard UI without implying central editing or view', async () => {
    const context = client(async () => [{name: 'curriculum_assign_context'}]);
    assert.equal(await context.hasPermission('curriculum_assign_context'), true);
    assert.equal(await context.hasPermission('curriculum_manage_central'), false);
    assert.equal(await context.hasPermission('curriculum_view'), false);
    assert.equal(await context.hasPermission('curriculum_student_progress'), false);
});

test('student own progress is independent of teacher view and refresh fails closed', async () => {
    let failed = false;
    const context = client(async () => {
        if (failed) throw Error('Unavailable');
        return [{name: 'curriculum_student_progress'}];
    });
    assert.equal(await context.hasPermission('curriculum_student_progress'), true);
    assert.equal(await context.hasPermission('curriculum_view'), false);
    assert.equal(await context.hasPermission('curriculum_assign_context'), false);
    failed = true;
    await context.loadCurrentPermissions();
    assert.equal(await context.hasPermission('curriculum_student_progress'), false);
});
