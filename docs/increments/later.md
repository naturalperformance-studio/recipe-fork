# Later — what the user asked for, not yet in an increment

Items the user raised at a demo and asked to keep for the future. Each names where it came from. When one goes into an increment, it moves there and leaves this list.

- *(→ v0.8, T-093)* **The camera full-screen, then a crop.** The in-app camera shows the whole sensor view full-screen; after the shot the author is offered a crop to the recipe's frame. (v0.6 demo, 2026-10-03.) Also fixes: the v0.6 viewfinder framed portrait instead of 4:3 landscape; a second tap during the permission dialog reads as a refusal; an upside-down phone gives an upside-down shot.
- *(→ v0.8, T-094)* **Entry actions in the ⋮ menu.** On the book's entries, own and imported, "Export" and "Delete"/"Remove" move into a ⋮ menu on the title line. (v0.6 demo, 2026-10-03.)
- **A left-handed drag column.** (v0.4 demo, 2026-10-02.)
- **Flaky `AndroidConnectivityTest`** ("a connection that is lost and returns…") timed out once. (T-088 review, 2026-10-02.)
- **A reference to part of an ingredient.** A step's reference may name a share of the ingredient's quantity, not only the whole — e.g. garlic split between the marinade and the sauce of one recipe; the share scales with the recipe like the whole does. (Asked 2026-10-03, during v0.8.) Touches the persisted reference (`step_reference`, the recipe file's document, a migration) and spec/ FLOW-031, FLOW-032, DOM-007, which say a reference renders the ingredient's whole quantity — a departure to fold back with `/req-forge --sync`.

