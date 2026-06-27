# PRD-006: Frontend Design System

## Overview

This document specifies the visual design system for Prayer Link's frontend. It defines the colour palette, typography, animated background, component styles, layout system, CSS architecture, and accessibility requirements. The design is ethereal, light, and airy — inspired by sites like kynejang.com — with colourful animated backgrounds that feel minimal and evocative.

## Goals & Non-Goals

### Goals
- Establish a cohesive, ethereal visual identity for Prayer Link.
- Define all design tokens as CSS custom properties for consistency and maintainability.
- Specify every UI component with exact CSS values so implementing agents can build without ambiguity.
- Ensure mobile-first responsive design (320px to 1920px viewports).
- Meet WCAG 2.1 AA accessibility standards.
- Achieve 60fps animations on modern mobile devices.

### Non-Goals
- Dark mode (design tokens should make it easy to add later, but not implemented in MVP).
- Print stylesheet.
- IE11 or legacy browser support.
- A standalone component library package (components live in the project).

## Design Principles

1. **Ethereal & Airy**: The interface should feel like a peaceful, sacred space. Light colours, soft gradients, gentle motion.
2. **Minimal & Respectful**: Prayer is intimate. The UI should not be loud, flashy, or distracting. Every element has purpose.
3. **Alive & Responsive**: Subtle animations and micro-interactions make the interface feel living and responsive to the user.
4. **Accessible First**: The beauty must not compromise usability. Contrast ratios, keyboard navigation, and screen reader support are non-negotiable.

## Functional Requirements

### FR-1: Design Tokens (CSS Custom Properties)

All design values are defined as CSS custom properties in `styles/tokens.css`. Every component style references these tokens — no hardcoded values.

```css
:root {
  /* === Colours === */
  --color-text-primary: #1a1a2e;
  --color-text-secondary: #4a4a6a;
  --color-text-muted: #8888aa;
  --color-text-inverse: #ffffff;

  --color-accent: #d4a574;
  --color-accent-hover: #c4915f;
  --color-accent-light: rgba(212, 165, 116, 0.2);

  --color-surface: rgba(255, 255, 255, 0.6);
  --color-surface-solid: #ffffff;
  --color-surface-border: rgba(255, 255, 255, 0.2);

  --color-success: #4ade80;
  --color-success-bg: #f0fdf4;
  --color-error: #f87171;
  --color-error-bg: #fef2f2;
  --color-warning: #fbbf24;
  --color-warning-bg: #fffbeb;

  --color-backdrop: rgba(0, 0, 0, 0.4);

  /* Admin-specific */
  --color-admin-sidebar: #1a1a2e;
  --color-admin-sidebar-text: #ffffff;
  --color-admin-sidebar-active: var(--color-accent);
  --color-admin-bg: #f8f8fc;
  --color-admin-table-alt: #f3f3f8;

  /* === Typography === */
  --font-body: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  --font-display: 'Outfit', var(--font-body);

  --font-size-xs: 0.75rem;    /* 12px */
  --font-size-sm: 0.875rem;   /* 14px */
  --font-size-base: 1rem;     /* 16px */
  --font-size-md: 1.125rem;   /* 18px */
  --font-size-lg: 1.25rem;    /* 20px */
  --font-size-xl: 1.563rem;   /* 25px */
  --font-size-2xl: 1.953rem;  /* 31.25px */
  --font-size-3xl: 2.441rem;  /* 39px */
  --font-size-4xl: 3.052rem;  /* 48.8px */

  --font-weight-normal: 400;
  --font-weight-medium: 500;
  --font-weight-semibold: 600;
  --font-weight-bold: 700;

  --line-height-body: 1.6;
  --line-height-heading: 1.2;

  /* === Spacing === */
  --space-1: 0.25rem;   /* 4px */
  --space-2: 0.5rem;    /* 8px */
  --space-3: 0.75rem;   /* 12px */
  --space-4: 1rem;      /* 16px */
  --space-5: 1.25rem;   /* 20px */
  --space-6: 1.5rem;    /* 24px */
  --space-8: 2rem;      /* 32px */
  --space-10: 2.5rem;   /* 40px */
  --space-12: 3rem;     /* 48px */
  --space-16: 4rem;     /* 64px */

  /* === Border Radius === */
  --radius-sm: 8px;
  --radius-md: 16px;
  --radius-lg: 24px;
  --radius-full: 9999px;

  /* === Shadows === */
  --shadow-sm: 0 2px 8px rgba(0, 0, 0, 0.04);
  --shadow-md: 0 4px 16px rgba(0, 0, 0, 0.06);
  --shadow-lg: 0 8px 32px rgba(0, 0, 0, 0.08);
  --shadow-xl: 0 16px 48px rgba(0, 0, 0, 0.12);

  /* === Transitions === */
  --transition-fast: 150ms ease;
  --transition-base: 200ms ease;
  --transition-slow: 300ms ease-in-out;

  /* === Backdrop === */
  --blur-sm: 10px;
  --blur-md: 20px;
  --blur-lg: 40px;

  /* === Z-index Scale === */
  --z-background: -1;
  --z-base: 0;
  --z-dropdown: 100;
  --z-sticky: 200;
  --z-modal-backdrop: 300;
  --z-modal: 400;
  --z-toast: 500;
}
```

