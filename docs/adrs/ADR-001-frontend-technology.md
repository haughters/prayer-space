# ADR-001: Frontend Technology — Vanilla HTML/CSS/JS with Vite

## Status

Proposed

## Date

2026-06-26

## Context

Prayer Link is a prayer request platform that connects users seeking prayer with intercessors. The frontend consists of a small number of pages: a landing page with an animated background, a prayer submission form, a returning-user view showing floating prayer badges, and a hidden admin panel for managing prayer groups and contacts.

The UI design is intentionally minimal and ethereal — inspired by sites like [kynejang.com](https://kynejang.com) — with colourful animated backgrounds, simple forms, and responsive mobile-first layouts. The application does not require complex client-side state management, real-time data binding, or deeply nested component trees. The most interactive elements are CSS/canvas animations and a handful of form submissions.

Given this simplicity, adopting a full single-page application (SPA) framework would introduce unnecessary complexity, bundle size, and cognitive overhead. However, a modern development experience — fast hot module replacement (HMR), ES module support, and optimised production bundling — is still desirable to maintain developer productivity.

The admin panel (Application Admin and Prayer Group Admin) is part of the same frontend codebase, accessed via a hidden route. Its scope for MVP is limited to CRUD operations on prayer groups, group members, and a prayer dashboard.

## Decision

Use **vanilla HTML, CSS, and JavaScript** for the Prayer Link frontend, with **Vite** as the build tool and development server.

Specifically:

- **No SPA framework** (React, Angular, Vue) will be used for the MVP.
- **Vite** provides fast HMR during development, native ES module support, and tree-shaken production builds via Rollup.
- The frontend will be structured as **vanilla JS modules** using ES `import`/`export`, organised by feature (e.g., `prayer/`, `admin/`, `shared/`).
- CSS will be written as **vanilla CSS** with CSS custom properties for theming, avoiding preprocessors or utility frameworks.
- HTML animations and canvas-based backgrounds will be implemented with vanilla JS and CSS animations/transitions.
- The admin panel will be built within the same Vite project, sharing common utilities and styles with the user-facing pages.
- Vite's **multi-page app (MPA) mode** or client-side routing via vanilla JS will be used to serve different views.

## Consequences

### Positive

- **Minimal complexity**: No framework abstractions, no virtual DOM, no build-time JSX transforms. The codebase is approachable to any web developer.
- **Fast load times**: No framework runtime overhead. The production bundle will be significantly smaller than a React/Angular equivalent.
- **No framework lock-in**: Migrating to a framework later is straightforward since vanilla JS is the lowest common denominator.
- **Fast development feedback**: Vite's HMR is near-instant, even without a framework plugin.
- **Reduced dependency surface**: Fewer `node_modules` dependencies mean fewer security audit concerns and faster `npm install`.
- **Alignment with project ethos**: The UI is described as "minimal and respectful" — the technology choice mirrors this philosophy.

### Negative

- **Manual DOM manipulation**: Without a framework's declarative rendering, developers must manage DOM updates manually. This is manageable for the current scope but could become error-prone if the admin panel grows significantly.
- **No component model out of the box**: Reusable UI components must be implemented as plain JS functions or Web Components, requiring discipline to maintain consistency.
- **State management is ad-hoc**: For the MVP, state is minimal (device ID, prayer list). If complex state emerges (e.g., real-time prayer updates, multi-step forms), a lightweight state library or framework adoption may be warranted.
- **Testing**: Without a framework's testing ecosystem (React Testing Library, etc.), UI tests require more setup (e.g., JSDOM, Playwright).

### Reassessment Trigger

If the admin panel grows beyond simple CRUD — e.g., requiring real-time dashboards, complex form validation, drag-and-drop group management — the team should reassess whether a lightweight framework (Preact, Svelte, or Alpine.js) is warranted for the admin section specifically.

## Alternatives Considered

### React with Vite

- **Pros**: Rich ecosystem, component model, extensive testing libraries, large community, easy to hire for.
- **Cons**: Adds ~40–80 KB (minified + gzipped) of runtime overhead. Introduces JSX, virtual DOM reconciliation, and a learning curve for contributors unfamiliar with React. Overkill for a few pages with simple forms and animated backgrounds.
- **Verdict**: Rejected for MVP. The UI complexity does not justify the framework overhead.

### Next.js

- **Pros**: Server-side rendering (SSR), file-based routing, built-in API routes, excellent SEO support.
- **Cons**: Significantly heavier than Vite. Ties the frontend to a Node.js server runtime, conflicting with the project's architecture (static frontend + Java Spring Boot backend). SSR is unnecessary for a prayer submission form. Introduces React as a dependency.
- **Verdict**: Rejected. The project already has a dedicated backend in Spring Boot. A meta-framework adds unnecessary architectural coupling.

### Plain HTML without a Build Tool

- **Pros**: Zero tooling complexity. Files are served as-is.
- **Cons**: No ES module bundling for production (larger payloads, no tree-shaking). No HMR during development (manual browser refreshes). No CSS/JS minification without separate tooling. Managing multiple `<script>` tags becomes unwieldy as the codebase grows.
- **Verdict**: Rejected. The developer experience penalty is too high, and production optimisation requires a build step regardless.
