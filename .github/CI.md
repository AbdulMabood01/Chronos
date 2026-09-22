# Build and security checks

The workflow runs on pull requests, pushes to `master`/`main`, manual requests,
and every Monday at 07:23 UTC. It does not deploy anything.

Jobs run Maven tests against an isolated PostgreSQL 16 service, Vitest tests,
Playwright tests in Edge, CodeQL analysis for Java and JavaScript, and Trivy
checks for high/critical dependency vulnerabilities, secrets, and configuration
issues. Opt-in database tests are enabled when present. Trivy scans the packaged
backend JAR (including transitive libraries) and the frontend lockfile.

The build job stores the backend JAR, frontend `dist`, commit identity, and
SHA-256 checksums in a `chronos-build-<commit>-<attempt>` GitHub Actions artifact
for 30 days. Open a workflow run and scroll to **Artifacts** to download it.
Test and security reports are retained for 14 days.

Builds run independently of tests/scans so diagnostic artifacts remain available
when existing checks fail. An artifact's existence does not mean checks passed:
use the aggregate **CI checks** result. CodeQL findings appear in GitHub's
Security / Code scanning view; a successful CodeQL analysis means the scan ran,
not that it found no alerts. Enforcing CodeQL alert severity at merge requires a
repository code scanning ruleset. High/critical Trivy findings fail CI.

Actions use commit pins. No deployment credentials or custom secrets are needed.
The database credentials are only for the disposable CI service. The test JWT
key is randomly generated per run. Pull requests use `pull_request`, never
`pull_request_target`, with read-only checkout permissions.

To enforce checks before merging, select **CI checks** in the repository's branch
protection/ruleset settings. The workflow itself reports PR checks but does not
change branch protection. The build uses the source committed to GitHub;
uncommitted local work is not included.
