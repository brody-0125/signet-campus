# Enrollment notifications

The first pathway enrollment queues an email when the authenticated account has a verified email address. Recipient addresses cannot be supplied in the request body. Accounts without a verified address can still enroll; the service does not send a retrospective notification when the same enrollment is requested again later.

In local Docker, open [Mailpit](http://localhost:8025) to read captured messages. Mailpit accepts SMTP only on the Compose network and does not relay messages to external recipients. Its inbox is stored in the `mailpit-data` volume. The HTTPS overlay keeps Mailpit internal and does not publish its UI port.

```sh
docker compose up -d --build web
node dev/notifications-smoke.mjs
```

## Delivery guarantees

Enrollment and its outbox row are saved in one PostgreSQL transaction. A unique pathway/learner key prevents duplicate events from repeated or concurrent enrollment requests. A rolled-back enrollment leaves no event.

Enabled workers poll every two seconds, processing up to ten messages per poll. Each message is selected with `FOR UPDATE SKIP LOCKED`, so other workers can process different messages. A database transaction holds the selected row while SMTP runs. Connection, read and write timeouts are each five seconds. Each message has a stable UUID-based Message-ID.

Failures increment `attempts`, record only the exception class in `last_error`, and schedule exponential retry from five seconds up to one hour. The queue remains in PostgreSQL across restarts. Delivery success sets `sent_at`, clears `last_error` and removes the recipient address from the outbox. The captured email remains in Mailpit until removed under its retention settings; database and inbox backups may retain earlier data.

Delivery is **at least once**. If SMTP accepts a message but the database commit fails, a retry can deliver it again. A stable Message-ID helps identify duplicates but does not guarantee recipient-side deduplication. There is no automatic terminal-failure threshold; operators must investigate repeated failures.

## Configuration and operations

| Setting | Default | Purpose |
|---|---|---|
| `NOTIFICATIONS_ENABLED` | `false` outside Compose | Enable scheduled delivery; events are still queued when disabled |
| `SMTP_HOST` / `SMTP_PORT` | `localhost` / `1025` | SMTP transport; Compose uses `mailpit` |
| `NOTIFICATIONS_FROM` | `campus@example.test` | Sender mailbox |
| `CAMPUS_PUBLIC_URL` | `http://localhost:5173` | URL in the email |
| `campus.notifications.poll-ms` | `2000` | Delay between worker batches |

For an authorized deployment using an external provider, configure Spring Mail authentication and required TLS through deployment secrets and `spring.mail.properties`; local SMTP is intentionally unauthenticated inside the development network. Do not expose Mailpit publicly as a production mail service.

Inspect queue health without selecting recipient addresses:

```sql
SELECT count(*) AS pending, min(created_at) AS oldest_pending,
       max(attempts) AS largest_attempt_count
FROM notification_outbox WHERE sent_at IS NULL;

SELECT id, attempts, next_attempt_at, last_error
FROM notification_outbox WHERE sent_at IS NULL AND attempts > 0
ORDER BY next_attempt_at;
```

After correcting a delivery problem, allow automatic retry or move a specific event's `next_attempt_at` to `now()`. Sent records must not be reset: their recipient data has been removed. Review failed-recipient retention and completed-event cleanup policies before production deployment.

The SMTP integration uses [Spring Boot's mail support](https://docs.spring.io/spring-boot/reference/io/email.html). Local inbox configuration follows [Mailpit's Docker guidance](https://mailpit.axllent.org/docs/install/docker/).
