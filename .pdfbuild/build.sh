#!/bin/zsh
# Builds Navrh.md -> navrh-token-router.pdf (A4).
# Pipeline: Mermaid (.mmd -> .png) -> pandoc (md -> standalone html) -> headless Chrome (html -> pdf).
set -euo pipefail

P="$PWD"
B="$P/.pdfbuild"
TITLE="Návrh - TokenRouter"
OUT="$P/navrh-token-router.pdf"
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

mkdir -p "$B"

# Earlier builds left generated artifacts lying around.
rm -f "$B"/body.html(N) "$B"/p*.png(N) "$B"/navrh-*.png(N) "$B"/Navrh.pdf(N)

# Deterministic Mermaid rendering (no random font / theme).
cat > "$B/mermaid-config.json" <<'JSON'
{
  "fontFamily": "Helvetica Neue, Helvetica, Arial, sans-serif",
  "theme": "default"
}
JSON

echo "==> Rendering diagrams"
for mmd in "$P"/docs/mmd/*.mmd; do
  name="$(basename "$mmd" .mmd)"
  mmdc -q -i "$mmd" \
       -o "$B/$name.png" \
       -b white -s 2 -t default -c "$B/mermaid-config.json"
done

echo "==> Rendering Markdown -> HTML"
pandoc "$P/Navrh.md" \
  -f gfm+smart -t html5 \
  --no-highlight \
  --standalone \
  --metadata pagetitle="$TITLE" \
  --metadata lang=cs \
  --metadata pdfbuild-root="$P" \
  --css "file://$B/style.css" \
  --lua-filter="$B/filters.lua" \
  --resource-path="$P" \
  -o "$B/navrh.html"

echo "==> Printing PDF"
"$CHROME" \
  --headless \
  --disable-gpu \
  --no-pdf-header-footer \
  --allow-file-access-from-files \
  --virtual-time-budget=15000 \
  --print-to-pdf="$OUT" \
  "file://$B/navrh.html" 2>/dev/null

echo "Wrote $OUT"
