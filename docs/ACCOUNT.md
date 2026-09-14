# Account access

After signing in, open **Account**, then **Manage account**. The app opens the configured Keycloak account console. Use **Back to campus-dev** (or your deployment's client name) to return to Campus. Account settings include the sign-in details and security options enabled by the identity provider. Campus does not store passwords or provide its own email-change form.

Before losing access to a school or work address, arrange a durable sign-in method with the account administrator, review the account email, and complete the identity provider's verification process. Changing an email alone does not keep a disabled institutional account active. Signing in through a different provider or creating a different account does not transfer existing badges automatically.

## Ownership and email changes

Campus associates submissions, enrollments and credentials with the user's subject ID from its configured OIDC issuer. Keep that issuer and subject stable when changing login details. The current server expects UUID subjects, as supplied by the local Keycloak realm.

Changing the email on the same identity does not rewrite an existing signed credential. Its salted recipient identifier and proof remain unchanged; download the original document through the same owner account. Existing owned credentials remain readable while an email is unverified. New issuance requires a verified email. Another subject with a matching email receives no ownership rights.

Download JSON or PNG/SVG copies from **My submissions** or **Pathways** before an account becomes unavailable. Exports preserve signed data but do not restore access to private evidence or account controls. Sharing remains an explicit owner action, and any retained copy still needs current signature, expiry and revocation checks.

## Local configuration

The demo learner and reviewer need the built-in `account` client roles `manage-account` and `view-profile` to access their own Keycloak account console. These roles do not grant realm administration or Campus review privileges. The realm seed includes them, and `dev/smoke.mjs` checks authenticated access to the account endpoint for both demo users.

Keycloak imports the seed only when creating the realm. For an existing local realm, an administrator must assign these two client roles to the demo users through **Users → Role mapping → Assign role → Filter by clients**. Preserve user IDs and application data; deleting the realm or identity volume is not a migration procedure. If administrator access is unavailable, follow Keycloak's [temporary administrator recovery procedure](https://www.keycloak.org/server/bootstrap-admin-recovery), stop all nodes before the offline bootstrap command, and remove the temporary administrator after the repair.

The default demo realm has preverified synthetic users. It does not configure SMTP or a complete self-service email-verification flow. Configure and test identity-provider email verification and account recovery before enabling real address changes. Use local Mailpit for synthetic tests. Keycloak 26.3.3 marks the separate `UpdateEmail` workflow as a technology preview; this application does not enable it.

## Verification scope

Server integration tests check access with changed email claims on the same subject, denial for a different subject with the same email, rejection of unverified issuance and preservation of the original signed JSON. Browser checks cover account-console navigation and responsive account guidance. These checks do not prove institutional identity unlinking, email delivery/verification, account merging or migration between issuers.

The continuity guidance is informed by [Parchment's account-management documentation](https://community.instructure.com/en/kb/articles/663728-how-do-i-manage-my-parchment-digital-badges-account). Campus uses its own identity and ownership policy. See the version-pinned [Keycloak email-update documentation](https://github.com/keycloak/keycloak/blob/26.3.3/docs/documentation/server_admin/topics/login-settings/update-email-workflow.adoc) when choosing an email-change workflow.
