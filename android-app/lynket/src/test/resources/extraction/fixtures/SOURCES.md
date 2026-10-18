# Extraction fixtures

Pages that the article extraction parity test (`ExtractionParityTest`) reads. The five fetched pages
were saved on 2026-10-18 by `android-app/scripts/fetch-extraction-fixtures.sh`, with the app's
User-Agent, as raw bytes. They are kept unchanged so the extractor always reads the same HTML.

| File | Kind | Address | Licence | Attribution |
|---|---|---|---|---|
| `news.html` | news article | https://en.wikinews.org/wiki/Grateful_Dead_rhythm_guitarist_Bob_Weir_dies,_aged_78 | CC BY 4.0 | Wikinews contributors |
| `blog.html` | blog post | https://creativecommons.org/2026/09/21/a-principled-approach-as-the-sands-shift/ | CC BY 4.0 (site content; the header image credit is CC BY-SA 4.0, Sergey Pesterev) | Creative Commons |
| `no-article.html` | page with no article | https://www.wikipedia.org/ | CC BY-SA 4.0 | Wikimedia Foundation, Wikipedia contributors |
| `images.html` | page with images | https://en.wikipedia.org/wiki/Lighthouse | CC BY-SA 4.0 | Wikipedia contributors |
| `non-english.html` | non-English page (German) | https://de.wikipedia.org/wiki/Leuchtturm | CC BY-SA 4.0 | Wikipedia contributors (de) |
| `metadata.html` | every metadata field the app reads | written for this test | same as the app | - |

The test also reads the two reader fixtures in `android-app/maestro/reader/fixtures/`
(`article.html`, `no-article.html`) in place.

`urls.txt` lists addresses for the link classification and redirect checks, one per line.

The goldens in `../golden/` were recorded from these files by the earlier extractor (Crux 2.0.1 on
jsoup 1.9.2). Replacing a fixture means recording its golden again with that extractor.
