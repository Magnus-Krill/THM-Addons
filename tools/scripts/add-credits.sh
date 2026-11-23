#!/usr/bin/env bash
# Prepend the THM Addons credit header to source files that lack it (idempotent).
# Usage: scripts/add-credits.sh [file ...]   (no args = all tracked source files)
# Skipped by design: .json (no comment syntax), .fsh (#version must stay line 1).
set -euo pipefail
cd "$(dirname "$0")/../.."

REPO="https://github.com/Magnus-Krill/THM-Addons"
MARK="This file is part of THM Addons"
L1="$MARK — $REPO"
L2="Copyright (c) THM Addons contributors. Credit the devs, keep the link."
L3="By using this code you agree to the license terms and to keep your repo public."
L4="You also agree to properly credit the original devs in your Readme.md"

add() {
  local f="$1" open mid close pre=""
  [ -f "$f" ] || return 0
  case "$f" in
    *.java|*.c|*.kts)              open='/*'; mid=' * '; close=' */' ;;
    *.md)                          open='<!--'; mid='  '; close='-->' ;;
    *.sh|*.properties|*.yml|*.toml|*.editorconfig|*.gitignore)
                                   open='#'; mid='# '; close='#' ;;
    *) return 0 ;;   # unknown/binary type -> leave alone
  esac
  grep -qF "$MARK" "$f" && return 0          # already credited
  # keep a shebang on line 1 if present
  if IFS= read -r first < "$f" && [ "${first#\#!}" != "$first" ]; then
    pre="$first"$'\n'; tail -n +2 "$f" > "$f.body"
  else
    cp "$f" "$f.body"
  fi
  { printf '%s' "$pre"; printf '%s\n%s%s\n%s%s\n%s%s\n%s\n\n' "$open" "$mid" "$L1" "$mid" "$L2" "$mid" "$L3" "$L3" "$close"; cat "$f.body"; } > "$f.tmp"
  mv "$f.tmp" "$f"; rm -f "$f.body"
  echo "credited: $f"
}

if [ "$#" -gt 0 ]; then
  for f in "$@"; do add "$f"; done
else
  git ls-files | while IFS= read -r f; do add "$f"; done
fi
