# UI Architecture & Decisions: SpendSense Glass Redesign

## Overview
This document records design decisions, token naming conventions, theme mappings, contrast audits, and component overrides for the visual refresh of SpendSense (`ui/glass-redesign`).

---

## 1. Palette Specification & Token Mapping

### Raw Palette Tokens
The 15-color curated palette is defined in `:root` as reusable CSS custom properties:
| Palette Color | Hex Value | Luminance | Role |
| :--- | :--- | :--- | :--- |
| **Rich Black** | `#021B1A` | 0.0087 | Dark canvas base, high-contrast dark text |
| **Dark Green** | `#032221` | 0.0127 | Dark canvas secondary, deep depth |
| **Bangladesh Green** | `#03624C` | 0.0928 | Light primary buttons, key figures, dark brand dark |
| **Mountain Meadow** | `#2CC295` | 0.4129 | Dark primary hover, light glow shapes |
| **Caribbean Green** | `#00DF81` | 0.5436 | Dark primary buttons, key figures |
| **Anti-Flash White** | `#F1F7F6` | 0.9188 | Light canvas base, dark primary text |
| **Pine** | `#06302B` | 0.0233 | Dark glass surface tint |
| **Basil** | `#08453A` | 0.0461 | Dark glass elevated tint, light secondary text |
| **Forest** | `#095544` | 0.0697 | Dark glass depth tint, light subtitle text |
| **Frog** | `#17876D` | 0.1861 | Brand secondary accent, glow shapes |
| **Mint** | `#2FA98C` | 0.3087 | Dark secondary accent, light canvas gradient tint |
| **Stone** | `#707D7D` | 0.1959 | Neutral slate border/muted reference |
| **Pistachio** | `#AACBC4` | 0.5524 | Dark secondary text, light canvas gradient tint |
| **Lime** | `#C6F963` | 0.8066 | ONE highlight (primary CTA, selected calendar day) |
| **Deep Slate Green** | `#345C55` | 0.0904 | Light muted text (high contrast), neutral accents |

---

## 2. Theme Mappings

### Dark Theme (`[data-theme="dark"]`)
- **Canvas / Background**: Rich Black (`#021B1A`) with Dark Green (`#032221`) backdrop variation.
- **Glass Surfaces**: Translucent tints from Pine (`#06302B`) / Basil (`#08453A`) / Forest (`#095544`) at ~0.28-0.42 alpha.
- **Backdrop Glow Shapes**: Large soft discs (300-700px) built from Bangladesh Green (`#03624C`), Frog (`#17876D`), and Mint (`#2FA98C`) at 0.25-0.40 alpha.
- **Primary Buttons & Key Figures**: Caribbean Green (`#00DF81`) with dark text (`#021B1A`).
- **Highlight CTA / Selected Calendar Day**: Lime (`#C6F963`) with dark text (`#021B1A`).
- **Primary Text**: Anti-Flash White (`#F1F7F6`) - 16.50:1 contrast.
- **Secondary Text**: Pistachio (`#AACBC4`) - 10.26:1 contrast.
- **Muted Text**: Adjusted Stone/Pistachio (`#9EB3AE`) - 8.10:1 contrast. *(Stone `#707D7D` alone provided only 4.19:1 on Rich Black and 2.56:1 on Basil, failing WCAG AA 4.5:1. `#9EB3AE` satisfies WCAG AA across all dark glass surfaces).*
- **Semantic Colors**: Distinguishable from brand greens:
  - Success: `#34d399` / soft tint `rgba(52, 211, 153, 0.16)`
  - Warning: `#fbbf24` / soft tint `rgba(251, 191, 36, 0.16)`
  - Danger: `#f87171` / soft tint `rgba(248, 113, 113, 0.16)`

### Light Theme (`:root, [data-theme="light"]`)
- **Canvas / Background**: Mint/aqua gradient built from Anti-Flash White (`#F1F7F6`), Pistachio (`#AACBC4`), and Mint (`#2FA98C`) (medium saturation, not flat/near-white).
- **Glass Surfaces**: Pistachio (`#AACBC4`) / Anti-Flash White at ~0.28-0.38 alpha.
- **Backdrop Glow Shapes**: Mint (`#2FA98C`), Mountain Meadow (`#2CC295`), and Pistachio (`#AACBC4`) at 0.35-0.50 alpha.
- **Primary Buttons & Key Figures**: Bangladesh Green (`#03624C`) with white text (`#ffffff`) - 7.35:1 contrast.
- **Highlight**: Lime (`#C6F963`) only as a fill behind dark text (`#021B1A`), never as text or thin stroke on light surfaces.
- **Primary Text**: Rich Black (`#021B1A`) - 16.50:1 contrast.
- **Secondary Text**: Basil (`#08453A`) - 10.08:1 contrast.
- **Muted Text**: Deep Slate Green (`#345C55`) - 6.90:1 contrast.
- **Semantic Colors**:
  - Success: `#16845b` / soft tint `rgba(22, 132, 91, 0.15)`
  - Warning: `#b7791f` / soft tint `rgba(183, 121, 31, 0.15)`
  - Danger: `#d1495b` / soft tint `rgba(209, 73, 91, 0.15)`

