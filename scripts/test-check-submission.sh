#!/usr/bin/env bash
# End-to-end tests for the git hooks in .githooks/ and scripts/check-submission.sh.
#
#   scripts/test-check-submission.sh       run every case
#   scripts/test-check-submission.sh -v    also show each hook's output
#
# Builds a throwaway repo (plus a bare "remote") in a temp dir, wires it to a
# copy of this repo's hooks, and asserts which commits and pushes get through.
# Uses stand-in names, never the real forbidden-names list. Never touches this
# repository's own history or config.
set -uo pipefail

src=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# Isolate from the user's git config (hooksPath, signing, templates...).
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_SYSTEM=/dev/null GIT_CONFIG_NOSYSTEM=1
export GIT_AUTHOR_NAME=test GIT_AUTHOR_EMAIL=test@example.com
export GIT_COMMITTER_NAME=test GIT_COMMITTER_EMAIL=test@example.com

printf 'zorblax\nacme[[:space:]_-]*widgets\n' >"$work/names"
export FORBIDDEN_NAMES_FILE=$work/names

git init -q --bare -b main "$work/remote.git"
git init -q -b main "$work/repo"
cd "$work/repo" || exit 1
mkdir -p scripts
cp "$src/scripts/check-submission.sh" scripts/
cp -r "$src/.githooks" .
git config core.hooksPath .githooks
git remote add origin "$work/remote.git"

verbose=0
[ "${1-}" = "-v" ] && verbose=1
pass=0
fail=0
expect() { # expect <ok|reject> <label> <command...>
  local want=$1 label=$2 got
  shift 2
  if "$@" >"$work/out" 2>&1; then got=ok; else got=reject; fi
  if [ "$got" = "$want" ]; then
    pass=$((pass + 1)); printf '  ok  want=%-6s %s\n' "$want" "$label"
    [ "$verbose" -eq 1 ] && sed 's/^/        /' "$work/out"
  else
    fail=$((fail + 1)); printf 'FAIL  want=%-6s got=%-6s %s\n' "$want" "$got" "$label"
    sed 's/^/        /' "$work/out"
  fi
}
# Stage and commit; on rejection, unstage so the next case starts clean.
try_commit() { # try_commit <message> <file> [content]
  mkdir -p -- "$(dirname -- "$2")"
  printf '%s\n' "${3-x}" >"$2"
  git add -f -- "$2" && git commit -q -m "$1" && return 0
  git rm -q --cached -f -- "$2" >/dev/null 2>&1; rm -f -- "$2"
  return 1
}

echo "== pre-commit / commit-msg =="
git add -A
expect ok     "initial commit of the hooks"           git commit -q -m "Add submission hooks"
expect ok     "ordinary file"                         try_commit "Add app" App.java "class App {}"
expect reject "the brief by name"                     try_commit "Add brief" AI_TASK.md
expect reject "the dump by name, any case/dir"        try_commit "Add dump" sub/Dump.SQL
expect reject "dump rows under another name" \
  try_commit "Seed" V2__seed.sql "$(printf '2025-04-02 01:52:29.614\t1\t2025-04-02\t0\t93\t2025-10-22 18:36:10.548\tf6245760-3da5-46aa-bbe8-fe72b79b9797\tSTARTED\tFATIGUE\tPROGRAMME')"
expect reject "forbidden name in content"             try_commit "Add notes" notes.md "Thanks, Acme-Widgets"
expect reject "forbidden name in path"                try_commit "Add notes" zorblax.md
expect reject "forbidden name in message"             try_commit "For ZORBLAX" ok.md
expect ok     "comment lines in message are ignored" \
  bash -c 'printf "x\n" > c.md && git add c.md && printf "Add c\n# zorblax\n" > ../msg && git commit -q -F ../msg --cleanup=strip'

echo "== pre-push =="
expect ok     "first push"                            git push -q origin main
expect ok     "fast-forward push"                     bash -c 'printf "y\n" > d.md && git add d.md && git commit -q -m "Add d" && git push -q origin main'
git commit -q --no-verify -m "Sneak" --allow-empty
printf 'zorblax\n' >bad.md && git add bad.md && git commit -q --no-verify -m "Sneak bad file"
git rm -q bad.md && git commit -q -m "Remove bad file"
expect reject "bad file committed with --no-verify, then deleted" git push -q origin main
git reset -q --hard origin/main
expect ok     "clean again after dropping those commits" git push -q origin main
git commit -q --amend -m "Add d (reworded)"
expect reject "non-fast-forward (amend + force)"      git push -q --force origin main
git reset -q --hard origin/main
git push -q origin main:feature 2>/dev/null
expect reject "deleting a remote branch"              git push -q origin --delete feature

echo "== fails closed =="
export FORBIDDEN_NAMES_FILE=$work/missing
expect reject "names file missing"                    try_commit "Add e" e.md

echo
printf 'passed: %d   failed: %d\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
