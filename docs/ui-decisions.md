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



