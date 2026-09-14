# Web interface

The React application provides a public achievement catalog, evidence submission, learner review history, a reviewer queue, and feedback-driven resubmission. API data is managed by TanStack Query; Zustand holds the current workspace view. Vite builds the application, and Docker serves it through an unprivileged Nginx reverse proxy.

## Design system

The interface uses a white canvas, generous whitespace, flat surfaces and regular-weight Inter typography. Desktop content is centered within 1,200 px; mobile layouts stack the hero, achievement details and actions.

| Token | Value | Use |
|---|---|---|
| Primary | `#2457E6` | Actions, links, focus and selection |
| Primary hover | `#1944BC` | Hovered primary actions |
| Ink | `#171B23` | Text and footer |
| Muted | `#626B79` | Supporting text |
| Surface | `#F3F5F8` | Criteria and empty states |
| Border | `#D7DCE5` | Flat surface boundaries |
| Canvas | `#FFFFFF` | Page and controls |

Buttons and cards use an 8 px radius; dialogs use 14 px. Type ranges from 16 px controls to 80 px desktop headings, with weight 400 throughout. Blue, sky and silver 3D artwork is decorative and has empty alternative text. UI gradients and drop shadows are not used. Error messages include text and use a separate dark red semantic color.

Native dialogs provide focus containment, Escape dismissal and focus restoration. Controls have visible focus outlines, form fields have associated labels, async results use status/alert regions, and reduced-motion preferences disable transitions.

Learner submissions show the most recent work first. A successful resubmission returns to the first page; cancelling keeps the current page. Reviewers see the oldest pending submissions first.

## Authentication

The browser uses the Keycloak JavaScript adapter with authorization code flow and PKCE S256. Access and refresh tokens remain in adapter memory; they are not stored in local storage. Requests refresh expiring tokens before accessing submission endpoints. Signing out clears cached private API data.

The identity provider hosts the login screen. Application styling does not replace or collect identity-provider credentials. Browser configuration is supplied through `VITE_OIDC_URL`, `VITE_OIDC_REALM` and `VITE_OIDC_CLIENT` at build time. Register the deployed web origin and redirect URI with the identity provider.

## Development

Start the backend with `docker compose up -d --build server`, then run:

```bash
cd web
npm ci
npm run dev
```

Vite proxies `/api` to port 8080. Stop the Docker web service before starting Vite because both use port 5173. To serve the production bundle instead, run `docker compose up -d --build web` from the repository root.

```bash
npm test
npm run build
```

Component tests exercise submission success, failed-request evidence retention and version-conflict feedback. Backend tests enforce ownership, reviewer permissions and atomic writes; UI visibility is not an authorization boundary.

Dependency versions and integrity hashes are pinned in `web/package-lock.json`. Browser bundle notices are available at `/THIRD_PARTY_NOTICES.txt` and in [the dependency notices](../web/public/THIRD_PARTY_NOTICES.txt).

Public web builds require an explicit `VITE_OIDC_URL` to enable authentication. Without it, the page remains accessible and reports that sign-in is unavailable; it never falls back to the visitor's localhost. Vite development retains the local default, and Docker builds explicitly provide their identity URL. For public login, supply `VITE_OIDC_URL`, `VITE_OIDC_REALM` and `VITE_OIDC_CLIENT`, register the web origin and redirect URI on that provider, and rebuild. These are public client settings, not client secrets. Static Vercel hosting does not provision an identity service.

## Sample achievements

Explore offers three illustrative accessibility projects: accessible documents, keyboard-first navigation and meaningful image descriptions. Visitors start with **Sample achievements** and can open each project's criteria, sample evidence and review journey without signing in or contacting the API. **Live catalog** explicitly switches to real achievements; signed-in users default to the live catalog. Sample content cannot submit evidence or issue credentials.

The preview uses the existing blue primary, Inter typography, flat bordered cards and restrained cool tints. Cards form three columns on desktop and one on mobile. Details use the shared keyboard-accessible dialog with Escape dismissal. Time estimates and examples are illustrative rather than program requirements.
