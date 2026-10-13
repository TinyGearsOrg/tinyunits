#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
#
# Statically checks the GitHub configuration (milestone 7, D6): the CI workflow builds and checks only (no deploy,
# no secrets, read-only token, pinned official actions) and runs the commands of CONTRIBUTING.md; dependabot.yml.
# Usage: scripts/check-workflows.sh [--self-test]
# Requires: bash, python3 with PyYAML.
# Exits 1 with a message on the first failed check; --self-test mutates copies and asserts each is detected.
set -euo pipefail

SCRIPT=$(cd "$(dirname "$0")" && pwd)/$(basename "$0")
ROOT=${CHECK_WORKFLOWS_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}

fail() { echo "check-workflows: FAIL: $*" >&2; exit 1; }

self_test() {
  local status out c name mutation expect
  tmp=$(mktemp -d)
  trap 'rm -rf "$tmp"' EXIT
  # name|python mutation of the copy|expected message fragment (no "|" inside a field)
  local cases=(
    "baseline||"
    "secret-reference|sub(CI, 'run: mvn -B verify', 'run: mvn -B verify -Dtoken=\${{ secrets.TOKEN }}')|ci.yml references secrets"
    "github-token|sub(CI, 'run: git diff --exit-code', 'run: echo \${{ github.token }}')|ci.yml references secrets"
    "pull-request-target|sub(CI, 'pull_request:', 'pull_request_target:')|triggers are ['pull_request_target', 'push'], expected ['pull_request', 'push']"
    "extra-trigger|sub(CI, 'pull_request:', 'pull_request:\n  workflow_dispatch:')|triggers are ['pull_request', 'push', 'workflow_dispatch']"
    "write-permission|sub(CI, 'contents: read', 'contents: write')|permissions are {'contents': 'write'}, expected {'contents': 'read'}"
    "job-permission|sub(CI, '    timeout-minutes: 20', '    timeout-minutes: 20\n    permissions: write-all')|job generated sets permissions"
    "deploy|sub(CI, 'run: mvn -B verify', 'run: mvn -B -Prelease deploy')|job build runs a forbidden command: 'mvn -B -Prelease deploy'"
    "release-profile|sub(CI, 'run: mvn -B verify', 'run: mvn -B -Prelease verify')|job build runs a forbidden command: 'mvn -B -Prelease verify'"
    "git-push|sub(CI, 'run: git diff --exit-code', 'run: git diff --exit-code && git push origin HEAD')|job generated runs a forbidden command"
    "gh-release|sub(CI, 'run: git diff --exit-code', 'run: git diff --exit-code\n      - run: gh release create v1')|job generated runs a forbidden command: 'gh release create v1'"
    "unpinned-action|sub(CI, 'actions/checkout@v7', 'actions/checkout@main')|action 'actions/checkout@main' is not an allowed action at a major tag"
    "third-party-action|sub(CI, 'actions/setup-java@v6', 'someone/setup-java@v6')|action 'someone/setup-java@v6' is not an allowed action at a major tag"
    "no-timeout|sub(CI, '    timeout-minutes: 45\n', '')|job release-check has no timeout-minutes"
    "no-concurrency|drop(CI, 'concurrency:', 'cancel-in-progress: true')|concurrency group must contain github.ref"
    "matrix|sub(CI, 'java: [21, 25]', 'java: [21]')|build matrix java is [21], expected [21, 25]"
    "distribution|sub(CI, 'distribution: temurin', 'distribution: zulu')|setup-java distribution is 'zulu'"
    "missing-job|drop(CI, '  generated:', 'run: git diff --exit-code\n')|jobs are ['build', 'release-check'], expected ['build', 'generated', 'release-check']"
    "no-require-jlink|sub(CI, 'verify-release.sh --require-jlink', 'verify-release.sh')|the run commands differ from the CI block of CONTRIBUTING.md"
    "contributing-drift|sub('CONTRIBUTING.md', 'mvn -B verify\n', 'mvn -B install\n')|the run commands differ from the CI block of CONTRIBUTING.md"
    "extra-workflow|add('.github/workflows/release.yml', 'name: release\n')|workflow files are ['ci.yml', 'release.yml'], expected ['ci.yml']"
    "yaml-error|sub(CI, 'permissions:', 'permissions: [')|ci.yml does not parse"
    "dependabot-ecosystem|drop(DB, '  - package-ecosystem: github-actions', 'patterns: [\"*\"]\n')|dependabot ecosystems are ['maven'], expected ['github-actions', 'maven']"
    "dependabot-interval|sub(DB, 'interval: weekly', 'interval: daily')|dependabot maven: interval is 'daily', expected 'weekly'"
  )
  for c in "${cases[@]}"; do
    IFS='|' read -r name mutation expect <<< "$c"
    rm -rf "$tmp/copy" && mkdir -p "$tmp/copy"
    cp -r "$ROOT/.github" "$ROOT/CONTRIBUTING.md" "$tmp/copy/"
    if [[ -n $mutation ]]; then
      python3 - "$tmp/copy" <<EOF
import sys, pathlib
root = pathlib.Path(sys.argv[1])
CI, DB = '.github/workflows/ci.yml', '.github/dependabot.yml'
def edit(f, fn):
    p = root / f; t = p.read_text(); n = fn(t)
    if n == t: sys.exit("self-test: mutation did not change " + f)
    p.write_text(n)
def drop(f, start, end):
    edit(f, lambda t: t[:t.index(start)] + t[t.index(end, t.index(start)) + len(end):])
def sub(f, old, new):
    edit(f, lambda t: t.replace(old, new, 1))
def add(f, text):
    (root / f).write_text(text)
$mutation
EOF
    fi
    set +e
    out=$(CHECK_WORKFLOWS_ROOT="$tmp/copy" bash "$SCRIPT" 2>&1)
    status=$?
    set -e
    if [[ $name == baseline ]]; then
      [[ $status -eq 0 ]] || { echo "$out"; fail "self-test: unmodified copy fails (exit $status)"; }
    else
      [[ $status -eq 1 ]] || { echo "$out"; fail "self-test $name: expected exit 1, got $status"; }
      [[ $out == *"$expect"* ]] || { echo "$out"; fail "self-test $name: message lacks '$expect'"; }
    fi
    echo "self-test $name: ok (exit $status)"
  done
  echo "check-workflows: self-test passed"
}

