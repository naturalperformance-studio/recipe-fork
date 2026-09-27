# The model has its own fifty dollars
**Status:** accepted, 2026-09-27. Qualifies CON-002 once the import feature is built.

**Context:** Import (section [`import`](../68-import/00-index.md)) needs a
language model on every import that is not already answered by an existing
recipe. CON-002 caps the whole product's recurring infrastructure at USD 20 a
month and requires a cost estimate before a feature is accepted into scope. Every
comparable product found parses with a model (MKT-F6), so there is no model-free
version of the feature.

**Decision:** The language model's cost is budgeted separately, at USD 50 per
calendar month, on top of CON-002's USD 20. When the month's model cost reaches
USD 50, every new import is refused until the month ends (IMPORT-020). The USD 20
of CON-002 is unchanged and still covers everything else.

**Consequences:**
- Buys: a hard ceiling that no number of accounts can break; the rest of the
  product keeps the budget it was designed against.
- Costs: the product's total recurring cost with import becomes up to USD 70 a
  month, and in a busy month imports stop before the month ends, for everyone at
  once. The one-source-one-recipe rule (IMPORT-004) and the remembered failures of
  IMPORT-012 are what stretch the fifty.
- The estimate CON-002 asks for is this ceiling: what it buys in imports depends
  on the model and provider engineering chooses, and is to be stated with that
  choice.

**Alternatives considered:**
- *A share of the existing USD 20.* Rejected by the product owner: import is a
  feature to grow into, not something to squeeze beside storage and attestation.
- *No product-wide ceiling, only the per-account daily limit.* Rejected: the cost
  would grow with the number of accounts, and nothing would protect any ceiling.
