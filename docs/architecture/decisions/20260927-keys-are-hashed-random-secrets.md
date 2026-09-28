# Keys are hashed random secrets; a sign-in yields a device session

**Status:** superseded by 20260928-sign-in-secrets-lapse-and-retired-keys-remove-the-account.md
**Date:** 2026-09-27
**Requirements:** AUTH-007, AUTH-009, AUTH-010, AUTH-014, AUTH-017, AUTH-019, AUTH-020, AUTH-021, EXT-006, EXT-008, QUAL-007, QUAL-009, RET-008

**Context:** AUTH-014 leaves the key format and protocol to engineering, bound by properties: keys never issued twice, unguessable at ten tries a day per source, nothing stored from which a key can be reconstructed or an account acted as, and the check made at the server. QUAL-009 counts every sign-in attempt, successful or not, against ten a day per source. AUTH-021 must sign out every device holding the account.

**Decision:**
- Each of the three keys (account, deletion, recovery) is 256 bits from a cryptographically secure generator, written as Crockford base32 with a prefix naming its kind.
- The server stores only the SHA-256 digest of each key and looks keys up by digest.
- Signing in presents the account key once and exchanges it for a device session token: 256 random bits, stored by the server as a SHA-256 digest. Every other request carries the session token.
- Presenting a recovery key replaces all three digests and deletes every session of the account; other devices receive an authentication failure at their next request and wipe the account (AUTH-012).
- Attempt limits are counters per source address in PostgreSQL over a 24-hour window, one counter shared by account and recovery keys (QUAL-009), one for deletion keys (QUAL-007); rows are purged within the log retention of RET-008.

**Consequences:**
- A full copy of the store yields digests of 256-bit random values, from which no key can be recovered.
- The account key travels once per sign-in, not with every request, and ordinary requests do not count as sign-in attempts.
- The key file of AUTH-007 carries the account key and the deletion key as text; the recovery key is never in it.

**Alternatives considered:**
- *Sending the account key with every request.* Rejected: each request would be a sign-in attempt under QUAL-009, and the key would travel constantly.
- *An asymmetric key pair with a signed challenge.* Rejected: AUTH-014's properties already hold with digests of high-entropy secrets; the protocol adds complexity for no requirement.
- *A slow password hash (Argon2, bcrypt).* Rejected: slow hashing protects low-entropy secrets; on 256 random bits it only costs CPU.
