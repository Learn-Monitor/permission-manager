const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {test} = require('node:test');

const resources = path.join(__dirname, '../../main/resources');
const productionFiles = [];
for (const directory of ['html', 'js', 'css']) {
  const root = path.join(resources, directory);
  function collect(current) {
    for (const entry of fs.readdirSync(current, {withFileTypes: true})) {
      const file = path.join(current, entry.name);
      if (entry.isDirectory()) collect(file);
      else if (/\.(html?|js|css)$/.test(entry.name)) productionFiles.push(file);
    }
  }
  collect(root);
}

test('production resources are ready for strict CSP', () => {
  const contents = productionFiles.map(file => fs.readFileSync(file, 'utf8')).join('\n');
  const inlineScripts = contents.match(/<script\b(?![^>]*\bsrc\s*=)[^>]*>[\s\S]*?<\/script>/gi) || [];
  const inlineStylesheets = contents.match(/<style\b[^>]*>[\s\S]*?<\/style>/gi) || [];
  const inlineHandlers = contents.match(/\bon[a-z][\w-]*\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)/gi) || [];
  const styleAttributes = contents.match(/\bstyle\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)/gi) || [];

  assert.equal(inlineScripts.length, 0, `inline scripts: ${inlineScripts.join(', ')}`);
  assert.equal(inlineStylesheets.length, 0, `inline stylesheets: ${inlineStylesheets.join(', ')}`);
  assert.equal(inlineHandlers.length, 0, `inline handlers: ${inlineHandlers.join(', ')}`);
  assert.equal(styleAttributes.length, 0, `style attributes: ${styleAttributes.join(', ')}`);
  assert.doesNotMatch(contents, /\b(?:innerHTML|outerHTML|insertAdjacentHTML)\s*=/);
  assert.doesNotMatch(contents, /['"]unsafe-(?:inline|eval)['"]/);
});

