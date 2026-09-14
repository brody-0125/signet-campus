# Replica and worker recovery rehearsal

This disposable local environment runs two real Spring Boot server containers against the same PostgreSQL database and signing-key volume. It uses separate identity, mail, database and key state from the main application.

```sh
node dev/prepare-replica-realm.mjs
docker compose -p signet-campus-replicas -f compose.yml -f compose.replicas.yml up -d --build web replica
node --test dev/replicas.test.mjs
```

The test waits for both APIs and the identity realm to become ready. Web is at `http://localhost:5186`; API instances are at ports 8086 and 8087, identity at 8088, Mailpit at 8027, and the test relay control at 8099. All published ports bind to loopback. The web ingress uses the first API; the test addresses each API directly to prove which process handled a request. This is a recovery rehearsal, not a load balancer or a production availability architecture.

## What the test proves

- Twelve concurrent issuance requests across both processes return identical signed JSON and leave one credential row. Concurrent reviews produce one success and one conflict.
- Archival racing issuance either preserves an already-issued award or pauses first issuance. Once archived, both instances reject first issuance while returning existing awards; restoration resumes the same achievement.
- Pathway completion requested on both instances produces one credential. Both can read and verify the shared records and use the same signing-key volume.
- The test SMTP relay forwards a real message to Mailpit and withholds its final success response. The test identifies the connected server by its container IP and kills only that server in the named disposable project.
- PostgreSQL releases the dead worker's uncommitted row lock. The surviving worker delivers the pending event and commits its sent state. The surviving API can issue and verify a new credential; restarting the killed instance preserves access to the original award.

The relay is a fixture for valid local SMTP conversations, not an email service. It holds one acknowledgment only when armed. Do not use it in a service deployment.

## Delivery semantics

The acknowledgment-loss exercise produces **two physical emails with the same logical Message-ID**. A stable ID does not make SMTP exactly-once. The crashed transaction leaves no committed attempt increment, so `attempts` becomes one after the surviving worker succeeds even though Mailpit has received two messages. Treat this field as committed worker attempts, not a count of all physical deliveries.

This verifies application behavior after a worker crash. It does not prove database failover, identity-provider recovery, automatic traffic rerouting, or regional disaster recovery. Those require their own deployment topology and recovery exercises.

## Cleanup

Stop and remove only this disposable project's state:

```sh
docker compose -p signet-campus-replicas -f compose.yml -f compose.replicas.yml down -v
```

Keep the explicit project name. The main application's database, identity and signing-key volumes are not part of this project.