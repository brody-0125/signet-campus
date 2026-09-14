# Identity database recovery

Campus ownership depends on a stable OpenID Connect issuer and user subject. Restoring seed accounts or matching email addresses cannot recover that identity. Preserve the identity database, realm signing keys, client and broker configuration, external secrets, public issuer URL and the exact identity-server image together.

The isolated rehearsal uses the pinned Keycloak 26.3.3 image with PostgreSQL 17. Its PostgreSQL driver and database configuration are native [Keycloak capabilities](https://github.com/keycloak/keycloak/blob/26.3.3/docs/guides/server/db.adoc). The default Compose environment continues to use Keycloak's development file store; this fixture does not migrate existing accounts. Both environments use `start-dev` and local credentials, so neither is a production identity deployment.

## Run the rehearsal

Use a fresh fixture and stop any account-continuity fixture occupying ports 5183, 8083, 8084 or 8026. Run from the repository root:

```sh
npm ci --prefix web
npx --prefix web playwright install chromium
node dev/prepare-account-realm.mjs
docker compose -p signet-campus-identity-recovery -f compose.yml -f compose.accounts.yml -f compose.identity-recovery.yml up -d --build --wait web identity-restored-db
node --test dev/account-continuity.test.mjs
node --test dev/identity-recovery.test.mjs
```

The account test changes and verifies a personal address through Keycloak's actual account UI and local Mailpit. It also proves institutional sign-in and unlinking preserve the subject. The recovery test then:

1. Authenticates the changed account and reviewer, issues a badge, and records public identity keys and the exact signed credential.
2. Stops the fixture's only Keycloak writer and captures a custom-format `pg_dump` in process memory. No archive is written to the repository or printed.
3. Rejects empty and corrupt archives, restores into a separate PostgreSQL instance using `pg_restore --single-transaction --no-owner --no-acl`, and rejects a second restore over populated tables.
4. Recreates the same Keycloak image at the same public issuer URL using the recovered database, with realm import disabled.
5. Obtains fresh learner and reviewer tokens, compares subjects, issuer, audience, verified personal address, roles and public keys, reads the original exact credential, and issues and verifies a new credential.

This is a one-shot destructive exercise **inside the named disposable project**. Container labels and distinct source/target identities are checked before stopping or restoring anything. The application's database and badge signing volume remain in place during this identity exercise; the [application recovery rehearsal](RECOVERY.md) separately restores those assets.

After inspecting the exact project name, remove only this fixture, including its synthetic account data and copied identity secrets:

```sh
docker compose -p signet-campus-identity-recovery -f compose.yml -f compose.accounts.yml -f compose.identity-recovery.yml down -v
```

Run cleanup even after a test failure. Forced termination can leave the fixture stopped or using its recovered database; discard the fixture before repeating. Never substitute the primary project's name in this cleanup command. CI performs both account and identity recovery checks in the `accounts` job.

## Service recovery requirements

Quiesce every identity writer before taking the snapshot and keep the recovered store isolated until cutover. Use the same server version first; perform upgrades as a separate validated operation. Provision database ownership and grants explicitly because the rehearsal omits original ACLs. Protect and encrypt identity backups as credentials: they contain password hashes, realm private keys, broker secrets and account data. The in-memory test archive is capped at 64 MiB and is not an operator backup solution.

Preserve the public issuer, registered redirects, client audiences, external identity integrations and secret references. Validate fresh authentication, reviewer authorization, old badge ownership and new issuance before accepting traffic. A wrong issuer or regenerated subject requires correcting the restored configuration; do not compensate by matching users by email. Reconcile application and identity snapshot times so account creation after one snapshot cannot silently orphan newer application records.

This test requires a new login after recovery and does not guarantee continuity of existing browser or refresh-token sessions. Establish a deployment-specific session invalidation/re-authentication policy. Snapshot frequency defines the recovery point; measured restore, readiness and acceptance times define achievable recovery time. The fixture demonstrates a controlled cold recovery, not continuous WAL recovery, automatic failover, database high availability or a zero-downtime cutover.
