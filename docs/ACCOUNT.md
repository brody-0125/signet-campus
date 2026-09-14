# Account access

After signing in, open **Account**, then **Manage account**. The app opens the configured Keycloak account console. Use **Back to campus-dev** (or your deployment's client name) to return to Campus. Account settings include the sign-in details and security options enabled by the identity provider. Campus does not store passwords or provide its own email-change form.

Before losing access to a school or work address, arrange a durable sign-in method with the account administrator, review the account email, and complete the identity provider's verification process. Changing an email alone does not keep a disabled institutional account active. Signing in through a different provider or creating a different account does not transfer existing badges automatically.

## Create a learner account

On the Campus sign-in screen, select **Register**. Choose a username and password, enter your name and a personal email address, then submit the form. Open the confirmation message in local [Mailpit](http://localhost:8025) and follow its link. The identity provider requires email verification before completing sign-in. Never use a real recipient address in the local demo.

The new account can enroll in pathways, submit evidence and receive approved badges. It can manage its own profile through **Account → Manage account**. Registration does not grant the reviewer role. Reviewers are assigned separately by the identity administrator. The identity provider supplies the stable subject ID; knowing another learner's email or credential ID does not grant ownership.

For an existing local realm, enable **Realm settings → Login → User registration** in Keycloak and retain **Verify email** and the local SMTP configuration. Importing an updated seed does not modify an existing realm. Preserve users and their IDs; do not delete the identity volume to apply this setting. For a service deployment, choose the institution's registration policy and configure its approved email delivery, abuse controls and identity administration.

The disposable account fixture below also supports `node --test dev/registration.test.mjs`. That test creates a synthetic learner through the native registration form, verifies the delivered email, checks that unverified login is blocked, enrolls the learner, issues and verifies a badge, checks private ownership after fresh login, and rejects reviewer operations. CI runs it before the account continuity and identity recovery checks.

Self-registration uses the [Keycloak 26.3.3 registration setting](https://github.com/keycloak/keycloak/blob/26.3.3/docs/documentation/server_admin/topics/users/proc-enabling-user-registration.adoc). The personal-account flow is informed by [Bowdoin's pathway guidance](https://bowdoin.teamdynamix.com/TDClient/1814/Portal/KB/Article/157578/Understand-the-Digital-Badge-Learning-Pathway-Subscription-Email); Campus uses its own identity provider and access policy.

## Ownership and email changes

Campus associates submissions, enrollments and credentials with the user's subject ID from its configured OIDC issuer. Keep that issuer and subject stable when changing login details. The current server expects UUID subjects, as supplied by the local Keycloak realm.

Changing the email on the same identity does not rewrite an existing signed credential. Its salted recipient identifier and proof remain unchanged; download the original document through the same owner account. The credential read API does not require a verified email, but the identity provider may require verification before allowing a fresh sign-in. New issuance requires a verified email. Another subject with a matching email receives no ownership rights.

Download JSON or PNG/SVG copies from **My submissions** or **Pathways** before an account becomes unavailable. Exports preserve signed data but do not restore access to private evidence or account controls. Sharing remains an explicit owner action, and any retained copy still needs current signature, expiry and revocation checks.

## Local configuration

The demo learner and reviewer need the built-in `account` client roles `manage-account` and `view-profile` to access their own Keycloak account console. These roles do not grant realm administration or Campus review privileges. The realm seed includes them, and `dev/smoke.mjs` checks authenticated access to the account endpoint for both demo users.

Keycloak imports the seed only when creating the realm. For an existing local realm, an administrator must assign these two client roles to the demo users through **Users → Role mapping → Assign role → Filter by clients**. Preserve user IDs and application data; deleting the realm or identity volume is not a migration procedure. If administrator access is unavailable, follow Keycloak's [temporary administrator recovery procedure](https://www.keycloak.org/server/bootstrap-admin-recovery), stop all nodes before the offline bootstrap command, and remove the temporary administrator after the repair.

The default demo realm has preverified synthetic users, requires email verification and sends identity emails only to the local Mailpit service. After changing an address in account settings, sign in again and open its confirmation message at `http://localhost:8025`. Use synthetic addresses for local exercises. Keycloak 26.3.3 marks the separate `UpdateEmail` workflow as a technology preview; this application does not enable it.

For a realm created before these settings were enabled, an administrator must enable **Realm settings → Login → Verify email** and configure **Realm settings → Email** with host `mailpit`, port `1025`, sender `accounts@example.test`, display name `Signet Campus`, and authentication, SSL and StartTLS disabled. These values are only for the private local Compose network. Do not recreate users or manually mark a changed address as verified. Test delivery through the realm's SMTP test before relying on it. A service deployment must use its approved email service with appropriate authentication and transport security.

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

The generated realm inherits email verification and local SMTP settings from the default realm. Sign in as the demo learner, open **Account → Manage account**, change the email to a synthetic `@example.test` address and save. Return to Campus and sign in again; if prompted, enter the same username and password. Open the verification message in this fixture's Mailpit and follow the link. Once confirmed, a fresh login/token contains the verified new address and the same subject ID. Existing SSO state may take the user directly to verification rather than asking for the password again.

The stable Keycloak flow changes the address before its verification, and requires verification during the subsequent sign-in flow. This is not the preview workflow that retains the old address until confirmation. Already-issued access tokens can retain their original claims until refresh or expiry; changing an email is not instant revocation of those tokens. Preserve a durable username/password or another supported sign-in method and handle mistyped/lost email recovery through the identity provider.

The automated check changes only the isolated learner, confirms the delivered link in a real browser, checks the new token, retrieves the unchanged original credential and issues/verifies another credential. It can run again after successful completion. After an interrupted verification, reset only this disposable fixture before rerunning:

```sh
docker compose -p signet-campus-accounts -f compose.yml -f compose.accounts.yml down -v
```

That command deletes this fixture's data, mail and keys. Never substitute the main project name when retaining real work. Regenerate the fixture and start it again using the commands above. CI runs the same test with Chromium and removes the fixture afterward.

## Verification scope

Server integration tests check access with changed email claims on the same subject, denial for a different subject with the same email, rejection of unverified issuance and preservation of the original signed JSON. The isolated browser test covers actual email delivery/verification, fresh issuance and the institutional account flow below. These checks do not prove account merging, migration between issuers or compatibility with a particular institution's identity service.

## Institutional sign-in and departure

An institutional identity can be linked to an existing Campus identity when the Keycloak administrator enables that provider. While signed in to the existing account, open **Manage account → Account security → Linked accounts**, choose **Link account**, confirm **Continue**, and authenticate with the institution. This proves control of both accounts. Do not automatically merge accounts based on matching email addresses.

Before removing an institutional login, verify a personal email and test a durable sign-in method on the same account in a separate browser session. Then select **Unlink account** for the institution and sign in again with the durable method. The Campus issuer and subject must remain unchanged. Do not remove the whole Campus account. Unlinking is not a promise of immediate termination of existing sessions or access tokens; administrators must apply their session-revocation policy separately.

The disposable account fixture includes an `institution` realm, exposed as **Test institution**, with synthetic username `student` and password `local-student-only`. The generated confidential broker client uses the local-only secret `local-broker-only`, an exact callback URI, signature validation and the built-in first-broker-login flow. Browser authorization uses port `8084`; token and signing-key requests use the Keycloak container's port `8080`. No real institution or external email service is involved. These credentials and HTTP endpoints must never be used for a service deployment.

After the email verification check, the automated test links the learner through the native account console, opens a separate browser context and signs in through the institution. It verifies the same Campus subject and original badge, unlinks the institution through the console, then obtains a fresh local-password token and checks ownership and badge validity again. The main demo does not enable this synthetic provider. For a real deployment, configure the institution's approved OIDC provider, HTTPS endpoints, exact callback, protected client credentials and account-linking policy in the persistent identity service.

Keycloak documents [authenticated account linking](https://github.com/keycloak/keycloak/blob/26.3.3/docs/documentation/server_development/topics/identity-brokering/account-linking.adoc) and its required account roles. Campus delegates this flow to the native console rather than storing institutional passwords or implementing its own linking protocol.

The continuity guidance is informed by [Parchment's account-management documentation](https://community.instructure.com/en/kb/articles/663728-how-do-i-manage-my-parchment-digital-badges-account). Campus uses its own identity and ownership policy. See the version-pinned [Keycloak email-update documentation](https://github.com/keycloak/keycloak/blob/26.3.3/docs/documentation/server_admin/topics/login-settings/update-email-workflow.adoc) when choosing an email-change workflow.
