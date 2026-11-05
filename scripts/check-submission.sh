#!/usr/bin/env bash
# Enforces the submission rules on what goes into this repository:
#
#   1. The brief and the supplied database dump are never committed — not by
#      file name, and not by pasting the dump's contents into another file
#      (a Flyway migration, a seed script, a test fixture).
#   2. Certain names never appear — in file contents, file paths or commit
#      messages. The names live OUTSIDE the repo, in
#      ${FORBIDDEN_NAMES_FILE:-~/.task/forbidden-names.txt} (one extended regex
#      per line, matched case-insensitively), so this script does not itself
#      mention them. If that file is missing the check fails closed.
#   3. Published history is never rewritten (pre-push rejects non-fast-forward
#      pushes and branch deletions).
#
# Usage:
#   check-submission.sh index            the staged tree        (pre-commit)
#   check-submission.sh message <file>   a commit message       (commit-msg)
#   check-submission.sh pre-push         git's pre-push stdin   (pre-push)
#   check-submission.sh rev <rev>...     whole tree + message of commits
#   check-submission.sh stdin <path>     text on stdin, as if written to <path>
#                                        (used by the Claude Code content guard)
#
# Exit: 0 clean, 1 violations found, 2 misconfigured (fails closed).
set -uo pipefail

names_file=${FORBIDDEN_NAMES_FILE:-$HOME/.task/forbidden-names.txt}

# Paths that must never be committed (matched against the basename).
banned_basename_re='^(ai_task\.md|dump\.sql)$'

# Lines that only occur in a pg_dump file. Anchored at line start, so this
# script's own source does not match them.
tab=$'\t'
uuid_re='[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
dump_markers=(
  -e '^\\restrict [A-Za-z0-9]+'
  -e '^-- (PostgreSQL database dump|Dumped (from|by) )'
  # A COPY data row: leading date/timestamp column, tab-separated, with a UUID column.
  -e "^[0-9]{4}-[0-9]{2}-[0-9]{2}( [0-9:.]+)?${tab}.*${tab}${uuid_re}${tab}"
)

violations=()
add() { violations+=("$1"); }

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
patterns=$tmp/names

load_names() {
  if [ ! -r "$names_file" ]; then
    printf 'check-submission: forbidden-names file not found: %s\n' "$names_file" >&2
    printf 'Create it (one extended regex per line) or set FORBIDDEN_NAMES_FILE. Refusing to pass without it.\n' >&2
    exit 2
  fi
  grep -vE '^[[:space:]]*(#|$)' "$names_file" >"$patterns" || true
  if [ ! -s "$patterns" ]; then
    printf 'check-submission: %s has no patterns. Refusing to pass without it.\n' "$names_file" >&2
    exit 2
  fi
}

# check_paths <label> : newline-separated paths on stdin.
check_paths() {
  local label=$1 path base
  while IFS= read -r path; do
    [ -z "$path" ] && continue
    base=${path##*/}
    if printf '%s\n' "${base,,}" | grep -qE "$banned_basename_re"; then
      add "$label: $path must not be committed (submission rule)."
    fi
    if printf '%s\n' "$path" | grep -qiE -f "$patterns"; then
      add "$label: path '$path' contains a forbidden name."
    fi
  done
}

# report_grep <label> <what> : 'path' or 'rev:path' lines on stdin.
report_grep() {
  local label=$1 what=$2 hit
  while IFS= read -r hit; do
    hit=${hit#*[0-9a-f]:}  # 'git grep <rev>' prefixes paths with '<sha>:'
    [ -n "$hit" ] && add "$label: $hit $what"
  done
}

check_index() {
  # Process substitution, not a pipe: a piped function runs in a subshell and
  # its add() calls would be lost.
  check_paths "staged" < <(git ls-files --cached)
  report_grep "staged" "contains a forbidden name." \
    < <(git grep --cached -I -l -i -E -f "$patterns" 2>/dev/null)
  report_grep "staged" "contains content from the supplied database dump." \
    < <(git grep --cached -I -l -E "${dump_markers[@]}" 2>/dev/null)
}

check_message_text() {
  local label=$1 file=$2
  # Drop git's comment lines; they are not part of the stored message.
  if grep -v '^#' "$file" | grep -qiE -f "$patterns"; then
    add "$label: commit message contains a forbidden name."
  fi
}

check_rev() {
  local rev=$1 short
  short=$(git rev-parse --short "$rev") || { add "rev: cannot resolve '$rev'."; return; }
  check_paths "commit $short" < <(git ls-tree -r --name-only "$rev")
  report_grep "commit $short" "contains a forbidden name." \
    < <(git grep -I -l -i -E -f "$patterns" "$rev" -- 2>/dev/null)
  report_grep "commit $short" "contains content from the supplied database dump." \
    < <(git grep -I -l -E "${dump_markers[@]}" "$rev" -- 2>/dev/null)
  git log -1 --format=%B "$rev" >"$tmp/msg"
  check_message_text "commit $short" "$tmp/msg"
}

check_pre_push() {
  local local_ref local_sha remote_ref remote_sha commit
  local -a range
  while read -r local_ref local_sha remote_ref remote_sha; do
    [ -z "${local_ref-}" ] && continue
    if [[ $local_sha =~ ^0+$ ]]; then
      add "push: deleting $remote_ref is not allowed (history must be preserved)."
      continue
    fi
    if [[ $remote_sha =~ ^0+$ ]]; then
      range=("$local_sha" --not --remotes)
    else
      if ! git cat-file -e "${remote_sha}^{commit}" 2>/dev/null; then
        add "push: $remote_ref has commits you do not have locally. Fetch and merge first; do not overwrite them."
        continue
      fi
      if ! git merge-base --is-ancestor "$remote_sha" "$local_sha"; then
        add "push: $local_ref -> $remote_ref is not a fast-forward. Rewriting or squashing published history is not allowed."
        continue
      fi
      range=("$remote_sha..$local_sha")
    fi
    # Every new commit is checked in full: a file added in one commit and
    # deleted in the next is still in the history.
    while IFS= read -r commit; do
      check_rev "$commit"
    done < <(git rev-list "${range[@]}")
  done
}

check_stdin() {
  local path=$1
  [ -n "$path" ] && check_paths "write" <<<"$path"
  cat >"$tmp/content"
  if grep -qiE -f "$patterns" "$tmp/content"; then
    add "write: content for ${path:-<stdin>} contains a forbidden name."
  fi
  if grep -qE "${dump_markers[@]}" "$tmp/content"; then
    add "write: content for ${path:-<stdin>} contains content from the supplied database dump."
  fi
}

mode=${1-}
[ $# -gt 0 ] && shift
load_names

case "$mode" in
  index)    check_index ;;
  message)  [ -r "${1-}" ] || { echo "usage: $0 message <file>" >&2; exit 2; }
            check_message_text "commit-msg" "$1" ;;
  pre-push) check_pre_push ;;
  rev)      [ $# -gt 0 ] || set -- HEAD
            for r in "$@"; do check_rev "$r"; done ;;
  stdin)    check_stdin "${1-}" ;;
  *)        sed -n '2,/^set -uo/p' "$0" | sed '$d; s/^# \{0,1\}//' >&2; exit 2 ;;
esac

if [ ${#violations[@]} -gt 0 ]; then
  {
    printf 'Submission check failed:\n'
    printf '  - %s\n' "${violations[@]}"
  } >&2
  exit 1
fi
exit 0
