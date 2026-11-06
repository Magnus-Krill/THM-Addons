#!/usr/bin/env bash
#
# This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
# Copyright (c) THM Addons contributors. Credit the devs, keep the link.
# By using this code you agree to the license terms and to keep your repo public.
#

set -euo pipefail
command -v setsid >/dev/null
cd "$(dirname "$0")/../.."
mkdir -p build/async-tests
result_dir=$(mktemp -d "$PWD/build/async-tests/run-XXXXXX")
nohup setsid bash -c '
    result_dir=$1
    shift
    "$@" >"$result_dir/output.log" 2>&1
    result=$?
    printf "%s\n" "$result" >"$result_dir/exit-code.tmp"
    mv "$result_dir/exit-code.tmp" "$result_dir/exit-code"
' bash "$result_dir" "$PWD/gradlew" build --console=plain "$@" </dev/null >"$result_dir/launcher.log" 2>&1 &
printf 'PID: %s\nLog: %s/output.log\nResult: %s/exit-code (0 = passed)\n' "$!" "$result_dir" "$result_dir"