### FR-2: Typography

- **Google Fonts**: Load `Inter` (weights: 400, 500, 600) and `Outfit` (weights: 600, 700) via `<link>` in HTML `<head>`.
- **Base font size**: 16px on `<html>` element.
- **Heading styles**:
  - `h1`: `--font-display`, `--font-size-3xl`, `--font-weight-bold`, `--line-height-heading`. Mobile: `--font-size-2xl`.
  - `h2`: `--font-display`, `--font-size-2xl`, `--font-weight-semibold`. Mobile: `--font-size-xl`.
  - `h3`: `--font-display`, `--font-size-xl`, `--font-weight-semibold`. Mobile: `--font-size-lg`.
- **Body text**: `--font-body`, `--font-size-base`, `--font-weight-normal`, `--line-height-body`.
- **Small text**: `--font-size-sm`.
- **Caption text**: `--font-size-xs`, `--color-text-muted`.

### FR-3: Animated Background

The background creates a soft, watercolour-like effect using multiple semi-transparent gradient blobs that slowly drift across the viewport.

#### Implementation
- **Container**: `<div class="background-blobs">` positioned `fixed`, covering the full viewport, `z-index: var(--z-background)`, `overflow: hidden`.
- **Blobs**: 4-5 `<div>` elements, each a large circle (300-500px diameter) with a radial gradient fill.
- **Blob colours**:
  - Blob 1: Lavender `hsla(260, 60%, 80%, 0.5)`
  - Blob 2: Rose `hsla(340, 60%, 80%, 0.4)`
  - Blob 3: Peach `hsla(25, 70%, 80%, 0.5)`
  - Blob 4: Sky blue `hsla(200, 60%, 80%, 0.4)`
  - Blob 5: Mint `hsla(160, 50%, 80%, 0.3)`
- **Animations**: Each blob has a unique `@keyframes` animation:
  - `translate()` movement across a path (e.g., blob drifts from top-left to bottom-right and back).
  - `border-radius` morphing (between 40% and 60% on each corner) creates organic shape changes.
  - `scale()` subtle breathing (0.9 to 1.1).
  - Duration: 20-40 seconds per blob (each different to avoid sync).
  - Timing function: `ease-in-out`. Iteration: `infinite`. Direction: `alternate`.
- **Performance**:
  - Use only `transform` and `opacity` for animations (GPU-composited, no layout/paint).
  - Set `will-change: transform` on each blob.
  - Do NOT animate `width`, `height`, `top`, `left`, `background`.
- **Reduced motion**:
  ```css
  @media (prefers-reduced-motion: reduce) {
    .background-blobs * {
      animation-play-state: paused !important;
    }
  }
  ```

### FR-4: Component Specifications

#### Button (Primary)
```css
.btn-primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-4) var(--space-8);
  background: var(--color-accent);
  color: var(--color-text-primary);
  font-family: var(--font-body);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  border: none;
  border-radius: var(--radius-full);
  cursor: pointer;
  box-shadow: var(--shadow-md);
  transition: transform var(--transition-base), box-shadow var(--transition-base);
}
.btn-primary:hover {
  transform: scale(1.02);
  box-shadow: var(--shadow-lg);
}
.btn-primary:active {
  transform: scale(0.98);
}
.btn-primary:focus-visible {
  outline: 3px solid var(--color-accent);
  outline-offset: 3px;
}
.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}
```

#### Button (Secondary)
Same shape and sizing as primary, but:
```css
.btn-secondary {
  background: transparent;
  color: var(--color-accent);
  border: 1px solid var(--color-accent);
  box-shadow: none;
}
.btn-secondary:hover {
  background: var(--color-accent-light);
}
```

