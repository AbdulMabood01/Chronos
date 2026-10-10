# Landing page validation

Implemented public / landing page; authenticated visitors still redirect to /dashboard. Existing protected-route permission enforcement is unchanged.

- Production build passed using Vite's programmatic build with the existing React plugin and configFile:false. Standard CLI config bundling could not read Windows ancestor directories in this execution sandbox. The application bundle emitted successfully; the existing large-chunk warning remains.
- 24 existing tests passed in RoleRoutes.test.jsx and Login.test.jsx using Vitest's programmatic runner with the same React plugin.
- 9 Playwright checks passed in installed Chrome against the existing localhost:5173 dev server. Covered public login/plan/access navigation, desktop scroll chapters and chapter buttons, mobile at 320/390/760 pixels, FAQ expansion, reduced motion, authenticated / routing, configured media failure fallback, actual promo playback/seeking, and footer policy navigation.
- Desktop hero, pinned review demo, promo frame and mobile hero screenshots were visually inspected. Captures: landing-desktop.png, landing-demo-desktop.png, landing-film-desktop.png, landing-mobile.png.
- Rendered the existing promo-video ChronosPromo composition to a 30-second 1920 × 1080 H.264 MP4 with audio (5,543,317 bytes). Rendered a poster from frame 60. Both assets are included under frontend/public/media and copied into dist/media by the production build.
- Browser metadata confirmed 30 seconds and 1920 × 1080; muted playback advanced and seeking to 15 seconds succeeded. Promo poster and grouped footer screenshots were visually inspected at desktop/mobile sizes: landing-promo-desktop.png, landing-footer-desktop.png, landing-footer-mobile.png. Subjective audio quality was not assessed, and no captions were added because the promo has no narration.

See docs/landing-page.md for media configuration and repeatable browser test instructions. Changes were made in the existing checkout; no deployment was performed.

## Maxwell theme and continuous scrolling revision

The revised landing page uses the actual workspace.css brand tokens rather than the older styles.css palette. Hero captions, text, dividers and content sections share an alignment grid. Timed/remounted demo transitions were replaced with persistent scroll-driven layers, reversible product reveals and perspective movement on desktop and portrait mobile. Reduced motion and short viewports retain button-controlled normal flow. Eleven browser checks passed, including stable DOM surfaces during forward/reverse scrolling, matching brand tokens, and aligned section gutters. Desktop/mobile hero and demo captures are saved as landing-brand-desktop.png, landing-brand-demo.png, landing-brand-mobile.png and landing-brand-demo-mobile.png. Production build passed.

## Visible 3D motion revision

The product screens now rotate and move through true CSS perspective as a dimensional deck, with extruded edges, separate-depth sample-stat cards and orbital rings. The hero has a slowly rotating clock and pointer-responsive product layers. Text remains on its established alignment grid. Short desktop previews retain 3D; reduced motion and cramped mobile viewports use the accessible fallback. Browser coverage adds matrix-based depth/rotation checks, pointer response, reduced motion, and short desktop/mobile behavior. Evidence: landing-3d-hero.png, landing-3d-transition.png, landing-3d-demo.png, landing-3d-mobile.png.

Final visible-3D revision: production build passed; 13 browser checks passed. The short desktop layout was verified at 1440 × 600 with no horizontal overflow and the product copy contained in the viewport.
