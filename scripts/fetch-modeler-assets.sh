#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/src/main/resources/bpmn-editor"
mkdir -p "$DEST"
if [[ -f "$DEST/camunda-cloud-modeler.production.min.js" && -d "$DEST/assets" ]]; then
  echo "modeler assets already present"
  exit 0
fi
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
cd "$TMP"
npm pack camunda-bpmn-js@5.34.0
tar -xzf camunda-bpmn-js-*.tgz
cp package/dist/camunda-cloud-modeler.production.min.js "$DEST/"
cp -R package/dist/assets "$DEST/"
echo "fetched camunda-bpmn-js assets into $DEST"