if [[ ${1:-} == --self-test ]]; then self_test; exit 0; fi
[[ $# -eq 0 ]] || fail "usage: check-workflows.sh [--self-test]"

python3 - "$ROOT" <<'EOF'
import pathlib, re, sys
try:
    import yaml
except ImportError:
    print("check-workflows: FAIL: python3 module yaml (PyYAML) not found", file=sys.stderr); sys.exit(1)

def fail(msg):
    print("check-workflows: FAIL: " + msg, file=sys.stderr); sys.exit(1)

root = pathlib.Path(sys.argv[1])
wf_dir = root / '.github/workflows'
files = sorted(p.name for p in wf_dir.iterdir()) if wf_dir.is_dir() else []
if files != ['ci.yml']:
    fail("workflow files are %s, expected ['ci.yml'] (no release or deploy workflow)" % files)

def load(rel):
    p = root / rel
    if not p.is_file():
        fail(rel + " is missing")
    text = p.read_text()
    try:
        return text, yaml.safe_load(text)
    except yaml.YAMLError as e:
        fail("%s does not parse: %s" % (p.name, str(e).splitlines()[0]))

text, ci = load('.github/workflows/ci.yml')
if not isinstance(ci, dict):
    fail("ci.yml is not a mapping")
if re.search(r'\bsecrets\b|github\.token|GITHUB_TOKEN', text):
    fail("ci.yml references secrets or the token")

# YAML 1.1 reads the key 'on' as true
on = ci.get('on', ci.get(True))
triggers = sorted(on) if isinstance(on, dict) else sorted([on] if isinstance(on, str) else on or [])
if triggers != ['pull_request', 'push']:
    fail("triggers are %s, expected ['pull_request', 'push']" % triggers)
if ci.get('permissions') != {'contents': 'read'}:
    fail("permissions are %s, expected {'contents': 'read'}" % ci.get('permissions'))
if '${{ github.ref }}' not in str((ci.get('concurrency') or {}).get('group', '')):
    fail("concurrency group must contain github.ref")

jobs = ci.get('jobs') or {}
if sorted(jobs) != ['build', 'generated', 'release-check']:
    fail("jobs are %s, expected ['build', 'generated', 'release-check']" % sorted(jobs))
FORBIDDEN = re.compile(r'\bdeploy\b|-Prelease|\bpublish|\bgpg\b|\bgit\s+(push|tag)\b|\bgh\s+release\b|\bcentral\b', re.I)
ACTION = re.compile(r'actions/(checkout|setup-java)@v\d+')
commands = []
for name, job in jobs.items():
    for key in ('permissions', 'environment', 'secrets', 'uses', 'container', 'services'):
        if key in job:
            fail("job %s sets %s" % (name, key))
    if not isinstance(job.get('timeout-minutes'), int) or job['timeout-minutes'] <= 0:
        fail("job %s has no timeout-minutes" % name)
    if job.get('runs-on') != 'ubuntu-latest':
        fail("job %s runs on %s, expected ubuntu-latest" % (name, job.get('runs-on')))
    for step in job.get('steps') or []:
        if 'uses' in step:
            if not ACTION.fullmatch(step['uses']):
                fail("action '%s' is not an allowed action at a major tag" % step['uses'])
            with_ = step.get('with') or {}
            if step['uses'].startswith('actions/checkout@') and with_.get('persist-credentials') is not False:
                fail("job %s: checkout must set persist-credentials: false" % name)
            if step['uses'].startswith('actions/setup-java@'):
                if with_.get('distribution') != 'temurin':
                    fail("job %s: setup-java distribution is '%s', expected 'temurin'" % (name, with_.get('distribution')))
                java = with_.get('java-version')
                if name != 'build' and str(java) != '21':
                    fail("job %s: java-version is %s, expected 21" % (name, java))
        for line in str(step.get('run', '')).splitlines():
            if line.strip():
                if FORBIDDEN.search(line):
                    fail("job %s runs a forbidden command: '%s'" % (name, line.strip()))
                commands.append(line.strip())
matrix = ((jobs['build'].get('strategy') or {}).get('matrix') or {}).get('java')
if matrix != [21, 25]:
    fail("build matrix java is %s, expected [21, 25]" % matrix)

# the fenced block after "run what the CI runs" in CONTRIBUTING.md, in job order
contrib = (root / 'CONTRIBUTING.md').read_text()
m = re.search(r'run what the CI runs.*?\n```\n(.*?)```', contrib, re.S)
documented = [l.strip() for l in m.group(1).splitlines() if l.strip()] if m else []
if commands != documented:
    fail("the run commands differ from the CI block of CONTRIBUTING.md:\n  ci.yml:       %s\n  CONTRIBUTING: %s"
         % (commands, documented))

text, db = load('.github/dependabot.yml')
if not isinstance(db, dict) or db.get('version') != 2:
    fail("dependabot.yml: version must be 2")
updates = {u.get('package-ecosystem'): u for u in db.get('updates') or []}
if sorted(updates) != ['github-actions', 'maven']:
    fail("dependabot ecosystems are %s, expected ['github-actions', 'maven']" % sorted(updates))
for eco, u in sorted(updates.items()):
    interval = (u.get('schedule') or {}).get('interval')
    if interval != 'weekly':
        fail("dependabot %s: interval is '%s', expected 'weekly'" % (eco, interval))
    if u.get('directory') != '/' or not u.get('groups') or not isinstance(u.get('open-pull-requests-limit'), int):
        fail("dependabot %s: needs directory '/', groups and open-pull-requests-limit" % eco)
print("check-workflows: OK")
EOF
