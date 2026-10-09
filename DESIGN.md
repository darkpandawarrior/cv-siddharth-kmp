# cv-siddharth-kmp DESIGN.md

> Inherits: house design standard (AgentHarness skill `design-md`). This file wins on conflict.
> Does NOT use the kmp-toolkit `designsystem` tokens: this app has its own theme, `CvTheme`, and
> deliberately does not use `MaterialTheme.colorScheme`. The toolkit `DESIGN.md` does not apply to
> screens here. The web twin (cv-siddharth, `src/index.css` `@theme`) is the upstream of these values.
> Agents: read this before creating or changing UI. Values live in
> `cmp-shared/src/composeMain/kotlin/com/siddharth/cv/shared/theme/` (`CvTheme.kt`,
> `CvComponents.kt`, `CvMotion.kt`).
> Dial: ENERGY 5 / RHYTHM 5 / MOTION 6

## Overview

A Compose Multiplatform port (android, desktop, ios, web) of a personal portfolio whose web
version is "an instrument, not a control room". Art direction (`CAL-1`): calibration amber is the
measured signal, cyan is the reference being compared against, and cyan is never decoration. Near
black ground, hairline lines, glow as the one depth device, Space Grotesk display over DM Mono
metadata. Because the web build is canonical, token values are translated from `index.css`; when
the two disagree, `index.css` is upstream and this port is stale.

Per-project and per-surface reskins are a nested `CvTheme(colors = ...)`, the same mechanism the
production tenant theming uses. Nothing inside a subtree names a colour.

## Colors

Not M3. The token set is `CvColors` (read it with `cvColors`), roughly mapped below for orientation
only. Dark (`CvDarkColors`):

| CvColors token | Value | Nearest M3 role |
|---|---|---|
| accent | #F2A13D (calibration amber, 9.24:1 on ink) | primary |
| accentDim | #C47F2A | primaryContainer |
| accent2 | #4FD6E0 (cyan, the reference signal) | secondary |
| accent2Dim | #2FB8D6 | secondaryContainer |
| ink | #0A0D0C | background |
| surface | #111514 | surface |
| card | #171C1A | surfaceContainer |
| line | #262E2B | outlineVariant |
| onBackground | #E8EFE9 | onBackground |
| muted | #8B909A | onSurfaceVariant |
| deepVoid | #060807 | scrim / deepest ground |
| glass | #111619 at 66% alpha | surface with blur |
| glassBorder | #4FD6E0 at 14% alpha | outline |

`muted` is held at 4.65:1 against the lightest themed ground on the site; do not lighten or darken
it. The résumé route (`CvResumeColors`) is dark text on light: ink #E4E4E7, surface and card #FFFFFF,
onBackground #111111, muted #55585F, line #C8C8CC. Per-project themes (`projectColors`) override
accent, accentDim, ink, surface, card and line only. Android green left the site and survives as
`--color-signal` on the web; do not reintroduce it as the accent.

## Typography

Space Grotesk (Regular, Medium, SemiBold, Bold; Black is synthesised from Bold) for display and
body, DM Mono Medium for monospace, both vendored under `composeResources/font/`. Scale
(`CvTypography`, read with `cvType`): hero 36 to 60sp fluid (line height 1.05, tracking -0.02em),
h2 28 to 36sp fluid, metric 30 to 40sp fluid in accent, cardTitle 20/26 Bold, body 16/25, bodySmall
14/22 in muted, mono 13/20, metaMono 11/16 Medium tracking 0.08em in muted, eyebrow 12/16 SemiBold
tracking 0.14em in accent at 70%, ghostNumeral 36 Black in accent at 10%. Fluid sizes ramp linearly
between 375dp and 1920dp (`fluidSp`).

## Layout

`CvContentMaxWidth` 1024dp, `CvGutter` 24dp, `CvSectionGap` 72dp (`CvComponents.kt`). Sections
open with `SectionEyebrow` and `SectionHeading`, separated by `CircuitDivider`. The page ground is
`AmbientBackground`. Collapsible content uses `ExpanderSection`.

## Elevation & Depth

Depth is accent-tinted glow, not Material elevation (`Modifier.glow`, `CvMotion.kt`): concentric
rounded rects behind content, the stand-in for CSS `0 14px 40px -10px <tint>`. `CvCard` is black
glow at rest and accent glow on hover, press or focus, so a project reskin recolours it for free.
Glass panels use the `glass` and `glassBorder` tokens.

## Shapes

Literal radii in `CvComponents.kt`: cards 16dp, buttons (`PrimaryButton`, `GhostButton`) 10dp,
chips (`TagChip`) fully rounded (999), status elements 6dp. There is no shape token object yet.

## Components

`CvCard`, `TagChip`, `MonoMeta`, `PrimaryButton`, `GhostButton`, `AnimatedCounter`, `MetricGauge`,
`Sparkline`, `HeroShimmerText`, `MediaPanel`, `StatusDot`, `ExpanderSection`, `SectionEyebrow`,
`SectionHeading`, `CircuitDivider`, `AmbientBackground`. Previews: `CvComponentPreviews.kt`. Reveal
and tilt helpers: `Reveal`, `Modifier.tiltOnHover`. A GPU wash lives in `CvShaderWash.kt`.

## Motion

Ported one-for-one from `index.css` (`CvMotion`): EaseOutExpo cubic-bezier(0.16, 1, 0.3, 1) for
reveals, EaseOutQuart (0.25, 1, 0.5, 1) for hover and press, EaseSpring (0.34, 1.56, 0.64, 1) only
for the primary CTA hover-in. Durations: fast 180, base 300, slow 600, reveal 600 ms.
`LocalReducedMotion` is inherited by nested themes and can never be silently re-enabled.

## Do's and Don'ts

- Do read colour from `cvColors` and type from `cvType`; wrap subtrees in `CvTheme` to reskin.
- Do keep amber as the single measured-signal accent and cyan strictly as the compared reference.
- Do check any new colour against `index.css` in the web repo first.
- Don't read `MaterialTheme.colorScheme`; defaults would drag purple into this site.
- Don't hard-code a colour or font inside a themed subtree.
- Don't lighten or darken `muted`; the a11y suite depends on that exact value.
- Don't add a Material elevation shadow; use `glow`.

## Agent notes

- The `external/kmp-toolkit` and `external/kmp-build-logic` submodules may be empty in a fresh
  worktree; they do not feed this theme.
- The code wins on any value here. If a hex differs, fix this file, then check `index.css`.
- Run the `antislop` skill as the filter on any UI diff and report its Delivery Gate result.

## Changelog

| Date | Change | Why | Source |
|---|---|---|---|
| 2026-10-09 | Initial version, distilled from the code token files and existing design docs | Establish design direction for agents | DESIGN.md rollout |

## Open questions

- None recorded yet.

## Evolving this file

Agents: when you change UI and find this file wrong or silent, fix it in the same change and add a Changelog row. Code token files win over this file; when they disagree, correct the doc. A user correction of a visual choice with a stated reason becomes a rule here immediately. Lessons that apply beyond this repo go to the LEARNINGS log of the `design-md` skill in AgentHarness.
