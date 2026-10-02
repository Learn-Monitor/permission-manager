const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const permissions = JSON.parse(fs.readFileSync(
  path.join(__dirname, '../../main/resources/meta/permission-manager/permissions.json'),
));
const publicAssets = permissions.flat.find(({name}) => name === 'arcanum_public_assets');
assert.ok(publicAssets, 'public asset permission exists');

for (const asset of [
  '/arcanum-login-background-3d41c61567504c9b.webp',
  '/arcanum-logo-b674493ab1b01691.webp',
  '/arcanum-stamp-b5ab61fed492b500.webp',
  '/arcanum-coin-a5c34f85b21b75d5.webp',
  '/arcanum-inspiration-05-dranbleiben-600e7ad17aac285a.webp',
]) assert.ok(publicAssets.paths.includes(asset), `public asset path: ${asset}`);

const resultsTeacher = permissions.flat.find(({name}) => name === 'results_teacher');
assert.ok(resultsTeacher, 'results teacher permission exists');
for (const asset of ['/arcanum-student.css', '/arcanum-student-theme.css']) {
  assert.ok(resultsTeacher.paths.includes(asset), `teacher results asset: ${asset}`);
}
assert.ok(resultsTeacher.paths.includes('/get-plugin'), 'teacher results config access');
const resultsStudent = permissions.flat.find(({name}) => name === 'results_student');
assert.ok(resultsStudent, 'results student permission exists');
assert.ok(resultsStudent.paths.includes('/get-plugin'), 'student results config access');

const css = fs.readFileSync(path.join(__dirname, '../../main/resources/css/site/style.css'), 'utf8');
assert.match(css, /#dashboard\s*\{[^}]*box-sizing:\s*border-box/);

console.log('A4c browser contract: PASS');