#### Button (Destructive)
Same shape, but red for dangerous actions:
```css
.btn-destructive {
  background: var(--color-error);
  color: var(--color-text-inverse);
}
```

#### Card
```css
.card {
  background: var(--color-surface);
  backdrop-filter: blur(var(--blur-md));
  -webkit-backdrop-filter: blur(var(--blur-md));
  border: 1px solid var(--color-surface-border);
  border-radius: var(--radius-lg);
  padding: var(--space-8);
  box-shadow: var(--shadow-lg);
}
```

#### Input / Textarea
```css
.input, .textarea {
  width: 100%;
  padding: var(--space-4);
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: var(--radius-md);
  font-family: var(--font-body);
  font-size: var(--font-size-base);
  color: var(--color-text-primary);
  transition: border-color var(--transition-base), box-shadow var(--transition-base);
}
.input:focus, .textarea:focus {
  outline: none;
  border-color: var(--color-accent);
  box-shadow: 0 0 0 3px var(--color-accent-light);
}
.input::placeholder, .textarea::placeholder {
  color: var(--color-text-muted);
  font-style: italic;
}
.input.error, .textarea.error {
  border-color: var(--color-error);
  box-shadow: 0 0 0 3px rgba(248, 113, 113, 0.2);
}
```

#### Badge (Prayer Count)
```css
.badge-prayer {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-3);
  background: var(--color-accent-light);
  border-radius: var(--radius-full);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
}
```

#### Prayer Pill (Floating Prayer Element)
```css
.prayer-pill {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  padding: var(--space-3) var(--space-4);
  background: var(--color-surface);
  backdrop-filter: blur(var(--blur-sm));
  border: 1px solid var(--color-surface-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  max-width: 200px;
  transition: transform var(--transition-base), box-shadow var(--transition-base);
}
.prayer-pill:hover {
  transform: scale(1.05);
  box-shadow: var(--shadow-md);
  border-color: var(--color-accent);
}
.prayer-pill.closed {
  opacity: 0.5;
  filter: grayscale(30%);
}
.prayer-pill__text {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
```

#### Toast Notification
```css
.toast {
  position: fixed;
  top: var(--space-4);
  right: var(--space-4);
  z-index: var(--z-toast);
  padding: var(--space-4) var(--space-6);
  background: var(--color-surface-solid);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xl);
  max-width: 400px;
  animation: toast-slide-in var(--transition-slow) ease-out;
}
.toast.success { border-left: 4px solid var(--color-success); }
.toast.error { border-left: 4px solid var(--color-error); }
.toast.info { border-left: 4px solid var(--color-accent); }

@keyframes toast-slide-in {
  from { transform: translateX(120%); opacity: 0; }
  to { transform: translateX(0); opacity: 1; }
}
```

#### Modal
```css
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: var(--color-backdrop);
  z-index: var(--z-modal-backdrop);
  animation: fade-in var(--transition-base);
}
.modal {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: var(--z-modal);
  width: min(90vw, 500px);
  animation: modal-pop var(--transition-slow);
}

@keyframes fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes modal-pop {
  from { opacity: 0; transform: translate(-50%, -50%) scale(0.95); }
  to { opacity: 1; transform: translate(-50%, -50%) scale(1); }
}
```

#### Loading Spinner
```css
.spinner {
  width: 24px;
  height: 24px;
  border: 3px solid var(--color-accent-light);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
```

### FR-5: Layout

#### Main Site Layout
```css
.page-main {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-4);
}
.content-narrow {
  width: 100%;
  max-width: 600px;
}
```

#### Admin Layout
```css
.admin-layout {
  display: grid;
  grid-template-columns: 250px 1fr;
  min-height: 100vh;
}
@media (max-width: 768px) {
  .admin-layout {
    grid-template-columns: 1fr;
  }
}
.admin-sidebar {
  background: var(--color-admin-sidebar);
  color: var(--color-admin-sidebar-text);
  padding: var(--space-6) 0;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
}
.admin-content {
  background: var(--color-admin-bg);
  padding: var(--space-8);
}
```

### FR-6: CSS File Structure

| File | Purpose |
|------|---------|
| `styles/tokens.css` | All CSS custom properties (colours, spacing, typography, shadows) |
| `styles/reset.css` | CSS reset/normalize. Box-sizing: border-box. Remove default margins/padding |
| `styles/base.css` | Base element styles (body, headings, links, lists) |
| `styles/components.css` | All component classes (buttons, cards, inputs, badges, pills, toasts, modals, spinners) |
| `styles/animations.css` | All `@keyframes` definitions and animation utility classes |
| `styles/layout.css` | Page layout classes, responsive breakpoints, grid utilities |
| `styles/admin.css` | Admin-specific styles (sidebar, tables, admin layout) |

