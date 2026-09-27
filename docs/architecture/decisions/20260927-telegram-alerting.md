# The alerting channel is a Telegram bot that only sends

**Status:** accepted
**Date:** 2026-09-27
**Requirements:** OBS-004, OBS-005, OBS-006, EXT-001, EXT-010

**Context:** The specification decided one outbound alerting channel, a messaging bot proposed by the product owner, carrying a signal's name and value only and accepting nothing back (OBS-004, OBS-005). Which messaging service was left open. The user confirmed Telegram on 2026-09-27.

**Decision:** Alerts are sent with the Telegram Bot API's `sendMessage` method, to one configured chat. The server never calls `getUpdates` and registers no webhook, so nothing sent to the bot can reach the product. The message body is built from a signal name and a number only. Delivery is attempted a bounded number of times and then abandoned (OBS-006). Sending sits behind an interface in the server, so the channel can be replaced without touching the signals.

**Consequences:**
- One-way by construction rather than by filtering.
- The bot token is a server secret, held in the server's environment.

**Alternatives considered:**
- *A bot that also reads messages.* Rejected by OBS-005.
- *Email.* Rejected in the specification's own decision on the alerting channel.
