#!/usr/bin/env python3
"""Read pinned backend Git objects; verify route coverage without checking out/building it."""
import argparse
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'src/test/resources/contracts/student-context-routes.json'
TRACKS = {
    'upstream': ('8dbe2b35f9d60c0309eb48b127a51c23b4109b39', '205f8d853c7f4011f722c621f8c306243a3bf23b'),
    'canonical-v2': ('f4f22abcdeff32c839cb21d61bbae7cd1f55940b', '739bbccadebabf2acc69911a273cfcda4f9bb665'),
}
PERMISSIONS = ROOT / 'src/main/resources/meta/permission-manager/permissions.json'
NEW_NAMES = {'curriculum_student_progress', 'curriculum_assign_context'}

def show(repo, commit, path):
    return subprocess.check_output(['git', '-C', str(repo), 'show', f'{commit}:{path}'], text=True)

def snapshot(backend):
    tracks = {}
    for name, (before, after) in TRACKS.items():
        track = {'beforeCommit': before, 'afterCommit': after}
        for label, commit in [('before', before), ('after', after)]:
            track[label] = {}
            for method in ('GET', 'POST'):
                path = f'src/main/resources/meta/paths/{method.lower()}_paths.json'
                routes = json.loads(show(backend, commit, path))
                assert all(route['type'] == method for route in routes.values())
                track[label][method] = {path: route['access_level'] for path, route in sorted(routes.items())}
        tracks[name] = track
    return tracks

def check(tracks):
    policy = json.loads(PERMISSIONS.read_text())
    all_permissions = policy['flat'] + policy['generics']
    deltas = []
    for name, track in tracks.items():
        delta = {}
        for method in ('GET', 'POST'):
            before, after = track['before'][method], track['after'][method]
            assert set(before) <= set(after), 'Unexpected removed route'
            for path in after.keys() - before.keys():
                matches = [p for p in all_permissions if path in p['paths']]
                assert len(matches) == 1, (name, method, path, matches)
                permission = matches[0]
                assert permission['name'] in NEW_NAMES
                assert permission['default'] == after[path] in ('student', 'admin')
                assert permission['allowed_methods'] == [method]
                assert permission['depends'] == [] and permission['post_restrictions'] == []
                delta[method + ' ' + path] = permission['name']
        assert len(delta) == 5 and all(route.startswith('POST ') for route in delta)
        deltas.append(delta)
        print(name + ': ' + json.dumps(delta, sort_keys=True))
    assert deltas[0] == deltas[1]
    # Everything in the two new permissions must be a discovered backend route.
    expected = {route.split(' ', 1)[1] for route in deltas[0]}
    actual = {path for p in all_permissions if p['name'] in NEW_NAMES for path in p['paths']}
    assert actual == expected
    # Track-specific legacy configurations, including canonical plugin groups, remain byte/structure equivalent.
    canonical = subprocess.run(['git', '-C', str(ROOT), 'merge-base', '--is-ancestor',
                                'bde94e87e7d3bd4bf1ca42125322c4a276a65f77', 'HEAD'], capture_output=True).returncode == 0
    base = 'bde94e87e7d3bd4bf1ca42125322c4a276a65f77' if canonical else '12fc737db4f39801e34c787e06898a3e851fe119'
    old = json.loads(show(ROOT, base, str(PERMISSIONS.relative_to(ROOT))))
    policy['flat'] = [p for p in policy['flat'] if p['name'] not in NEW_NAMES]
    assert policy == old, 'Existing permission groups changed'
    roles = 'src/main/resources/meta/permission-manager/default-roles.yaml'
    assert (ROOT / roles).read_text() == show(ROOT, base, roles), 'Default roles changed'

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--backend', type=Path, required=True)
    parser.add_argument('--write-fixture', action='store_true')
    args = parser.parse_args()
    tracks = snapshot(args.backend)
    check(tracks)
    if args.write_fixture:
        FIXTURE.write_text(json.dumps(tracks, indent=2) + '\n')
    else:
        assert json.loads(FIXTURE.read_text()) == tracks, 'Pinned backend route fixture differs'
    print('Both backend tracks and unchanged legacy PM policies verified.')
