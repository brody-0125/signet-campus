# HTTPS deployment

The HTTPS Compose overlay runs a separate local deployment at `https://localhost:8443`. Nginx terminates TLS 1.2 or 1.3, serves the web application, proxies `/api/` to Spring Boot and `/auth/` to Keycloak. Only port 8443 is published to the host; database, API and identity ports remain internal to the Compose network. This setup exercises the TLS transport required by the public revocation protocol.

Use Docker Compose 2.24.4 or newer (the overlay uses `!override` and `!reset`). From the repository root:

```sh
docker compose -p signet-campus-tls -f compose.yml -f compose.https.yml up -d --build web
mkdir -p secrets
docker compose -p signet-campus-tls -f compose.yml -f compose.https.yml run --rm --entrypoint cat tls-init /certs/server.crt > secrets/tls.crt
```

The first run generates a self-signed localhost certificate, valid for 30 days, in the project's `tls-certificates` volume. Existing files are preserved; an incomplete pair or expired certificate stops initialization. The private key stays in that volume. `secrets/tls.crt` is the public certificate, and `secrets/` is excluded from Git. The initializer does not install trust on the host. To use a browser without a certificate warning, explicitly trust the exported certificate in your local development browser or provide a certificate that it already trusts.

The separate project name gives this deployment its own database, identity, signing-key and certificate volumes. Do not apply the HTTPS origin to an existing HTTP credential registry: changing issuer URLs would invalidate its configured issuer identity. The overlay starts with its own signing key and empty historical key set; configure its key paths explicitly when rotating keys in this deployment.

## Verification

Run the following in a POSIX shell:

```sh
export CAMPUS_TLS_CERT="$PWD/secrets/tls.crt"
export NODE_EXTRA_CA_CERTS="$CAMPUS_TLS_CERT"
export CAMPUS_API=https://localhost:8443
export CAMPUS_ISSUER=https://localhost:8443/auth/realms/signet-campus
node --test dev/https.test.mjs
node dev/smoke.mjs
```

In PowerShell, assign the same values using `$env:NAME = 'value'`; use `(Resolve-Path secrets/tls.crt).Path` for the certificate path. Wait until the API and realm discovery endpoints are ready if running these commands immediately after startup. Tests keep certificate and hostname verification enabled. They assert TLS 1.2/1.3 success, rejection of an untrusted certificate, wrong hostname and TLS 1.1, HTTPS discovery/login-form URLs and Secure authentication cookies. The smoke script obtains real development tokens and checks issuance, signature verification, revocation and the signed status URL through HTTPS.

Restart the TLS server with the same Compose arguments and run `node dev/smoke.mjs SUBMISSION_ID` using the printed submission ID to check persistence. Stop this deployment without deleting its data using `docker compose -p signet-campus-tls -f compose.yml -f compose.https.yml down`.

## Production configuration

Both proxy configurations use Docker's embedded DNS resolver (`127.0.0.11`) with a five-second cache and a two-second resolver timeout. API and identity service names are resolved during request handling, so replacing a container does not require restarting the web proxy. The destination names are fixed configuration values, not caller-supplied hosts. DNS refresh does not eliminate errors while a backend is unavailable or starting.

Run `node --test dev/proxy-resolution.test.mjs` to verify address replacement against both actual proxy configurations in an isolated Docker network. The test occupies the old backend IP, starts a replacement at a different address, and checks HTTP/API, HTTPS/API and HTTPS/auth requests without restarting Nginx. It also checks POST bodies, encoded query strings and forwarded headers. Certificate verification remains enabled.

When deploying outside Docker's embedded DNS network, replace the resolver address with the deployment's trusted DNS service. See Nginx's [resolver](https://nginx.org/en/docs/http/ngx_http_core_module.html#resolver) and [variable proxy destinations](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_pass) documentation.

This overlay uses development accounts, Keycloak `start-dev`, local passwords and a short-lived self-signed certificate. Before an actual service deployment, provision trusted certificates with renewal, production identity/database credentials and backups. Replace the localhost origin consistently in the frontend build argument, Keycloak hostname and client redirect/origin allowlists, server issuer/public URL, Nginx server name and forwarded port. Restrict proxy-header trust to the controlled ingress network. Keep the public origin stable after issuing credentials. Provision a renewed certificate/key pair in the mounted TLS location and reload or recreate Nginx; this initializer is not an automatic renewal service.

Internal service links in this overlay use HTTP on the private Compose network. Deployments requiring encryption between services must add that transport separately. The single-host overlay does not establish multi-node high availability or a complete production security configuration.

Configuration follows the [Nginx HTTPS guide](https://nginx.org/en/docs/http/configuring_https_servers.html) and [Keycloak reverse-proxy guidance](https://www.keycloak.org/server/reverseproxy). Keycloak's public hostname and `/auth` relative path match the proxy, which replaces forwarded scheme, host, port and client-address headers.
