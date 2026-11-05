#!/usr/bin/env bash
# PreToolUse / Edit|Write|MultiEdit|Bash — refuse content that breaks the
# submission rules before it reaches a file or a commit message.
#
# The checks themselves live in scripts/check-submission.sh (shared with the
# git hooks); this is only the Claude Code adapter. It fails closed: if the
# checker is missing or misconfigured, the tool call is denied.
#
#   Edit|Write|MultiEdit  files inside the project only. The new text is checked
#                         for forbidden names and dump content; the target path
#                         for banned files (the brief, the dump). Git-ignored
#                         targets get the path check only.
#   Bash                  the command text is checked for forbidden names and
#                         dump content, which covers heredoc writes and commit
#                         messages that bypass the Edit|Write hooks.
set -uo pipefail

root=${CLAUDE_PROJECT_DIR:-$(pwd)}
checker=$root/scripts/check-submission.sh

input=$(cat)

deny() {
  jq -n --arg reason "$1" '{
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: "deny",
      permissionDecisionReason: $reason
    }
  }'
  exit 0
}

[ -x "$checker" ] || deny "Blocked by .claude/hooks/guard-content.sh: $checker is missing or not executable, so content cannot be checked."

tool=$(printf '%s' "$input" | jq -r '.tool_name // empty')
cmd=$(printf '%s' "$input" | jq -r '.tool_input.command // empty')
file=$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty')

if [ "$tool" = Bash ] || { [ -z "$tool" ] && [ -n "$cmd" ]; }; then
  [ -z "$cmd" ] && exit 0
  path=""
  content=$cmd
else
  [ -z "$file" ] && exit 0
  case "$file" in
    /*) abs=$file ;;
    *)  abs=$root/$file ;;
  esac
  case "$abs" in
    "$root"/*) ;;
    *) exit 0 ;;  # outside the repo: not part of the submission
  esac
  path=${abs#"$root"/}
  content=$(printf '%s' "$input" | jq -r '
    [.tool_input.content, .tool_input.new_string, (.tool_input.edits[]?.new_string)]
    | map(select(. != null)) | join("\n")')
  if git -C "$root" check-ignore -q -- "$path" 2>/dev/null; then
    content=""
  fi
fi

out=$(printf '%s\n' "$content" | "$checker" stdin "$path" 2>&1)
status=$?
[ "$status" -eq 0 ] && exit 0

deny "Blocked by .claude/hooks/guard-content.sh (submission rules):
$out"
