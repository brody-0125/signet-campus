# Local operational telemetry

Enable the optional local collectors alongside the Docker application:

```sh
docker compose -f compose.yml -f compose.telemetry.yml up -d --build web prometheus zipkin
node --test dev/management-ingress.test.mjs dev/telemetry.test.mjs
```

Prometheus is available at `http://localhost:9090` and Zipkin at `http://localhost:9411`. Both UI ports bind to host loopback. Prometheus scrapes the server's unpublished port 9091 on the dedicated telemetry network every five seconds. Application traffic continues through port 5173. The ordinary Compose configuration leaves tracing disabled and does not expose metrics.

The telemetry network uses a standard Docker bridge so Docker Desktop can reach the published loopback UIs. It is separate from the application network; the server joins both. Port 9091 is reachable by trusted containers that can reach the server, and is not an authenticated boundary within Docker. A service deployment must restrict management access with its network policy. Reviewer credentials do not grant management access through the application port. The HTTP and HTTPS ingress explicitly return 404 for `/actuator` paths.

## Requests, failures and privacy

Spring Boot's native HTTP, JVM and connection pool instrumentation feeds Prometheus. For example:

```promql
sum by (uri, status) (rate(http_server_requests_seconds_count{job="signet-campus"}[5m]))
```

Select service `signet-campus` in Zipkin to inspect completed HTTP requests. Valid W3C `traceparent` context is propagated into server traces. The local overlay samples all requests; choose an appropriate sampling policy for service operation. Spring Boot's standard logging correlation includes trace/span IDs where a log is emitted within a traced request. Telemetry does not add an access log or log request bodies.

The server retains route templates and response status, and omits the default high-cardinality URL attributes. It does not configure user identifiers, emails, evidence, credential JSON or authorization headers as observation tags. Runtime tests submit canary query/header values and authenticated requests, then inspect actual Zipkin spans and Prometheus series for leaks. Keep new custom observations low-cardinality and review their data before enabling them.

## Collector availability

The collector is not required for issuance. To exercise its outage locally:

```sh
docker compose -f compose.yml -f compose.telemetry.yml stop zipkin
node dev/smoke.mjs
docker compose -f compose.yml -f compose.telemetry.yml start zipkin
```

The smoke flow issues, exports, verifies and revokes badges with the collector stopped. Trace delivery is asynchronous and best-effort; events may be lost during an outage. Local collector storage is disposable and is not an audit ledger or credential backup. No remote collector or paid service is configured.

To return to ordinary local operation, stop the two collectors and recreate the server using only the base configuration:

```sh
docker compose -f compose.yml -f compose.telemetry.yml stop prometheus zipkin
docker compose up -d --force-recreate server web
```

This uses [Spring Boot's management port support](https://docs.spring.io/spring-boot/3.5/reference/actuator/monitoring.html), [Micrometer metrics](https://docs.spring.io/spring-boot/3.5/reference/actuator/metrics.html) and [OpenTelemetry tracing integration](https://docs.spring.io/spring-boot/3.5/reference/actuator/tracing.html). [Zipkin's Docker quickstart](https://zipkin.io/pages/quickstart.html) describes the local trace viewer.