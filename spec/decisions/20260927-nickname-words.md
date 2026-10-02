# A nickname is three English words from hand-kept lists

**Status:** accepted, writes AUTH-022 and AUTH-023

**Context:** A nickname is three generated words, unique, permanent and the only
public representation of an account, and no requirement said which words (SD-001).
Whoever wrote the lists would have decided it, irreversibly, at the first account.

**Decision:** Two adjectives and a noun, English, Latin letters, each capitalised,
separated by spaces — `Brave Copper Otter` — displayed the same in every interface
language (AUTH-022). The words come from two lists the product owner keeps outside
the application, admitting no word that is profane or insulting or that names a
real person, a brand, a nationality or ethnicity, a religion, a disease, a
sexuality or a feature of a person's body; combinations are not checked
(AUTH-023).

**Consequences:** A Russian-speaking author gets an English name, which a Russian
interface shows untranslated. The generator needs no grammar. The lists are one
more thing kept by hand, and they must exist before the first account.

**Alternatives considered:**
- *Words in the interface language at creation, or Russian always.* Rejected: a
  Russian adjective agrees with its noun in gender, which independent lists
  cannot guarantee.
- *CamelCase or lower-case hyphenated forms.* Rejected in favour of the form that
  reads most like a name.
- *A stop list of combinations.* Rejected: a list kept for ever against a rare
  case.
- *A published word list taken as is.* Rejected: nobody would have checked it
  against these criteria.