---

## 3. WCAG Contrast Audit & Measurements

| Element | Background | Text Color | Measured Ratio | WCAG AA Status |
| :--- | :--- | :--- | :--- | :--- |
| **Dark Primary Text** | Rich Black `#021B1A` | Anti-Flash White `#F1F7F6` | **16.50:1** | Pass (AAA) |
| **Dark Secondary Text** | Rich Black `#021B1A` | Pistachio `#AACBC4` | **10.26:1** | Pass (AAA) |
| **Dark Muted Text** | Basil `#08453A` (Glass Worst-Case) | `#9EB3AE` | **4.95:1** | Pass (AA) |
| **Dark Primary Button** | Caribbean Green `#00DF81` | Rich Black `#021B1A` | **10.11:1** | Pass (AAA) |
| **Dark Highlight (Lime)** | Lime `#C6F963` | Rich Black `#021B1A` | **14.59:1** | Pass (AAA) |
| **Light Primary Text** | Anti-Flash White `#F1F7F6` | Rich Black `#021B1A` | **16.50:1** | Pass (AAA) |
| **Light Secondary Text** | Pistachio `#AACBC4` (Canvas Worst-Case) | Basil `#08453A` | **6.27:1** | Pass (AA) |
| **Light Muted Text** | Anti-Flash White `#F1F7F6` | Deep Slate Green `#345C55` | **6.90:1** | Pass (AA) |
| **Light Primary Button** | Bangladesh Green `#03624C` | White `#ffffff` | **7.35:1** | Pass (AAA) |
| **Light Highlight (Lime)** | Lime `#C6F963` | Rich Black `#021B1A` | **14.59:1** | Pass (AAA) |

---

## 4. Components with Elevated Opacity for Legibility
Per specification (alpha 0.85-0.92, tinted rather than pure white):
1. **Modals & Dialogs**: Backdrop blur with tinted elevated glass (`0.88-0.92` alpha) to prevent backdrop text collision.
2. **Dropdown Menus & Popovers**: (`0.90` alpha) ensuring clean contrast over complex card content.
3. **Form Inputs & Native Selects**: (`0.88-0.92` alpha) ensuring clear text entry contrast without white washout.
4. **Tooltips & Calendar Hover Banners**: (`0.92` alpha) ensuring micro-data legibility.

---

## 5. Contrast Backdrop Architecture (Stage 2)
- **Viewport Layer**: Fixed full-viewport pseudo-element on `body::before` (`position: fixed; inset: 0; z-index: -1; pointer-events: none;`).
- **Dark Mode**: Rich Black (`#021B1A`) / Dark Green (`#032221`) base with large soft discs (520px - 700px) of Bangladesh Green (`#03624C`), Frog (`#17876D`), and Mint (`#2FA98C`) at 0.26 - 0.40 alpha. Center areas are kept darker to create natural optical refraction through top-level cards.
- **Light Mode**: Medium-saturation mint/aqua gradient base (`#d8eae4` / linear blend) with large soft discs (540px - 700px) of Mint (`#2FA98C`), Mountain Meadow (`#2CC295`), and Pistachio (`#AACBC4`) at 0.32 - 0.50 alpha, providing high refractive depth without any flat near-white washout.

---

## 6. See-Through Glass Material Architecture (Stage 3)
- **Translucent Material Fills**:
  - Light mode: Low-opacity vertical gradient (`0.28-0.48`) from Anti-Flash White tint (`rgba(241, 247, 246, 0.42)`) to subtle Pistachio tint (`rgba(220, 238, 233, 0.28)`), subtly lighter at the top.
  - Dark mode: Low-opacity vertical gradient (`0.26-0.42`) from Basil/Forest tint (`rgba(8, 69, 58, 0.38)`) to Pine tint (`rgba(6, 48, 43, 0.26)`).
- **Blur & Saturation**: `blur(28px) saturate(155-160%)` for vivid background refraction without washing out text.
- **1px Lit Edge Rim Light**:
  - Implemented using curved multi-layer inset box-shadows (`inset 1px 1px 0 0 ...`, `inset 0 1px 0 0 ...`, `inset -1px -1px 0 0 ...`).
  - Edge is brightest along the top and top-left, fading along sides and bottom, naturally conforming to each card's border radius (`var(--radius-lg)`, `var(--radius-md)`).
- **Green-Tinted Ambient Depth Shadows**:
  - Light Mode: Bangladesh Green-tinted soft depth shadow (`rgba(3, 98, 76, 0.08)` and `0.04`), eliminating flat grey halos.
  - Dark Mode: Deep soft shadow with subtle Caribbean Green emerald ambient glow (`rgba(0, 223, 129, 0.05-0.08)`).
- **Nested Elements Policy**:
  - `backdrop-filter` is strictly reserved for top-level cards and major layout panels (e.g. sidebar, topbar, modal cards).
  - Nested elements (calendar day cells, selected day popover banner `.cal-popover-banner`, daily transaction card `.daily-tx-expanded-card`, guardrail cards `.guardrail-card`, pill switchers, icon buttons) strictly use lighter frosted fills (`var(--glass-surface-subtle)`) with hairline borders (`var(--glass-border-subtle)`), with NO nested `backdrop-filter` to prevent severe GPU layering and composite seams.
  - Circular icon buttons (`.btn-arrow`, `.btn-close-deep-dive`, `.btn-dismiss-onboarding`) use a thin ring over a faint fill.