**Import order** (in each HTML file's `<style>` or via JS imports):
```css
@import './tokens.css';
@import './reset.css';
@import './base.css';
@import './components.css';
@import './animations.css';
@import './layout.css';
/* admin.css only imported in admin.html */
```

## Non-Functional Requirements

| Requirement | Target |
|-------------|--------|
| Animation frame rate | 60fps on iPhone 12+ and equivalent Android |
| Total CSS size | < 30KB uncompressed |
| First Contentful Paint | < 1.5 seconds on 4G connection |
| Contrast ratio (body text) | ≥ 4.5:1 against lightest background |
| Contrast ratio (large text/headings) | ≥ 3:1 |
| Responsive breakpoints | 320px (small mobile), 768px (tablet), 1024px (desktop), 1440px (large desktop) |

## Accessibility Requirements

| Requirement | Implementation |
|-------------|---------------|
| Keyboard navigation | All interactive elements reachable via Tab. Focusable in logical order. |
| Focus indicators | `focus-visible` outline on all buttons, links, inputs. 3px solid accent colour. |
| Screen reader support | Semantic HTML (`<main>`, `<nav>`, `<section>`, `<article>`). ARIA labels on icon-only buttons. `role="alert"` on toasts. `aria-live="polite"` on dynamic content. |
| Reduced motion | `prefers-reduced-motion: reduce` pauses all animations. Transitions reduced to opacity-only. |
| Colour independence | Status is never conveyed by colour alone. Use text labels + colour (e.g., "Open" green badge, "Closed" grey badge). |
| Touch targets | Minimum 44x44px (WCAG), recommended 48x48px. |
| Form labels | All inputs have associated `<label>` elements. Placeholder text is not a substitute for labels. |

## Dependencies

| Dependency | Purpose |
|------------|---------|
| Google Fonts (Inter, Outfit) | Typography. Loaded via `<link>` tag. |
| No CSS framework | Pure vanilla CSS |
| No JS animation library | Pure CSS animations |

## Milestones & Acceptance Criteria

### Milestone 1: Design Tokens & Reset
- [ ] `tokens.css` contains all CSS custom properties as specified.
- [ ] `reset.css` normalises browser defaults.
- [ ] `base.css` styles headings, body text, and links.
- [ ] Google Fonts (Inter, Outfit) load correctly.

### Milestone 2: Animated Background
- [ ] Background blobs render and animate.
- [ ] Animation runs at 60fps on mobile.
- [ ] `prefers-reduced-motion` pauses animations.
- [ ] Background covers full viewport and sits behind all content.

### Milestone 3: Core Components
- [ ] Buttons (primary, secondary, destructive) match specs.
- [ ] Cards have glassmorphic appearance.
- [ ] Inputs and textareas have correct focus/error states.
- [ ] Badges display prayer count correctly.
- [ ] Prayer pills are styled with hover effects.
- [ ] Toasts slide in and auto-dismiss.
- [ ] Modals have backdrop and pop animation.
- [ ] Spinner animates smoothly.

### Milestone 4: Layout & Responsiveness
- [ ] Main site layout centres content correctly at all breakpoints.
- [ ] Admin layout has sidebar + content grid.
- [ ] Admin sidebar collapses on mobile.
- [ ] All components work from 320px to 1920px width.

### Milestone 5: Accessibility
- [ ] All text meets WCAG contrast requirements.
- [ ] All interactive elements are keyboard-navigable.
- [ ] Focus indicators are visible.
- [ ] Screen reader navigates the page logically.
- [ ] Reduced motion preference is respected.

## Open Questions

1. **Colour-blind accessibility**: The pastel palette may be challenging for some colour-blind users. Should we test with colour blindness simulators and adjust? **Recommendation**: Yes — test with Chromatic Vision Simulator and adjust any problematic colour pairs.
2. **Favicon and branding**: Should the design system include a favicon, logo, and app manifest? **Recommendation**: Yes — a simple praying hands emoji favicon (`🙏`) is sufficient for MVP. Full branding in a future phase.
3. **CSS-in-JS consideration**: If the admin panel becomes complex, should admin styles move to a scoped/modular CSS approach? **Recommendation**: Not for MVP. BEM naming conventions within `admin.css` are sufficient.
