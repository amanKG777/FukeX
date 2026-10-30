#!/usr/bin/env bash
# Builds standalone web pages of the README and the user documentation with pandoc.
# Each page is one self-contained file (styles included), so it can be hosted or opened anywhere.
#
#   docs/build.sh
#
# The in-app documentation (app/src/main/assets/documentation.html) is a different, unstyled
# fragment that the app wraps itself; it is built separately with a plain pandoc call.
set -euo pipefail
cd "$(dirname "$0")/.."

build() {
	local source="$1" output="$2" title="$3"
	pandoc "$source" \
		--from gfm --to html5 --standalone --embed-resources \
		--css docs/style.css \
		--metadata lang=en \
		--metadata pagetitle="$title" \
		--include-before-body=docs/skip-link.html --include-after-body=docs/after.html \
		--lua-filter=docs/page.lua \
		-o "$output"
	echo "wrote $output"
}

build README.md docs/README.html "FukeX"
build documentation.md docs/documentation.html "FukeX Documentation"
