# Account access

After signing in, open **Account**, then **Manage account**. The app opens the configured Keycloak account console. Use **Back to campus-dev** (or your deployment's client name) to return to Campus. Account settings include the sign-in details and security options enabled by the identity provider. Campus does not store passwords or provide its own email-change form.

Before losing access to a school or work address, arrange a durable sign-in method with the account administrator, review the account email, and complete the identity provider's verification process. Changing an email alone does not keep a disabled institutional account active. Signing in through a different provider or creating a different account does not transfer existing badges automatically.

## Ownership and email changes

Campus associates submissions, enrollments and credentials with the user's subject ID from its configured OIDC issuer. Keep that issuer and subject stable when changing login details. The current server expects UUID subjects, as supplied by the local Keycloak realm.

Changing the email on the same identity does not rewrite an existing signed credential. Its salted recipient identifier and proof remain unchanged; download the original document through the same owner account. The credential read API does not require a verified email, but the identity provider may require verification before allowing a fresh sign-in. New issuance requires a verified email. Another subject with a matching email receives no ownership rights.

Download JSON or PNG/SVG copies from **My submissions** or **Pathways** before an account becomes unavailable. Exports preserve signed data but do not restore access to private evidence or account controls. Sharing remains an explicit owner action, and any retained copy still needs current signature, expiry and revocation checks.

## Local configuration

The demo learner and reviewer need the built-in `account` client roles `manage-account` and `view-profile` to access their own Keycloak account console. These roles do not grant realm administration or Campus review privileges. The realm seed includes them, and `dev/smoke.mjs` checks authenticated access to the account endpoint for both demo users.

Keycloak imports the seed only when creating the realm. For an existing local realm, an administrator must assign these two client roles to the demo users through **Users → Role mapping → Assign role → Filter by clients**. Preserve user IDs and application data; deleting the realm or identity volume is not a migration procedure. If administrator access is unavailable, follow Keycloak's [temporary administrator recovery procedure](https://www.keycloak.org/server/bootstrap-admin-recovery), stop all nodes before the offline bootstrap command, and remove the temporary administrator after the repair.

The default demo realm has preverified synthetic users. It does not configure SMTP. Use the separate verification environment below to exercise real email changes with Mailpit. Keycloak 26.3.3 marks the separate `UpdateEmail` workflow as a technology preview; this application does not enable it.

## Local verified email flow

The account fixture runs its own database, keys, Keycloak and Mailpit under the Compose project `signet-campus-accounts`. The main demo's identities and records are not modified. With Node 24, npm and Docker Compose supporting `!override`, run:

```sh
npm --prefix web ci
cd web
npx playwright install chromium
cd ..
node dev/prepare-account-realm.mjs
docker compose -p signet-campus-accounts -f compose.yml -f compose.accounts.yml up -d --build web
node --test dev/account-continuity.test.mjs
```

On Linux, browser system dependencies may require `npx playwright install --with-deps chromium`. On Windows, an installed Edge can be selected with `CAMPUS_BROWSER_CHANNEL=msedge` in the test process environment. The fixture is available at `http://localhost:5183`, its Keycloak at `http://localhost:8084`, API at `http://localhost:8083` and Mailpit at `http://localhost:8026`. It overrides production signing-path settings with its own generated local key.

The generated realm enables email verification and routes SMTP only to its local Mailpit. Sign in as the demo learner, open **Account → Manage account**, change the email to a synthetic `@example.test` address and save. Return to Campus and sign in again; if prompted, enter the same username and password. Open the verification message in this fixture's Mailpit and follow the link. Once confirmed, a fresh login/token contains the verified new address and the same subject ID. Existing SSO state may take the user directly to verification rather than asking for the password again.

The stable Keycloak flow changes the address before its verification, and requires verification during the subsequent sign-in flow. This is not the preview workflow that retains the old address until confirmation. Already-issued access tokens can retain their original claims until refresh or expiry; changing an email is not instant revocation of those tokens. Preserve a durable username/password or another supported sign-in method and handle mistyped/lost email recovery through the identity provider.

The automated check changes only the isolated learner, confirms the delivered link in a real browser, checks the new token, retrieves the unchanged original credential and issues/verifies another credential. It can run again after successful completion. After an interrupted verification, reset only this disposable fixture before rerunning:

```sh
docker compose -p signet-campus-accounts -f compose.yml -f compose.accounts.yml down -v
```

That command deletes this fixture's data, mail and keys. Never substitute the main project name when retaining real work. Regenerate the fixture and start it again using the commands above. CI runs the same test with Chromium and removes the fixture afterward.

## Verification scope

Server integration tests check access with changed email claims on the same subject, denial for a different subject with the same email, rejection of unverified issuance and preservation of the original signed JSON. The isolated browser test covers actual email delivery/verification and fresh issuance. These checks do not prove institutional identity unlinking, account merging or migration between issuers.

The continuity guidance is informed by [Parchment's account-management documentation](https://community.instructure.com/en/kb/articles/663728-how-do-i-manage-my-parchment-digital-badges-account). Campus uses its own identity and ownership policy. See the version-pinned [Keycloak email-update documentation](https://github.com/keycloak/keycloak/blob/26.3.3/docs/documentation/server_admin/topics/login-settings/update-email-workflow.adoc) when choosing an email-change workflow.
