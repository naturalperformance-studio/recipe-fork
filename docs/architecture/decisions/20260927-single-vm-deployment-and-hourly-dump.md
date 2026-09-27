# One virtual machine, and an hourly dump to object storage

**Status:** accepted
**Date:** 2026-09-27
**Requirements:** CON-002, QUAL-006, QUAL-008, EXT-003, EXT-011, EXT-012, OBS-001

**Context:** The whole product must cost at most USD 20 a month (CON-002). Availability of 95 per cent a month is the target, chosen against one server with no redundancy (QUAL-006). The store must be copied at least hourly, restorable into an empty environment, with 48 hourly and 30 daily copies kept (QUAL-008). The specification's availability reasoning cites Hetzner Cloud.

**Decision:**
- One Hetzner Cloud virtual machine of the smallest shared-CPU class that fits (about EUR 4–5 a month).
- Docker Compose runs three containers: the server, PostgreSQL 16 and Caddy (TLS with automatic certificates, reverse proxy).
- An hourly job runs `pg_dump` and stores the dump in the R2 bucket; retention keeps every copy of the last 48 hours and one a day for 30 days. The time of the last completed copy feeds the signal of OBS-001.
- The same Compose definition, with local settings, is how the server runs on a developer machine and in the hand-test artefact.

**Consequences:**
- Recurring cost is the machine plus R2 storage, well under the ceiling at launch size.
- A machine failure is an outage until it is rebuilt from the Compose definition and the latest dump; QUAL-006 accounts for that.
- The quarterly restore drill of QUAL-008 is an operational record, not code.

**Alternatives considered:**
- *Managed platform (serverless functions with a hosted database).* Rejected: cost of a hosted database, or ties to one provider's features (EXT-003) and a runtime that cannot run the shared Kotlin core.
- *Kubernetes.* Rejected: machinery for many machines on a product that has one.
