# IrisI identity

**Stay with the light.**

IrisI is about photographic craft and attention, not technical spectacle. The
optical-bloom mark combines a soft organic iris with the glint of a lens. Curved
petal folds give it enough character to feel considered without a mechanical
shutter, chrome texture or a generic camera outline.

## Palette and type

- Graphite `#12151A`: the launcher backing and identity anchor.
- Honey `#E6A954`, copper `#B8753D`, ivory `#F9E8CB`: warm, material-like accents.
- Space Grotesk for headings, Inter for interface copy, JetBrains Mono for readings.
- Wallpaper-derived Material You colors belong to app controls. Diagnostic colors
  (recording, focus peaking and exposure warnings) retain their meanings.
- Android 12+ uses the full wallpaper palette by default, following system day/night
  mode. Earlier versions use warm light and graphite dark fallback palettes. Only
  the optical preview subtree is kept in dark mode; app panels follow the system.

## Assets

- `irisi-mark.svg`: editable color mark with subtle fold gradients.
- `irisi-mark-mono.svg`: single-color silhouette with a transparent pupil.
- `launcher-preview.png`: repository/avatar preview, not a UI screenshot.
- Android adaptive foreground and monochrome resources share the SVG geometry.
- Legacy square and round PNGs are generated at all five launcher densities.

Rebuild with `python design/generate_brand_assets.py` (standard library only).
The mark fits within Android's centered 66dp adaptive safe region. Do not stretch
it, fill its pupil, or use the graphite rectangle as the monochrome layer.

The old JPEG resource is retained, unused, rather than deleting earlier artwork.
