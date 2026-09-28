# Chronos local observability

This directory runs a local-only Grafana, Prometheus, Loki, Tempo, and Grafana
Alloy stack. The Spring Boot application and PostgreSQL continue to run directly
on the host. Nothing in this setup enables production telemetry by default.

## Prerequisites

- Docker Desktop (or Docker Engine with Compose v2)
- The normal Chronos Java, Maven, and PostgreSQL prerequisites
- Ports `3000`, `3100`, `3200`, `4317`, `4318`, `9090`, and `12345` free on
  localhost

All published container ports bind to `127.0.0.1`. Grafana uses anonymous Admin
access because this stack is loopback-only and disposable; no password or other
secret is stored in the repository. Do not expose this Compose stack publicly.

## Start

From the repository root:

```powershell
docker compose -f monitoring/compose.yaml up -d
docker compose -f monitoring/compose.yaml ps
```

Run the backend from `backend/` so its relative log path resolves to
`monitoring/logs/chronos.json`:

```powershell
cd backend
$env:JWT_SIGNING_KEY = '<your existing local Base64 key>'
$env:SPRING_PROFILES_ACTIVE = 'local-monitoring'
mvn spring-boot:run
```

Existing database environment variables still apply. The profile changes only
telemetry and Actuator configuration. To run from another working directory,
set `CHRONOS_LOG_FILE` to the absolute path of
`monitoring/logs/chronos.json`.

Open Grafana at <http://localhost:3000>. The provisioned **Chronos / Chronos
Overview** dashboard refreshes every ten seconds. Prometheus, Loki, and Tempo are
already provisioned as data sources.

## Generate and verify telemetry

Generate safe requests (including 401 responses) without sending tokens or
employee data:

```powershell
1..20 | ForEach-Object { Invoke-WebRequest http://localhost:8080/api/actuator/health | Out-Null }
Invoke-WebRequest http://localhost:8080/api/welcome -SkipHttpErrorCheck | Out-Null
```

Verify the application metrics endpoint directly:

```powershell
Invoke-WebRequest http://localhost:8080/api/actuator/health | Select-Object -Expand Content
Invoke-WebRequest http://localhost:8080/api/actuator/prometheus | Select-Object -Expand Content
```

Verify Prometheus has scraped Chronos:

```powershell
$query = [uri]::EscapeDataString('up{job="chronos"}')
Invoke-RestMethod "http://localhost:9090/api/v1/query?query=$query"
```

In Grafana:

1. Open **Explore**, choose **Loki**, and run
   `{application="chronos", environment="local"} | json`.
2. Choose **Tempo**, select **Search**, set Service Name to `chronos`, and run the
   search. Open a trace to see HTTP and sanitized JDBC child spans.
3. In a Loki log line containing `trace_id`, click the derived **View trace**
   link. From a Tempo span, use **Logs for this span** to return to Loki.
4. Open **Connections > Data sources** and use **Save & test** on Prometheus,
   Loki, and Tempo if a panel reports a data-source error.

The application log is ECS JSON. Alloy indexes only the bounded labels
`application`, `environment`, and `level`; trace IDs, logger names, URLs, and
messages remain JSON fields rather than high-cardinality labels.

## Stop or reset

Stop containers while preserving seven days of local telemetry in Docker
volumes:

```powershell
docker compose -f monitoring/compose.yaml down
```

To deliberately delete all local monitoring history as well:

```powershell
docker compose -f monitoring/compose.yaml down -v
```

Application log files under `monitoring/logs/` are ignored by Git and are not
removed by Compose.

## Troubleshooting

**Prometheus target is down**

- Confirm the backend was started with the `local-monitoring` profile and that
  <http://localhost:8080/api/actuator/prometheus> responds.
- Check <http://localhost:9090/targets>. The target must be
  `host.docker.internal:8080`.
- On Linux, Compose maps `host.docker.internal` through `host-gateway`. If the
  Docker daemon does not support that mapping, replace the Prometheus target
  with the host's bridge address.
- If `SERVER_PORT` or `SERVER_CONTEXT_PATH` differs, update the target or
  `metrics_path` in `prometheus/prometheus.yml`.

**No Loki logs**

- Confirm `monitoring/logs/chronos.json` exists and receives one JSON object per
  line. Start Maven from `backend/` or set `CHRONOS_LOG_FILE` explicitly.
- Inspect the shipper with
  `docker compose -f monitoring/compose.yaml logs alloy` and its local UI at
  <http://localhost:12345>.
- Inspect Loki with `docker compose -f monitoring/compose.yaml logs loki`.

**No Tempo traces**

- Confirm port `4318` is free and Tempo is healthy:
  `Invoke-WebRequest http://localhost:3200/ready`.
- The backend exporter must resolve to
  `http://localhost:4318/v1/traces`. Override it with
  `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` only when needed.
- Inspect `docker compose -f monitoring/compose.yaml logs tempo` and backend
  startup logs for exporter errors. The local profile samples all traces; a
  later production configuration should use an appropriate lower ratio.

**Missing database spans**

- Exercise an authenticated API that actually reaches a repository; `/welcome`
  has no database work.
- JDBC values and bind parameters are intentionally omitted. Do not disable the
  statement sanitizer or enable query-parameter capture: Chronos contains
  credentials and sensitive employee data.

**Grafana starts before a data source**

- Wait a few seconds and refresh. Provisioned data sources retry automatically.
- Check all containers with `docker compose -f monitoring/compose.yaml ps` and
  their logs with `docker compose -f monitoring/compose.yaml logs`.

## Production adaptation notes

Keep the profile separation, replace anonymous Grafana access with authentication,
add TLS and network controls, use durable object storage for Loki/Tempo, configure
retention and sampling to policy, authenticate telemetry endpoints, and move the
Prometheus scrape target from `host.docker.internal` to the server's private
application address. Never copy local credentials into these files.