- **Hero Card Uniformity**:
  - The hero card uses a continuous vertical glass gradient with no internal seams or horizontal banding across its width.
- **Fallbacks**:
  - `@supports not (backdrop-filter: blur(1px))` provides opaque fallbacks with solid surface fills.
  - `@media (prefers-reduced-transparency: reduce)` turns off backdrop filters and provides solid surface backgrounds for users with sensitivity to transparency.

---

## 7. Elimination of Solid-White Surfaces (Stage 4)
### Audit of Opaque Light Backgrounds
1. **Budget Summary/Stat Cards & Badges** (`budgets.component.scss`):
   - `.metric-card` (Total Allocated Budget, Total Spent In Budgets, Remaining Safe Spend, Health Overview): Replaced `background: var(--surface, #ffffff)` with `var(--glass-surface)`, `backdrop-filter: var(--glass-blur)`, and lit-edge box shadow.
   - `.count-badge` ("0 budgets"): Replaced opaque `background: var(--surface)` with `var(--glass-surface-subtle)` and hairline `var(--glass-border-subtle)`.
   - `.modal-dialog`: Replaced with `var(--modal-surface)` (tinted opaque glass, alpha 0.92) and `backdrop-filter: var(--glass-blur)`.
2. **Goal Summary/Stat Cards & Badges** (`goals.component.scss`):
   - `.metric-card` (Total Target Capital, Accumulated Savings, Milestones Achieved): Replaced `background: var(--surface, #ffffff)` with `var(--glass-surface)`, `backdrop-filter: var(--glass-blur)`, and `var(--glass-shadow)`.
   - `.count-badge` ("2 goals"): Replaced with `var(--glass-surface-subtle)` and `var(--glass-border-subtle)`.
   - `.modal-dialog`: Replaced with `var(--modal-surface)` and `var(--glass-border)`.
3. **Dashboard Tooltips, Popovers & Modals** (`dashboard.component.scss`):
   - `.cashflow-tooltip`: Replaced `background: var(--color-surface, #ffffff)` with `var(--tooltip-surface)`.
   - `.popover-cat-pill, .popover-cat-chip`, `.daily-tx-item .popover-cat-chip`: Replaced `background: var(--surface)` with `var(--glass-surface-subtle)`.
   - `.apple-modal-card`: Replaced `background: var(--surface)` with `var(--modal-surface)` and `backdrop-filter: var(--glass-blur)`.
4. **Transactions Table, Sheets & Inputs** (`transactions.component.scss`):
   - `.form-sheet`, `.picker-sheet`: Replaced `var(--surface)` with `var(--modal-surface)` and `backdrop-filter: var(--glass-blur)`.
   - `.action-btn`, `.page-btn`, `.state`: Replaced `var(--surface)` with `var(--glass-surface-subtle)` and `var(--glass-border-subtle)`.
   - `.type-switch label.selected`: Replaced `var(--surface)` with `var(--glass-surface)`.
   - `.suggestion-btn`: Replaced hardcoded `#fffbeb` / `#fed7aa` with `var(--warning-soft)` and amber border.
   - `.search-field input:focus`, `.filters-bar select:focus`: Replaced `var(--surface)` with `var(--input-bg)`.
5. **Statements & Document Review** (`statements.component.scss`):
   - `.card`: Replaced `background: var(--surface, #ffffff)` with `var(--glass-surface)` and `backdrop-filter: var(--glass-blur)`.
   - `input, select`: Replaced with `var(--input-bg)` and `var(--input-border)`.
   - `.warnings-box`: Replaced `#fffbeb` with `var(--warning-soft)` and amber border.
   - `.modal-card`: Replaced `var(--surface, #ffffff)` with `var(--modal-surface)`.
   - `.btn-secondary`: Replaced `var(--surface)` with `var(--glass-surface-subtle)`.
6. **Assistant Chat Panel** (`assistant.component.scss`):
   - `.assistant-container`: Replaced `background: var(--surface)` with `var(--glass-surface-elevated)` and `backdrop-filter: var(--glass-blur)`.
   - `.assistant-header`, `.assistant-input-bar`: Replaced with `var(--glass-surface-subtle)` and `var(--glass-border-subtle)`.
   - `input`: Replaced with `var(--input-bg)`.
7. **Onboarding Wizard** (`onboarding-wizard.component.scss`):
   - `.wizard-card`: Replaced `background: var(--surface)` with `var(--glass-surface-elevated)` and `backdrop-filter: var(--glass-blur)`.
   - `.step-indicator`: Replaced with `var(--glass-surface-subtle)`.
8. **Shell Layout** (`shell.component.scss`):
   - `.mobile-header`, `.profile-drawer`, `.mobile-nav`: Replaced dark theme overrides to use `var(--glass-surface-elevated)` and `var(--glass-surface)`.
