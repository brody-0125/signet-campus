# Backup and recovery

Back up the application database, signing material, deployment configuration and identity provider as one recovery plan. A database snapshot alone cannot restore learner access or signing capability. Keep the original public origin, OIDC issuer and user subject identifiers: changing them creates different identities or invalidates the issuer identifiers used by existing credentials.

## Automated local rehearsal

Start the default HTTP Compose stack, build the current server image and create synthetic records with `node dev/smoke.mjs`. Then run from the repository root with Node 24 and Docker:

```sh
node --test dev/recovery.test.mjs
```

Run without concurrent application writes or key rotation. PostgreSQL's dump is consistent during concurrent writes, but the rehearsal compares it with separately collected table fingerprints. The rehearsal is for the default local Compose deployment, not an arbitrary production database. Its in-memory buffers are limited to 64 MiB; larger backups require a streamed operational procedure.

The check reads the running database, creates a custom-format dump, restores it into a separate PostgreSQL container and compares all public table rows, including evidence, review history, credentials, revocation, sharing, enrollments, outbox and migration history. Empty/corrupt input and restoring over populated tables must fail. It copies `/run/secrets` into a temporary volume, boots the same server image against the restored database, and verifies each credential's signature, lifecycle status and sharing availability. Existing retained public keys are copied with the active key, preserving old-key verification when present.

The temporary server has notifications disabled, no published ports and an internal Docker network without access to the local mail or identity services. The live database and key volume are never restore destinations. Containers, copied keys and network are removed on normal success or failure; a forced process termination can leave resources named `campus-recovery-<UUID>*`. Inspect exact names before removing leftovers. This rehearsal does not test identity-provider recovery, issuance after recovery, point-in-time recovery, or high availability.

## Create an operator backup

For a separate PostgreSQL-backed account-store restore that checks fresh authentication and new badge issuance, see [Identity database recovery](IDENTITY_RECOVERY.md).

The following commands use a POSIX shell in the repository root and the default local database credentials. For a service deployment, use its protected credentials and approved encrypted backup destination instead. Archive only trusted databases. Protect `secrets/` with restrictive access controls; on Windows use equivalent NTFS ACLs. Never commit or publish backups.

```sh
set -eu
umask 077
mkdir -p secrets
backup_dir=$(mktemp -d "$PWD/secrets/campus-backup.XXXXXX")
docker compose exec -T db pg_dump -U campus -d campus --format=custom > "$backup_dir/campus-backup.dump"
docker run --rm --network none --volumes-from "$(docker compose ps -q server):ro" \
  --mount "type=bind,src=$backup_dir,dst=/backup" --entrypoint sh node:24-alpine \
  -c 'umask 077; tar -czf /backup/signing-keys.tar.gz -C /run/secrets .'
```

Use a unique backup directory per snapshot and check every command's exit status before continuing. These files are not encrypted by the commands. Move them into access-controlled encrypted storage, record checksums and retention, and rehearse recovery before relying on them. Protect the active private key as a signing secret. Retain historical public verification keys; a newly generated key cannot verify older signatures. Pause key rotation while capturing the key set and its configuration.

Preserve `SIGNING_KEY_PATH`, `VERIFICATION_KEYS`, `CAMPUS_PUBLIC_URL`, the exact server image digest and deployment configuration separately. Back up the identity provider's database and configuration using its supported procedure, including stable user subjects and realm/client configuration. The development `dev/realm.json` seed is not a backup of accounts created or changed after import. Preserve external secret-manager/KMS references where applicable. `pg_dump` backs up one database; cluster roles and tablespaces require a separate operator procedure.

## Restore into a new environment

1. Provision a new PostgreSQL instance and empty database. Verify the target explicitly; never use `--clean` against the live database. Keep user traffic and notification delivery disabled.
2. Copy the trusted dump into that instance and use `pg_restore --single-transaction --no-owner --no-acl --dbname=<new-database> <archive>`. Run as the intended application owner. This fails on errors and rolls back that restore transaction. Recreate production ownership and least-privilege grants explicitly; the omitted ACLs are not restored automatically.
3. Restore the key archive into a new protected volume preserving ownership and permissions. Mount it read-only at `/run/secrets`; the app's UID 10001 must be able to read the configured active key and public set. Restore the original paths and stable public origin before startup. Do not run a key initializer as a replacement for missing signing material.
4. Start the matching application image against the new database with `NOTIFICATIONS_ENABLED=false`. Confirm readiness, Flyway history, record counts/content, existing credential verification, revocation and sharing. Check prior and current signing keys. Exercise a synthetic issuance and verify its result before accepting traffic.
5. Restore and validate the identity provider separately. Confirm the same learners still own their submissions and credentials; matching email addresses do not substitute for matching issuer/subject identity.
6. Reconcile pending outbox rows and delivery records before enabling mail. A snapshot may predate successful deliveries, so recovery can resend previously delivered notifications. Enable workers only after that reconciliation and the intended cutover.

Define backup frequency, retention, maximum acceptable data loss and recovery time for the actual service. This snapshot procedure does not provide continuous WAL archiving or automatic failover. Those require a deployment-specific design and measured recovery exercises.

PostgreSQL documents consistent snapshots and archive formats in [pg_dump](https://www.postgresql.org/docs/17/app-pgdump.html), and transactional restores in [pg_restore](https://www.postgresql.org/docs/17/app-pgrestore.html).
