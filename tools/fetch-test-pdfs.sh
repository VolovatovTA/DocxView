#!/usr/bin/env bash
# Downloads hard real-world PDFs from the Mozilla pdf.js test suite for the instrumented stress tests.
# The files keep their original licenses and are not committed (see .gitignore).
set -euo pipefail
REV=24176a9460ca69f4d2ea5df4577f5ac2302b6cc1
DEST="$(cd "$(dirname "$0")/.." && pwd)/docviewer/src/androidTest/assets/corpus"
mkdir -p "$DEST"
FILES=(
  tracemonkey.pdf TAMReview.pdf alphatrans.pdf cmykjpeg.pdf bug_jpx.pdf devicen.pdf
  ShowText-ShadingPattern.pdf function_based_shading_cmyk.pdf axial_shading_many_inputs.pdf
  ContentStreamCycleType3insideType3.pdf Type3WordSpacing.pdf complex_ttf_font.pdf
  cidfont_cmap_overflow.pdf arial_unicode_ab_cidfont.pdf ArabicCIDTrueType.pdf
  annotation-link-text-popup.pdf annotation-line.pdf form_two_pages.pdf
  hello_world_rotated.pdf image-rotated-black-white-ratio.pdf helloworld-bad.pdf
  bad-PageLabels.pdf encrypted-attachment.pdf
)
for f in "${FILES[@]}"; do
  [ -s "$DEST/$f" ] && continue
  curl -fsSL "https://raw.githubusercontent.com/mozilla/pdf.js/$REV/test/pdfs/$f" -o "$DEST/$f"
  echo "fetched $f"
done
