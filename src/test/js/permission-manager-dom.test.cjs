const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {test} = require('node:test');

const source = fs.readFileSync(
  path.join(__dirname, '../../main/resources/js/site/student-database.js'),
  'utf8',
);

function loadClient() {
  const document = {
    readyState: 'loading',
    addEventListener() {},
    createElement() {
      return {textContent: ''};
    },
  };
  const context = vm.createContext({
    document,
    getJson: async () => [],
    console: {error() {}},
  });
  vm.runInContext(source, context);
  return context;
}

test('dynamic role text is assigned as text, including XSS-shaped values', () => {
  const context = loadClient();
  for (const value of ['<', '>', '&', '"', "'", '<img src=x onerror=alert(1)>']) {
    const element = context.createTextElement('p', value);
    assert.equal(element.textContent, value);
  }
});

test('role actions use data and event listeners instead of executable HTML', () => {
  assert.match(source, /deleteButton\.dataset\.action\s*=\s*['"]delete-role['"]/);
  assert.match(source, /deleteButton\.dataset\.role\s*=\s*role\.name/);
  assert.match(source, /deleteButton\.addEventListener\(['"]click['"]/);
  assert.doesNotMatch(source, /\bonclick\s*=|innerHTML|outerHTML|insertAdjacentHTML/);
});