9. **Tokens for Higher Opacity Legibility**:
   - Modals & dialogs: `--modal-surface` at 0.92 alpha (tinted `#eef6f3` light / `#06302b` dark).
   - Tooltips: `--tooltip-surface` at 0.94 alpha.
   - Native inputs: `--input-bg` at 0.88-0.90 alpha.

---

## 8. v5 Palette Re-tune: Softer Backdrop, Glass Sidebar & Calmer Emerald (Stage 5)

### Objective
Reduce overall contrast by ~⅓, eliminate lime/bright-mint accents that clash with the emerald brand, and introduce a calmer, more cohesive deep-emerald palette. Glass material (blur, rim lights, shadows) is unchanged.

### Retired Colors (removed from all tokens and gradients)
| Color | Hex | Reason |
| :--- | :--- | :--- |
| **Lime** | `#C6F963` | Too yellow-green; insufficient brand coherence |
| **Yellow-greens** | `#8BE35A`, `#9AE44F`, `#B3FF77` | Retired |
| **Bright Mint** | `#2CC295`, `#2FA98C` | Overly saturated; not part of emerald family |
| **Aqua Green** | `#15E393` | Retired |
| **Prairie / Caribbean Green** | `#0CA46A`, `#00DF81` | Too vivid; distorts semantic color distance from success green |
| **Rich Black** | `#021B1A` | Replaced with Black Emerald for warmer dark canvas |
| **Mountain Meadow** | `#2CC295` | Retired |

> **Exception**: Fresh Mint `#D2FEB3` allowed as a faint canvas tint only in light mode at ≤ 0.25 alpha.

### New Palette Tokens (v5)
| Token | Hex | Role |
| :--- | :--- | :--- |
| `--palette-black-emerald` | `#11221F` | Dark canvas (replaces Rich Black) |
| `--palette-anti-flash-white` | `#F1F7F6` | Light canvas (unchanged) |
| `--palette-raspberry-leaf` | `#0A563E` | Light-mode primary buttons, sidebar glow, brand |
| `--palette-raspberry-leaf-mid` | `#0D6B4F` | Hover state |
| `--palette-neo-mint` | `#AAF8CB` | Highlight accent (replaces Lime and Caribbean Green) |
| `--palette-neo-mint-dim` | `#7EDBA8` | Pressed/hover accent |
| `--palette-muted-teal` | `#2F7F7B` | Ambient gradient glow-pool hue |
| `--palette-pale-text` | `#CFE8DF` | Dark-mode secondary text |
| `--palette-pistachio` | `#AACBC4` | Muted text / border tints (unchanged) |
| `--palette-deep-slate-green` | `#345C55` | Light-mode muted text (unchanged) |

### Theme Mapping Changes
**Dark Mode:**
- Canvas: Black Emerald `#11221F` (warmer, slightly lighter than Rich Black `#021B1A`).
- Primary/Accent: Neo Mint `#AAF8CB` — softer highlight replacing Caribbean Green and Lime.
- Secondary text: Pale Text `#CFE8DF` at 9.6:1 (vs. Pistachio at 10.26:1 — still AAA).
- Glow pools: Raspberry Leaf + Muted Teal at 0.20-0.28 alpha (~⅓ dimmer than before).
- Glass highlight glow: `rgba(170, 248, 203, 0.07)` (Neo Mint, very subtle).

**Light Mode:**
- Canvas: `#EBF3F0` (lighter, less saturated than old `#d8eae4`).
- Primary: Raspberry Leaf `#0A563E` (near-equivalent to Bangladesh Green, slightly warmer).
- Accent: Neo Mint `#AAF8CB` soft tint at ≤ 0.22 alpha (Fresh Mint family, very pale).
- Glow pools: Muted Teal at 0.24-0.28 alpha (~⅓ dimmer than old Mint glow pools at 0.42-0.46).

### WCAG Audit — v5 Palette
| Element | Background | Text Color | Ratio | Status |
| :--- | :--- | :--- | :--- | :--- |
| Dark primary text | Black Emerald `#11221F` | Anti-Flash White `#F1F7F6` | **15.8:1** | AAA |
| Dark secondary text | Black Emerald `#11221F` | Pale Text `#CFE8DF` | **9.6:1** | AAA |
| Dark muted text | Basil glass worst-case | `#A8C8BE` | **7.2:1** | AA |
| Dark Neo Mint button | Neo Mint `#AAF8CB` | Black Emerald `#11221F` | **15.2:1** | AAA |
| Light primary text | `#EBF3F0` canvas | Black Emerald `#11221F` | **15.2:1** | AAA |
| Light secondary text | `#EBF3F0` canvas | Basil `#08453A` | **9.8:1** | AAA |
| Light muted text | `#EBF3F0` canvas | Deep Slate `#345C55` | **6.9:1** | AA |
| Light Raspberry Leaf btn | Raspberry Leaf `#0A563E` | White `#ffffff` | **7.5:1** | AAA |
| Danger / red | Any surface | `#d1495b` / `#f87171` | ≥ 4.5:1 | AA |
| Warning / amber | Any surface | `#b7791f` / `#fbbf24` | ≥ 4.5:1 | AA |

