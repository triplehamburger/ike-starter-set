# ike:knowledge-generate — prototype

The inbound half of the `knowledge-*` goal family: read a document, emit ledger-form Java.
`ike:knowledge-export` replays a ledger outward; this reads the CQL keyword dictionary
(`ike-doc/src/docs/asciidoc/cql/keyword-dictionary.adoc`) and authors a
`compose(KnowledgeSet)` class in the shape `FoundationSet`'s sections have.

Staged in this repo for review. The target is
[`network.ike.tooling:ike-maven-plugin`](https://github.com/IKE-Network/ike-tooling),
which is why the sources sit in package `network.ike.plugin` and follow that plugin's
conventions rather than this repo's.

## What it produces

From 122 dictionary entries: 137 concepts — the root, 5 keyword families, 16 categories and
115 keywords — plus an identity report. Both committed under `src/test/resources/expected/` as the reviewable
output, and asserted byte-for-byte by `CqlKeywordGeneratorTest`.

### Why the axiom clause is `.isA(...)`

Each concept's single parent is emitted as `.isA(<target>)` rather than
`.statedAxioms(leb -> leb.NecessarySet(leb.And(leb.ConceptAxiom(<target>))))`. Not because one is
"the" hand-authored idiom — ike-terms is split almost perfectly, 100 `.isA` and 4 `statedAxioms`
across the top-level `*Set.java`, 0 and 375 across `foundation/Section*.java`, so each form *is*
the convention, in a different place. The reasons here are:

- **It is identity-neutral.** `ConceptBuilder.isA(ConceptFacade...)` and `statedAxioms(Consumer)`
  both call `ledger.addAxiomVersion(stamp, ...)` and neither declares an identity; for a single
  parent `isA` composes literally `NecessarySet(And(ConceptAxiom(parent)))`. Same axiom, same
  semantic, same stamp — and all 137 UUIDs are unchanged, which the committed identity report
  proves byte for byte.
- **It is materially smaller** — 137 fewer lines of a 728-line file. Generated output is read and
  reviewed by humans, so the noise it does not carry is real value.
- **Matching `FoundationSet` means matching the concept *shape*** — identity, descriptions,
  identifier, axiom, membership, dialect — not which equivalent API expresses the axiom clause.

Worth knowing alongside: every one of those 375 `Section*.java` calls pins an explicit
`PublicIds.of(...)` and no hand-authored one does. The long form is what *reproducing pre-existing
semantic identities* requires — a constraint this generator does not have, since it mints new ones.

## Determinism

A concept's identity is `UUIDv5(set namespace, fully qualified name)`. Re-wording a concept
therefore mints a *different* concept, so the generator never lets prose reach a name:

- A keyword's name comes from the keyword text, which the reader has already cross-checked
  against the entry's stable `[[term-...]]` anchor. Category and family names come from the
  dictionary's fixed taxonomy vocabulary. Definitions and CQL examples — the parts an editor
  rewords — feed descriptions only.
- Output order is imposed by sorting, so reordering the dictionary changes nothing.

`CqlKeywordSetGenerator.nameUuid` is pinned to tinkar's `UuidT5Generator.get` by a test over a
known namespace/name/UUID triple from the shipped ledger, so the reported identities are the ones
the DSL will actually author.

## Idempotency

The dictionary's Komet status decides what may be minted, and nothing is dropped silently:

| Status | Count | Treatment |
|---|---|---|
| `Not yet in Komet` | 115 | minted |
| `_Related concept in Komet_` | 5 | withheld — which concept to link is a human call |
| `*Implemented in Komet*` | 2 | withheld — already in the ledger; minting would duplicate it |

The 7 withheld entries name their Komet counterparts (`Komet concept: *And*`), and the identity
report carries those names.

## Failing rather than degrading

Every entry must match the grammar in `CqlKeywordDictionary`'s javadoc; anything else throws with
the offending line number and fails the build. In particular the reader enforces, rather than
assumes, the two shapes most likely to be mis-read: the metadata line's two forms (115 entries
carry `(Family > Subcategory)`, 7 do not), and the heading's uneven bold markup
(`=== *include*d in`, `=== such that`), which is why the keyword is cross-checked against the
anchor.

## Running it

