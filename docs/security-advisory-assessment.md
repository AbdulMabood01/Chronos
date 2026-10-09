# Spring WebMVC advisory applicability

Reviewed October 8, 2026 for Spring WebMVC 6.2.19. Re-review by November 8, 2026.

The dependency remains on an affected version. These are narrowly scoped, expiring application-specific assessments, not claims that the library is patched. All other HIGH/CRITICAL findings, secret scans, and required CI jobs continue to block merging.

- [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) requires XsltView and implicit view rendering. Chronos controllers are REST controllers; its catch-all retired routes throw HTTP 410 rather than rendering a view. No XsltView, XSLT resolver, or template engine is configured.
- [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) requires sending view fragments over Server-Sent Events. Chronos does not provide SSE or fragment rendering endpoints.

SecurityAdvisoryApplicabilityTest checks registered application handlers and view beans in the running Spring context. Adding a server-rendered or SSE endpoint requires removing/reviewing the corresponding assessment. CI also exports suppressed findings in its security artifacts.

Spring lists 6.2.20 as an enterprise-only fix and 7.0.9 as the open-source fix. Plan a separately validated Spring Boot 4 / Framework 7 migration or obtain the supported 6.2 patch before the assessment expires. Do not override Spring 7 underneath Boot 3 without a compatible platform migration.

## Automated review findings on PR 4

- The website-label regular expression now anchors both alternatives; embedded www. text no longer identifies an entire line as a website.
- Invitation ciphertext allocation uses Math.addExact instead of unchecked integer addition. The current callers also generate fixed-size tokens from 32 random bytes; they do not encrypt client-supplied payloads.
- CodeQL flagged confidential report list/detail and leave balance GET handlers as possible CSRF mutations. These handlers use stateless JWT bearer authentication (SecurityConfig); no cookie or HTTP session authentication is configured. A cross-site request cannot supply the bearer token automatically. The report handlers record authorized reads in audit history; leave balance handlers initialize/cache authorized allowance records. They do not submit, approve, reject, or change a policy. Actual workflow mutations use POST/PUT/PATCH/DELETE and independently enforce company access. These assessments must be revisited if cookie authentication is introduced.