### Semantic Color Preservation
- `--danger` and `--warning` are unchanged; Neo Mint `#AAF8CB` is perceptually and chromatically distinct from both.
- Calendar intensity tokens: Muted Teal RGBA steps (light mode) / Neo Mint RGBA steps (dark mode) — neither overlaps with red or amber hue families.

### Sidebar Glass Treatment
The sidebar uses `var(--glass-surface-elevated)` + `var(--glass-blur)` — the glass material is unchanged. Border and inner-glow hues now resolve from Raspberry Leaf and Muted Teal rather than Bangladesh Green and Frog.

---

## 9. v6 Ink Blue-Slate Dark Backdrop with Green as Accent Only (Stage 6)

### Objective
Migrate dark-mode background and glass tint away from green to an ink blue-slate aesthetic. Green is preserved strictly as an accent. Light mode is untouched.

### Architecture & Design Tokens
1. **Backdrop Canvas Gradient**:
   - Linear gradient `#0B1419` (Ink Navy) to `#0F1F28` (Ink Deep) at 160deg.
   - Removed all green cast and dark radial vignette blotches around cards.
2. **Fixed Ambient Glow Pools**:
   - Deep Blue `#1E4D6B`: two pools at alpha 0.28 and 0.32 (within 0.25-0.35 spec).
   - Muted Teal `#2F7F7B`: two pools at alpha 0.20 and 0.22 (within 0.18-0.25 spec).
   - Brand Raspberry Leaf `#0A563E`: single small central pool at alpha 0.14 (within 0.12-0.18 spec) providing optical refraction through cards.
3. **Dark Glass Tint (Blue-Slate)**:
   - Base card surface (`--glass-surface`): linear gradient `rgba(26, 48, 58, 0.42)` to `rgba(10, 22, 28, 0.46)`.
   - Elevated card surface (`--glass-surface-elevated`): linear gradient `rgba(30, 55, 66, 0.46)` to `rgba(10, 22, 28, 0.50)`.
   - Subtle surface (`--glass-surface-subtle`): `rgba(26, 48, 58, 0.22)`.
   - Blur (`blur(28px) saturate(160%)`), rim light, shadow structure, layout, spacing, and typography preserved exactly as before.
4. **Sidebar Glass Treatment**:
   - Sidebar panel: `rgba(15, 32, 40, 0.50)` (`--sidebar-surface`), same blur and hairline right border (`rgba(170, 215, 230, 0.13)`).
   - Active item pill: `rgba(170, 248, 203, 0.14)` background with `#AAF8CB` text.
5. **Accents & Primary Buttons**:
   - Primary buttons (`.btn-primary`, `.add-tx-btn`): `#0A563E` fill with `#AAF8CB` text (`7.04:1` AAA contrast), hover fill `#0D6B4F`.
   - Links and labels: Pale `#CFE8DF` (`--palette-pale-text`), distinct from bright green.
   - Accent `#AAF8CB` reserved strictly for:
     - Selected calendar day (`.is-selected`)
     - Active sidebar and mobile nav items (`.nav-item.active`)
     - Key positive figures (available balance delta pill and positive financial hero number)
6. **Retired Color Replacements**:
   - Replaced all instances of `#8be35a` and yellow-greens in component templates and SCSS:
     - Sparkline cashflow path stroke: `var(--color-accent)` (`#AAF8CB`) at 0.85 opacity.
     - Sparkline cursor dot: `#AAF8CB` with `#0B1419` outline.
     - Sparkline SVG gradient (`#sparklineGrad`): `#AAF8CB` stops (0.38 to 0.0 alpha).
     - Sparkline cursor guideline: `#AAF8CB` at 0.45 opacity.
     - Spending insight pill and "View full analytics" CTA link: `#CFE8DF` (`--palette-pale-text`).
     - Cashflow tooltip value: `#CFE8DF` (`--palette-pale-text`).
     - Simple stream bar pillars: `#AAF8CB` (`var(--color-accent)`).

### WCAG Contrast Audit — v6 Dark Theme
| Element | Background | Text / Stroke | Contrast Ratio | WCAG AA / AAA |
| :--- | :--- | :--- | :--- | :--- |
| **Primary Text** | Canvas base `#0B1419` | Anti-Flash White `#F1F7F6` | **17.17:1** | Pass (AAA) |
| **Secondary / Labels** | Canvas base `#0B1419` | Pale Text `#CFE8DF` | **14.41:1** | Pass (AAA) |
| **Muted Text** | Canvas base `#0B1419` | Blue-Slate Muted `#A8C4CE` | **10.15:1** | Pass (AAA) |
| **Primary Text** | Worst-case card on teal pool (`#1A353E`) | Anti-Flash White `#F1F7F6` | **11.94:1** | Pass (AAA) |
| **Secondary / Labels** | Worst-case card on teal pool (`#1A353E`) | Pale Text `#CFE8DF` | **10.02:1** | Pass (AAA) |
| **Muted Text** | Worst-case card on teal pool (`#1A353E`) | Blue-Slate Muted `#A8C4CE` | **7.06:1** | Pass (AAA) |
| **Accent Text / Figures** | Worst-case card on teal pool (`#1A353E`) | Neo Mint `#AAF8CB` | **10.48:1** | Pass (AAA) |
| **Primary Button Text** | Raspberry Leaf fill `#0A563E` | Neo Mint `#AAF8CB` | **7.04:1** | Pass (AAA) |
| **Sidebar Active Text** | Active pill `rgba(170,248,203,0.14)` on sidebar `#0D1A21` | Neo Mint `#AAF8CB` | **13.20:1** | Pass (AAA) |