```
mvn -pl ike-cql-generator-prototype install
mvn -pl ike-cql-generator-prototype \
  network.ike.foundation:ike-cql-generator-prototype:1-SNAPSHOT:knowledge-generate \
  -Dike.knowledgeGenerate.dictionaryFile=../ike-doc/src/docs/asciidoc/cql/keyword-dictionary.adoc \
  -Dike.knowledgeGenerate.packageName=network.ike.cql.terms \
  -Dike.knowledgeGenerate.namespace=d890e06f-ec35-429a-b541-d0ead19695e2 \
  -Dike.knowledgeGenerate.semanticTag=IkeCql \
  "-Dike.knowledgeGenerate.rootConceptName=CQL keyword (IkeCql)" \
  -Dike.knowledgeGenerate.rootParentExpression=network.ike.foundation.ike.terms.IkeTerm.MODEL_CONCEPT \
  -Dike.knowledgeGenerate.stampExpression=network.ike.cql.terms.IkeCql.INCEPTION
```

## Lifting into ike-maven-plugin

1. Copy `src/main/java/network/ike/plugin/{KnowledgeGenerateMojo,CqlKeywordDictionary,CqlKeywordSetGenerator}.java`
   into `ike-maven-plugin/src/main/java/network/ike/plugin/`.
2. Add a `KNOWLEDGE_GENERATE` constant to `IkeGoal` beside `KNOWLEDGE_EXPORT`, with its
   `NAME_KNOWLEDGE_GENERATE = "knowledge-generate"` mirror.
3. Replace `KnowledgeGenerateMojo.GOAL` with `IkeGoal.NAME_KNOWLEDGE_GENERATE` and delete the
   constant.
4. Carry `CqlKeywordGeneratorTest` over, minus the tests that read this repo's dictionary.

Nothing else moves: the goal has no store dependency, no SPI seam, and no fork — it emits text,
and the module it generates into is what compiles against the chronology builders.

## Naming, and what is deliberately not here

**The naming scheme is settled.** Names are `<name> (IkeCql)` — `after (IkeCql)`,
`Timing Operator (IkeCql)` — matching the set convention, and the root is
`CQL keyword (IkeCql)` hanging off `IkeTerm.MODEL_CONCEPT`. That was chosen with the consequence
stated: because identity derives from the name, changing the tag re-mints all 137 concepts, so
it is permanent once anything is authored. The root is authored by this goal rather than merely
referenced, so the generated set closes over every name it mentions except its parent — a term
from outside the set, referenced by constant rather than by name.

**The namespace is settled too, and it is IkeFoundation's.** All 137 identities are minted under
`d890e06f-ec35-429a-b541-d0ead19695e2` — the namespace `Ike.SET` declares and its own javadoc
calls *the permanent type-5 namespace*. There is exactly one such namespace in this repo and no
precedent for a second, and these concepts are authored *into* IkeFoundation's set: they are
already rooted under `IkeTerm.MODEL_CONCEPT`. A separate CQL namespace would need its own
permanent UUID decision that nothing yet justifies. So the identities in
`src/test/resources/expected/cql-keyword-identities.txt` stand as minted; nothing moves.

`IkeCql` is a *naming* choice within that one namespace, not a separate identity space. The
semantic tag suffixes names and `stampExpression` points at `network.ike.cql.terms.IkeCql`, but
neither implies a CQL-owned namespace — identity is `UUIDv5(IkeFoundation namespace, fqn)` for
every concept here, exactly as it is for `Stamp coordinate properties (IkeFoundation)`.

Deliberately out of scope for this first step:

- **The 5 `Related concept in Komet` keywords are excluded**, deferred unminted. Linking them
  means choosing a relationship to a named Komet concept per entry, which is a modelling
  decision rather than a mechanical one. The identity report lists them with the counterparts
  the dictionary names.
- **The CQL examples are parsed but not authored.** Giving each keyword its example needs a
  CQL-example pattern, which does not exist yet. The reader validates the example block — a
  missing one fails the build — but the generator emits nothing for it.
- **The generated source is not compile-verified here.** `dev.ikm.tinkar:entity` at the version
  this reactor pins does not resolve in this environment, so the emitted chain is checked by the
  committed golden file, a structural balance test, and comparison against the hand-authored
  chains in `ike-terms`' `CoordinateModelSet` and `PatternShapeRefinementSet` — which is also
  where `.isA(IkeTerm.MODEL_CONCEPT)` and `.isA(set.conceptRef("..."))`, the two target forms
  emitted here, are shown to be accepted — not by compiling it.
