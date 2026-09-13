# Local application

## Start

```bash
docker compose up -d --build web
```

Open the web interface at `http://localhost:5173`. The stack also exposes the API at `http://localhost:8080`, Keycloak at `http://localhost:8081`, and PostgreSQL on `localhost:5432`. Ports bind to the loopback interface. PostgreSQL and Keycloak data use named volumes.

The `key-init` service creates the issuer signing key once in the `signing-keys` volume. Keep this volume with the database when restarting or backing up the application. See [Credentials](CREDENTIALS.md) for key configuration and verification behavior.

The included Keycloak realm contains synthetic local accounts:

| Username | Password | Role |
|---|---|---|
| learner | local-learner-only | Learner |
| reviewer | local-reviewer-only | Reviewer |

These credentials and Keycloak's development mode are for local use. Deployments must provide their own identity provider and database credentials. The `campus-dev` client's password grant exists for local smoke tests; browser clients should use authorization code flow with PKCE.

## Verify the API

With Node.js 22 or later:

```bash
node dev/smoke.mjs
```

The check obtains real JWTs, submits evidence, rejects learner approval and invalid tokens, approves as a reviewer, and rejects a stale version. It prints the created submission ID. To verify persistence after restarting the server:

```bash
docker compose restart server
node dev/smoke.mjs <submission-id>
```

Wait for `GET /actuator/health/readiness` to return `UP` before running the check.

## Automated tests

```bash
docker compose --profile test run --rm tests
```

Tests use a separate `campus_test` database in `test-db`; they do not truncate the application database. The database integration suite refuses to reset a database with any other name. `test-db` uses temporary storage, while the application database persists in `campus-db`.

## Configuration

| Variable | Purpose |
|---|---|
| DATABASE_URL | PostgreSQL JDBC URL |
| DATABASE_USER | Database user |
| DATABASE_PASSWORD | Required database password |
| OIDC_ISSUER | Trusted JWT issuer URL |
| OIDC_JWKS | JWK endpoint accessible from the server |

Tokens must target the `signet-campus` audience. The `reviewer` realm role grants review permission. Health endpoints and the achievement catalog are public; submission endpoints require bearer authentication. The service uses stateless sessions and graceful shutdown. See [Web interface](WEB_INTERFACE.md) for browser authentication and frontend development.

```bash
docker compose stop
```

Stopping containers preserves application data. Avoid removing named volumes when retaining local credentials or evidence.
