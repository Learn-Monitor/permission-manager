const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {test} = require('node:test');
const {JSDOM} = require('jsdom');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/js/site/student-database.js'), 'utf8');

function client() {
    const dom = new JSDOM('<!doctype html><div id="target"></div>');
    let saved = [];
    const context = vm.createContext({
        document: dom.window.document,
        console,
        getJson: async () => [],
        getJsonWithPost: async url => url === '/get-permission-node'
            ? {override: 'inherit'} : {classification: 'NO_OVERRIDE', securityConflict: false},
        post: async (url, body) => { saved.push({url, body}); return {ok: false, status: 400}; },
    });
    vm.runInContext(source, context);
    return {dom, context, saved};
}

test('permission controls expose the inherited, allowed and revoked states', async () => {
    const {dom, context} = client();
    const target = dom.window.document.getElementById('target');
    await context.loadIndividualPermissionControls(target, [{name: 'synthetic_permission'}], ['synthetic@example.invalid']);
    const state = target.querySelector('select[aria-label="Individueller Zustand"]');
    assert.deepEqual([...state.options].map(option => option.value), ['inherit', 'allow', 'deny']);
    assert.equal(state.value, 'inherit');
    assert.match(target.textContent, /Standard folgt der aktuellen Rolle/);
    assert.match(target.textContent, /Standard \/ vererbt/);
    assert.doesNotMatch(target.textContent, /NO_OVERRIDE/);
});

test('a rejected role change does not visually move the selected assignment', async () => {
    const {dom, context} = client();
    const container = context.createDualList('synthetic');
    dom.window.document.body.append(container);
    const from = container.querySelector('select.available');
    const to = container.querySelector('select.selected');
    const option = dom.window.document.createElement('option');
    option.value = 'synthetic'; option.textContent = 'synthetic'; option.selected = true;
    option.toggleCallback = async () => ({ok: false, status: 400});
    from.append(option); option.selected = true;
    await context.moveSelected(from, to);
    assert.equal(from.contains(option), true);
    assert.equal(to.contains(option), false);
    assert.match(container.querySelector('[data-role-error]').textContent, /abgelehnt/);
});
