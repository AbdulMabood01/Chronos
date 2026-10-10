# Chronos landing page

Visitors opening / see the public product overview. Authenticated users opening / continue to /dashboard. Workspace routes still require authentication. The login wordmark links back to the overview.

The page includes a layered clock and sample workspace, a promo-film section, a three-chapter product demo, role-oriented feature summaries, onboarding steps, FAQs, plan links and sign-in/access actions. Preview records are illustrative data, not live company records.

## Promo video

The existing `promo-video` composition has been exported as a 30-second, 1920 × 1080 H.264 MP4 with audio and embedded in the film section. The video and poster ship in `frontend/public/media/` and work without environment configuration. To override the bundled assets, set these values in `frontend/.env.local` (or the deployment build environment):

```dotenv
VITE_PROMO_VIDEO_URL=/media/chronos-promo.mp4
VITE_PROMO_POSTER_URL=/media/chronos-promo-poster.png
VITE_PROMO_CAPTIONS_URL=/media/chronos-promo-en.vtt
```

All environment overrides are optional. No captions file is bundled; the current promo uses music and on-screen text without narration. Restart Vite after changing environment variables; rebuild for production. External media must allow browser loading; external captions need appropriate cross-origin support. The video uses native playback controls, plays inline, preloads metadata and does not autoplay. Failed media returns to the film frame with an unavailable message. The original promo source remains in `promo-video`.

The landing page ends with one branded footer containing Product, Legal, and Privacy & support columns. All eight policy/support pages remain linked. Other pages use the shared grouped footer without the landing-only product column.

## Motion and verification

The landing page inherits the active Maxwell workspace tokens: burgundy primary actions, navy type and bronze accents, with the same system typography. Headings, dividers and footer columns share a common page gutter. The demo sits immediately after the introduction. Desktop and portrait-mobile scrolling drives a continuous pinned Capture / Review / Connect sequence: all product surfaces remain mounted, the planes rotate by up to 72 degrees and travel up to 450 pixels back through CSS perspective as the next screen comes forward. Raised sample-stat cards and orbital rings add a separate depth layer. Text fades out before the next title appears, avoiding overlapping headlines. Scrolling backward reverses the presentation; chapter buttons move to positions on that timeline. Section reveals also follow scroll position rather than timed entrance animations. The hero includes a slowly rotating dimensional clock and pointer-responsive product planes. Reduced motion and short mobile viewports use simpler layouts with chapter controls; short desktop windows retain the 3D sequence. Scroll listeners are passive and scheduled through requestAnimationFrame; no animation library was added.

Browser coverage: frontend/browser-tests/landing.pw.cjs. Run npm run test:ui -- landing.pw.cjs with the existing dev-server configuration and installed Edge. Tests cover visitor navigation, authenticated root routing, desktop scroll chapters, mobile controls and FAQ at 320/390/760px, reduced motion, media error fallback, actual promo playback/seeking, and the grouped footer links.