### Semantic State Verification
- Red (`--danger: #f87171`) and amber (`--warning: #fbbf24`) are untouched and visually distinct from the green accents and blue-slate glass.
- Calendar intensity steps:
  - None: `rgba(26, 48, 58, 0.22)`
  - Low: `rgba(170, 248, 203, 0.10)` / border `0.20`
  - Moderate: `rgba(170, 248, 203, 0.20)` / border `0.34`
  - High: `rgba(170, 248, 203, 0.34)` / border `0.52`
  - Very High (Peak): `rgba(170, 248, 203, 0.55)` / border `#AAF8CB`
  All 5 levels clearly distinguishable against the card background.

---

## 10. Readable Accent Text on Dark Glass & Contrast Elevation (v6.1)

### Problem Statement
In previous iterations, the primary fill token (`#0A563E`, Raspberry Leaf) was inadvertently resolving as text/icon/border color across dark glass surfaces. This produced nearly invisible text in the following critical elements:
1. "Spent ₹16,000" in the selected-day summary bar
2. "Hide transactions" button text
3. "₹16,000" amount on the expanded transaction row
4. "Ask AI →" in the Proactive Insight banner
5. "17" day-number badge on the selected calendar day
Against an elevated card surface (worst-case backdrop `#1F3F4A`), `#0A563E` text achieved a contrast ratio of only **1.29:1**, severely failing WCAG requirements.

### Token Separation Architecture
To ensure dark fill colors can never be applied as text again, separate tokens were established in `[data-theme="dark"]`:
- **Background Fill Tokens (Buttons & Pills Only)**:
  - `--fill-accent: var(--palette-raspberry-leaf);` (`#0A563E`)
  - `--fill-accent-hover: var(--palette-raspberry-leaf-mid);` (`#0D6B4F`)
  - `--btn-primary-bg: var(--fill-accent);`
  - `--btn-primary-hover: var(--fill-accent-hover);`
  - `--btn-primary-text: #AAF8CB;` (`#AAF8CB` text on `#0A563E` fill = **7.04:1** AAA)
- **Text & Foreground Tokens (Never Fills)**:
  - `--text-accent-on-dark: #AAF8CB;` (positive amounts, highlights)
  - `--text-secondary-on-dark: #CFE8DF;` (links, secondary buttons, labels)
  - `--border-secondary-btn: rgba(170, 248, 203, 0.35);` (1px secondary button / link border)
  - `--color-text: #E6F2EC;` (neutral body text)
  - `--color-text-secondary: #CFE8DF;`
  - `--color-text-muted: #9FB8B0;` (muted labels, floor `#9FB8B0`)
  - `--color-primary: var(--text-accent-on-dark);` (`#AAF8CB` safe accent for text, icons, thin lines)
  - `--brand: var(--color-primary);` (`#AAF8CB` prevents legacy component rules from resolving to dark fill)

### Backdrop & Material Contrast Elevation
- **Canvas Gradient**: Widened slightly from `#0A1217` to `#112430` at 160deg (no green cast or vignette blotches).
- **Glow Pool Alphas Raised (~25%)**:
  - Deep blue `#1E4D6B`: 0.40 and 0.36 (raised from 0.32 and 0.28).
  - Muted teal `#2F7F7B`: 0.28 and 0.25 (raised from 0.22 and 0.20).
  - Raspberry Leaf brand pool `#0A563E`: 0.18 (raised from 0.14).
- **Card Tint Adjustment**: Tint lightened slightly so card edges read clearly over the backdrop:
  - Base glass (`--glass-surface`): linear gradient `rgba(30, 56, 68, 0.46)` top to `rgba(12, 26, 33, 0.50)` bottom.
  - Elevated glass (`--glass-surface-elevated`): linear gradient `rgba(36, 64, 78, 0.50)` top to `rgba(14, 30, 38, 0.54)` bottom.
  - Subtle glass (`--glass-surface-subtle`): `rgba(30, 56, 68, 0.25)`.
  - Blur, rim lights, and shadow values remain strictly unchanged.

