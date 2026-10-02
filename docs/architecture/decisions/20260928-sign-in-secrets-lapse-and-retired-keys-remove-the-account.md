# Keys are hashed random secrets; a sign-in secret lapses unused, and retired keys remove the account from other devices

**Status:** accepted — supersedes [20260927-keys-are-hashed-random-secrets.md](20260927-keys-are-hashed-random-secrets.md)
**Date:** 2026-09-28
**Requirements:** AUTH-007, AUTH-009, AUTH-010, AUTH-011, AUTH-013, AUTH-014, AUTH-015, AUTH-017, AUTH-019, AUTH-020, AUTH-021, AUTH-025, AUTH-027, EXT-006, EXT-008, QUAL-007, QUAL-009, RET-008

**Context:** AUTH-014 leaves the key format and protocol to engineering, bound by properties: keys never issued twice, unguessable at ten tries a day per source, nothing stored from which a key can be reconstructed or an account acted as, the check made at the server. QUAL-009 counts every presentation of an account key. A sign-in issues the device a secret the server minted, which is not a device identifier (AUTH-015 (4)), is deleted after six calendar months unused, and is replaced by a silent sign-in with the account key the device holds (AUTH-025). A device holds several accounts, and a key already used on it opens that account with no server (AUTH-011, AUTH-027). Presenting a recovery key signs out every other device, which then holds nothing of the account (AUTH-021 (3)).

**Decision:**
- Each of the three keys (account, deletion, recovery) is 256 bits from a cryptographically secure generator, written as Crockford base32 with a prefix naming its kind. The server stores only the SHA-256 digest of each key and looks keys up by digest.
- Signing in presents the account key once and exchanges it for a sign-in secret: 256 random bits, stored by the server as a SHA-256 digest beside the account and a last-presented date, and nothing else. Every other request carries the secret. A scheduled job deletes records not presented for six calendar months.
- The device keeps, for each account in its account list, the account key and the current sign-in secret, in the device database of `20260928-one-database-per-recipe-book.md`. Entering a key already in the list opens that account with no request (AUTH-027).
- A request answered "unknown secret" is followed by one silent sign-in with the held account key. If the key signs in, the new secret replaces the old one. If the attempt limit refuses it, the account stays on the device and behaves as with no connection until the QUAL-009 period ends (AUTH-025 (6)). If the key matches no account, its keys were retired: the device removes that account and everything it holds, as AUTH-013 removes one, with no confirmation, because AUTH-021 (3) requires it.
- Presenting a recovery key replaces all three digests and deletes every sign-in record of the account.
- Attempt limits are counters per source address in PostgreSQL over a 24-hour window, one counter shared by account and recovery keys (QUAL-009), one for deletion keys (QUAL-007); rows are purged within the log retention of RET-008.

**Consequences:**
- A full copy of the store yields digests of 256-bit random values, from which no key or secret can be recovered.
- The account key travels once per sign-in and once per lapse, not with every request.
- The device holds every account key of its account list; whoever holds the unlocked phone holds them, which the specification accepts (AUTH-012's cost).
- The key file of AUTH-007 carries the account key and the deletion key as text; the recovery key is never in it.

**Alternatives considered:**
- *Sending the account key with every request.* Rejected: each request would count under QUAL-009.
- *Asking the holder to enter the key again when a secret lapses.* Rejected: AUTH-025 (5) requires the sign-in to be silent.
- *An asymmetric key pair with a signed challenge.* Rejected: AUTH-014's properties hold with digests of high-entropy secrets.
- *A slow password hash.* Rejected: slow hashing protects low-entropy secrets; on 256 random bits it only costs CPU.
