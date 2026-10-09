const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {test} = require('node:test');

const resources = path.join(__dirname, '../../main/resources');
const permissions = JSON.parse(fs.readFileSync(
  path.join(resources, 'meta/permission-manager/permissions.json'),
  'utf8',
));
const flat = permissions.flat;
const permission = name => flat.find(entry => entry.name === name);

test('teacher dashboard navigation is assigned only to the teacher compatibility permission', () => {
  const teacher = permission('core_teacher_compat');
  assert.ok(teacher);
  assert.ok(teacher.paths.includes('/teacher-dashboard-navigation.js'));
  assert.equal(teacher.default, 'teacher');
  assert.equal(permission('core_admin_compat').paths.includes('/teacher-dashboard-navigation.js'), false);
});

test('admin-only core routes remain outside teacher compatibility', () => {
  const teacher = permission('core_teacher_compat');
  for (const route of ['/teacher', '/class', '/subject']) {
    assert.equal(teacher.paths.includes(route), false, `${route} must remain admin-only`);
  }
});

test('results route split remains student versus teacher/admin', () => {
  assert.ok(permission('results_student').paths.includes('/results'));
  assert.ok(permission('results_teacher').paths.includes('/student-results'));
  assert.equal(permission('results_teacher').paths.includes('/results'), false);
});

test('attendance stylesheet is teacher-only', () => {
  const attendance = permission('attendance_teacher');
  assert.ok(attendance);
  assert.equal(attendance.default, 'teacher');
  assert.ok(attendance.paths.includes('/attendance.css'));
  assert.equal(permission('core_public_compat').paths.includes('/attendance.css'), false);
  assert.equal(permission('core_user_compat').paths.includes('/attendance.css'), false);
});

test('manage permissions relies on the global site student-database script', () => {
  const template = fs.readFileSync(
    path.join(resources, 'html/admin/manage_permissions.html'),
    'utf8',
  );
  assert.equal((template.match(/<script\b[^>]*src=["']\/student-database\.js["'][^>]*>/gi) || []).length, 0);
});