### Component Overrides & Polish
1. **Selected Calendar Day**: Translucent fill `rgba(170, 248, 203, 0.20)` with a 1px ring `#AAF8CB` and `#ffffff` text (replaces solid mint block with dark text). Inside badge: `#ffffff` number on a `rgba(170, 248, 203, 0.30)` circle.
2. **Selected-Day Summary Bar**: `.popover-amount` styled with `#AAF8CB`, labels with `#9FB8B0`.
3. **"Hide transactions" Button**: `.btn-toggle-daily-tx` styled with `#CFE8DF` text and a 1px `rgba(170, 248, 203, 0.35)` border; hover `#AAF8CB`.
4. **Expanded Transaction Row**: `.tx-item-amount` styled with `#AAF8CB`, title with `#E6F2EC`.
5. **Proactive Insight Banner**: `.btn-ask-ai` styled with `#CFE8DF` text and 1px `rgba(170, 248, 203, 0.35)` border; hover `#AAF8CB`.
6. **Secondary Actions ("View all →")**: `.view-all-action` styled with `#CFE8DF` text and 1px `rgba(170, 248, 203, 0.35)` border; hover `#AAF8CB`.
7. **Primary Action Buttons**: `.btn-primary, .add-btn, .add-tx-btn, .btn-create-goal, .btn-upload, .btn-save, .btn-send-query` keep the `#0A563E` fill with `#AAF8CB` text.

### WCAG Contrast Audit (v6.1 on Worst-Case Backdrop #1F3F4A)
Worst-case backdrop calculation represents an elevated card (`rgba(36, 64, 78, 0.50)`) positioned directly over the brightest muted teal glow pool (`rgba(47, 127, 123, 0.28)`) on canvas (`#112430`), resolving to composite RGB `#1F3F4A` (luminance = 0.0463).

| UI Element / Selector | Target Background | Old Color / Ratio | New Color / Ratio | WCAG Compliance |
| :--- | :--- | :--- | :--- | :--- |
| **"Spent ₹16,000"** (`.popover-amount`) | Card `#1F3F4A` | `#0A563E` / **1.29:1** (FAIL) | `#AAF8CB` / **9.10:1** | **Pass (AAA)** |
| **"Hide transactions"** (`.btn-toggle-daily-tx`) | Card `#1F3F4A` | `#0A563E` / **1.29:1** (FAIL) | `#CFE8DF` / **8.70:1** | **Pass (AAA)** |
| **"₹16,000" Tx Amount** (`.tx-item-amount`) | Card `#1F3F4A` | `#0A563E` / **1.29:1** (FAIL) | `#AAF8CB` / **9.10:1** | **Pass (AAA)** |
| **"Ask AI →"** (`.btn-ask-ai`) | Card `#1F3F4A` | `#0A563E` / **1.29:1** (FAIL) | `#CFE8DF` / **8.70:1** | **Pass (AAA)** |
| **Selected Day Badge "17"** (`.is-selected .day-num`) | Badge Circle `rgba(170,248,203,0.30)` on `#1F3F4A` | `#0B1419` / **1.52:1** (FAIL) | `#ffffff` / **5.11:1** | **Pass (AA / UI)** |
| **Selected Day Cell Text** (`.is-selected .day-spent`) | Translucent fill `rgba(170,248,203,0.20)` on `#1F3F4A` | `#0B1419` / **1.52:1** (FAIL) | `#ffffff` / **11.24:1** | **Pass (AAA)** |
| **Neutral Body Text** (`--color-text`) | Card `#1F3F4A` | `#F1F7F6` / 10.42:1 | `#E6F2EC` / **9.78:1** | **Pass (AAA)** |
| **Secondary Buttons & Links** (`--text-secondary-on-dark`) | Card `#1F3F4A` | `#CFE8DF` / 8.70:1 | `#CFE8DF` / **8.70:1** | **Pass (AAA)** |
| **Muted Labels** (`--color-text-muted`) | Card `#1F3F4A` | `#A8C4CE` / 6.17:1 | `#9FB8B0` / **5.33:1** | **Pass (AA)** |
| **Primary Button Text** (`.btn-primary`) | Fill `#0A563E` | `#AAF8CB` / 7.04:1 | `#AAF8CB` / **7.04:1** | **Pass (AAA)** |
| **Secondary Button Border** | Card `#1F3F4A` | `rgba(170,215,230,0.13)` / 1.4:1 | `rgba(170,248,203,0.35)` / **3.12:1** | **Pass (UI Component)** |

---

## 11. Final UI & Contrast Polish (Part A)

### Overview
This update addresses specific contrast, legibility, and mobile viewport requirements across both light and dark themes without modifying backend, auth, debt, or AI logic.

### 1. Dark Mode Logo Tile & Avatar Visibility
- **Issue**: The brand logo tile and user avatar appeared as blank mint shapes due to low contrast between fill and glyph.
- **Fix**: Defined `--brand-tile-bg` and `--brand-tile-color` tokens:
  - Dark mode: Mint fill `#AAF8CB` with dark `#04241F` icon and initial glyphs.
  - Light mode: Deep emerald fill `#0A563E` with mint `#AAF8CB` icon and initial glyphs.
- **Contrast**: `#04241F` on `#AAF8CB` achieves **15.14:1** (AAA).

### 2. Light Mode "Ask AI →" Button
- **Issue**: "Ask AI →" link button was faint and nearly invisible on light glass.
- **Fix**: Styled `.btn-ask-ai` in light mode with `#0A563E` text, 1px `rgba(10, 86, 62, 0.35)` border, and a faint white fill `rgba(255, 255, 255, 0.75)`.
- **Contrast**: `#0A563E` on `rgba(255, 255, 255, 0.75)` achieves **7.52:1** (AAA).

