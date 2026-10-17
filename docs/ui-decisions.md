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
