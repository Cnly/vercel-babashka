#! /bin/bash

SCRIPT_DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

set -euo pipefail

target_dir=$1

function check_version() {
	local bb_exec=$1
	local version=$("$bb_exec" -e '(print (System/getProperty "babashka.version"))')
	local target_version=${BABASHKA_INSTALL_VERSION:-$version}
	[ "$version" = "$target_version" ]
}

# Already installed?
[ ! -x "$target_dir"/bb ] || ! check_version "$target_dir"/bb || exit 0

# Already have it in the system?
# We do this mainly to avoid generating tens of GBs of files during local dev
if type -p bb &> /dev/null && check_version bb; then
	echo "Linking system bb to $target_dir/bb"
	cat > "$target_dir"/bb <<-EOF
		#!/bin/sh
		"$(which bb)" "\$@"
	EOF
	chmod +x "$target_dir"/bb
	exit 0
fi

type -p curl &> /dev/null || {
	. "$SCRIPT_DIR"/import.sh
	import "static-binaries@1.0.0"
	static_binaries curl
}

bash <(curl -s https://raw.githubusercontent.com/babashka/babashka/master/install) \
	${BABASHKA_INSTALL_VERSION:+--version "$BABASHKA_INSTALL_VERSION"} \
	--download-dir "$target_dir" \
	--dir "$target_dir"