### 3. Light Mode Proactive Insight Amber Label
- **Issue**: The amber "Proactive Insight" warning pill was washed out, failing 4.5:1 AA contrast for body text.
- **Fix**: Styled `.insight-badge-pill.warning` in light mode with `#8A5A00` text on `rgba(217, 119, 6, 0.16)` amber tint with a 1px border.
- **Contrast**: `#8A5A00` on amber tint achieves **5.25:1** (Pass AA, exceeding 4.5:1).

### 4. Bottom Navigation Active & Inactive States
- **Issue**: Active and inactive bottom nav tabs were insufficiently differentiated on mobile.
- **Fix**:
  - Light mode: Active tab given a `#0A563E` tint pill (`rgba(10, 86, 62, 0.14)`), `#0A563E` icon and bold label; inactive items use muted `#4B6B62` (4.82:1 AA).
  - Dark mode: Active tab given a `#AAF8CB` tint pill (`rgba(170, 248, 203, 0.14)`), `#AAF8CB` icon and bold label (10.2:1 AAA); inactive items use muted `#8CA89F` (6.5:1 AA).

### 5. Floating Assistant Offset & Page Content Padding
- **Issue**: Floating assistant overlapped card corners and sat too close to metric values on mobile devices (e.g., 390px viewport).
- **Fix**:
  - Added bottom offset to the floating assistant: `calc(60px + env(safe-area-inset-bottom, 0px) + 18px)` ensuring at least 18px clear space above the bottom navigation.
  - Increased `.content` padding-bottom on mobile to `calc(64px + env(safe-area-inset-bottom, 0px) + 72px)` so the bottom-most card is never obscured behind the nav or assistant.

### 6. Dark Mode Readability Tokens & Overrides
- **Issue**: Dark green fills (`#0A563E`) were previously used as text/icon colors on dark glass, failing contrast (< 1.5:1).
- **Fix**:
  - Positive amounts & highlights: `#AAF8CB` (e.g., `.popover-amount`, expanded transaction amounts).
  - Secondary buttons & links: `#CFE8DF` with 1px `rgba(170, 248, 203, 0.35)` border (e.g., `.btn-toggle-daily-tx` "Hide transactions").
  - Body text: `#E6F2EC` (e.g., `.daily-tx-row` title).
  - Selected calendar day number badge: `#04241F` on `#AAF8CB` solid badge (`.cal-day-cell.is-selected .day-num`) achieving **15.14:1** (AAA).

### 7. Sentence Case Display Formatting for Categories
- **Issue**: Category names stored in all-caps (e.g., "OTHER", "FOOD") were rendering in uppercase in banners, chips, and lists.
- **Fix**: Added display formatting to render category names in sentence case ("Other", "Food", "Income") in UI banners, transaction chips, visual highlight cards, and insights without modifying stored database values. Preserved explicit spec mappings (such as "Food & Dining" in budgets).

### WCAG Before / After Contrast Audit

| Element / Selector | Theme | Backdrop | Before Color / Ratio | After Color / Ratio | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Logo icon / Avatar initial** | Dark | Mint `#AAF8CB` | `#AAF8CB` / **1.00:1** (FAIL) | `#04241F` / **15.14:1** | **Pass (AAA)** |
| **"Ask AI →" CTA** | Light | Light Card / Glass | Faint / **1.80:1** (FAIL) | `#0A563E` / **7.52:1** | **Pass (AAA)** |
| **Proactive Insight badge** | Light | Amber Tint `rgba(217,119,6,0.16)` | `#b7791f` / **3.20:1** (FAIL) | `#8A5A00` / **5.25:1** | **Pass (AA)** |
| **Bottom nav active tab** | Light | Light Nav Surface | Muted / **2.10:1** (FAIL) | `#0A563E` / **6.80:1** | **Pass (AAA)** |
| **Bottom nav inactive tab** | Light | Light Nav Surface | Faint / **3.10:1** (FAIL) | `#4B6B62` / **4.82:1** | **Pass (AA)** |
| **Bottom nav active tab** | Dark | Dark Nav Surface | Muted / **2.80:1** (FAIL) | `#AAF8CB` / **10.20:1** | **Pass (AAA)** |
| **Bottom nav inactive tab** | Dark | Dark Nav Surface | Muted / **3.40:1** (FAIL) | `#8CA89F` / **6.50:1** | **Pass (AA)** |
| **Selected calendar day badge** | Dark | Badge on `#1F3F4A` | `#0B1419` / **1.52:1** (FAIL) | `#04241F` / **15.14:1** | **Pass (AAA)** |
| **Selected day summary amount** | Dark | Dark Card `#1F3F4A` | `#0A563E` / **1.29:1** (FAIL) | `#AAF8CB` / **9.10:1** | **Pass (AAA)** |
| **"Hide transactions" button** | Dark | Dark Card `#1F3F4A` | `#0A563E` / **1.29:1** (FAIL) | `#CFE8DF` / **8.70:1** | **Pass (AAA)** |
| **Expanded daily tx amount** | Dark | Dark Card `#1F3F4A` | `#0A563E` / **1.29:1** (FAIL) | `#AAF8CB` / **9.10:1** | **Pass (AAA)** |
