#!/usr/bin/env bash
# Shared protected-path patterns. Sourced by guard-paths.sh (Edit|Write targets)
# and guard-bash.sh (shell redirection, tee, sed -i and dd targets).
#
# protected_reason <path>: prints a reason and returns 0 when the path is
# protected, returns 1 otherwise.

protected_reason() {
  local base=${1##*/}
  [ -z "$base" ] && return 1

  case "$base" in
    application-prod.*|application-prd.*|application-production.*)
      printf '%s is production configuration. Change it through your deployment pipeline, not from a Claude Code session.' "$base"
      return 0 ;;
    .env|.env.*)
      printf '%s holds environment secrets.' "$base"
      return 0 ;;
    *.jks|*.p12|*.pfx|*.pem|*.key|*.keystore|*.truststore)
      printf '%s is key or certificate material.' "$base"
      return 0 ;;
  esac

  local lower=${base,,}

  # Submission rule: the README is written by the human, without AI assistance.
  if [ "$lower" = readme.md ]; then
    printf '%s must be written by the author, not by Claude (submission rule). Suggest text in chat if asked, but do not write the file.' "$base"
    return 0
  fi

  if [[ $lower == *secret* || $lower == *credential* ]]; then
    printf '%s looks like it holds credentials.' "$base"
    return 0
  fi

  return 1
}
