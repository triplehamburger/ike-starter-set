# Ike CQL Knowledge Maven Plugin

Turns the CQL keyword dictionary chapter into ledger source: one `compose(KnowledgeSet)` class
carrying a concept per CQL keyword, hung under the family and category the dictionary itself
assigns it.

Standalone by construction. It depends on the Maven 4 plugin API and nothing else — not
`ike-maven-plugin`, not `ike-terms`, not the tinkar authoring tier. It reads AsciiDoc and writes
Java; the knowledge set that Java composes onto is somebody else's build.

## The one command

```bash
mvn -pl ike-doc network.ike.foundation:ike-cql-knowledge-maven-plugin:1-SNAPSHOT:generate
```

Writes `target/generated-sources/ike-cql-knowledge/network/ike/foundation/ike/terms/cql/CqlKeywordSet.java`
and reports what it deferred. Nothing is authored into a ledger: the output is source, for a human
to review and place.

## Identity, and why it never drifts

A concept's identity is `uuid5(namespace, fullyQualifiedName)` — RFC 4122 version 5, SHA-1 over
the namespace's raw bytes then the name's UTF-8 bytes. That is the same function
`KnowledgeSet.concept(name)` applies internally (tinkar's `UuidT5Generator.get`), which
`CqlKnowledgeGeneratorTest` proves by reproducing a UUID published in `ike-terms`' own fidelity
test from the name and namespace alone.

Nothing else feeds a UUID: not the run, not the definition, not the example, not the order the
chapter happens to list keywords in. So:

- the same dictionary always regenerates byte-identical output;
- reordering the chapter changes nothing (emission is sorted, not source-ordered);
- **renaming a keyword mints a new concept.** A fully qualified name is an identity, not a label.
  Every derived UUID is therefore written into the generated source as a literal, so a rewording
  arrives in review as a changed identity rather than as a quietly duplicated concept.

Parents are cited in the generated source as `set.conceptRef(name)`, which the knowledge set
re-derives from **its own** namespace at compose time, while the concepts themselves carry the
literal UUIDs derived here. The two agree only while `namespace` equals the namespace of the set
the output is composed into, so the generated class records that constraint in its javadoc: compose
it elsewhere and every `.isA(...)` points at an identity no concept in the file declares.

## What is generated, and what is not

The dictionary's Komet status decides:

| Status                      | Count | What happens                                                        |
|-----------------------------|-------|---------------------------------------------------------------------|
| `Not yet in Komet`          | 115   | Minted.                                                             |
| `Implemented in Komet`      | 2     | Deferred — a Komet concept exists; minting again duplicates it.     |
| `Related concept in Komet`  | 5     | Deferred — the relationship is an editorial call, not a guess.      |

Each deferred entry names its Komet counterpart in a trailing note the parser does not read:
`Komet concept:` for the 2 implemented keywords (chapter lines 74 and 1326) and
`Related Komet concept(s):` for the 5 related ones (lines 931, 984, 1379, 1398 and 1434). Those
notes are knowingly dropped rather than silently lost — the goal says so in its log, and whoever
takes up the editorial call for the 5 reads them from the chapter.

A dictionary that yields nothing to mint fails the goal rather than writing a taxonomy root with
nothing under it, so pointing `dictionary` at the wrong chapter is an error and not a green build.

Deferrals are reported by name, never silently dropped. The 115 minted keywords sit under a
generated taxonomy of 1 root, 5 families and 16 categories, all read out of the dictionary's own
metadata lines: 137 concepts in total.

## Reading the dictionary strictly

Every entry that does not match the expected shape fails the build. A keyword that parsed *nearly*
becomes a concept with the wrong name, and a wrong name is a wrong identity that no later edit can
correct — so there is no tolerant path and no skip-and-continue tally.

Two traps in the real chapter are worth knowing about, because both look like nothing in review:

- **The metadata line has two forms.** 115 entries carry `` `Category` -- (Family > Subcategory)
  Status ``; 7 carry `` `Category` -- Status `` with no family path. Both parse; a third form
  fails, and so does the short form on an entry marked `Not yet in Komet`, since a keyword to be
  minted has nothing to hang from without its family path. The status is read from its wording, not
  from the AsciiDoc emphasis that distinguishes the three values in rendered output.
- **The title is not the bolded run inside it.** Six titles bold only part of the keyword
  (`=== *include*d in`, `=== *or* after`) and one bolds none of it (`=== such that`). Reading the
  bolded run would name five different keywords `or` — five concepts collapsing onto one identity.
  The keyword is the whole title with markup stripped, and every entry's anchor is checked against
  it, so a name read wrongly no longer matches its own anchor and the parse stops.

## Configuration

| Parameter                            | Default                                             |
|--------------------------------------|-----------------------------------------------------|
| `ike-cql-knowledge.dictionary`       | `${project.basedir}/src/docs/asciidoc/cql/keyword-dictionary.adoc` |
| `ike-cql-knowledge.outputDirectory`  | `${project.build.directory}/generated-sources/ike-cql-knowledge` |
| `ike-cql-knowledge.namespace`        | `d890e06f-ec35-429a-b541-d0ead19695e2` (the IkeFoundation set) |
| `ike-cql-knowledge.semanticTag`      | `CQL`                                               |
| `ike-cql-knowledge.rootName`         | `Clinical Quality Language`                         |
| `ike-cql-knowledge.rootParent`       | `IkeTerm.MODEL_CONCEPT`                             |
| `ike-cql-knowledge.packageName`      | `network.ike.foundation.ike.terms.cql`              |
| `ike-cql-knowledge.className`        | `CqlKeywordSet`                                     |
| `ike-cql-knowledge.stamp`            | `Ike.INCEPTION`                                     |
| `ike-cql-knowledge.dryRun`           | `false`                                             |
| `ike-cql-knowledge.skip`             | `false`                                             |

Changing `namespace`, `semanticTag` or `rootName` regenerates every identity below it. They are
parameters so the goal can target another set, not so they can be tuned.

## Testing

`mvn -pl ike-cql-knowledge-maven-plugin verify`. The tests run against the real chapter when
`ike-doc` is beside this module and skip themselves when it is not, so the plugin stays buildable
on its own.

The generated source is written against a `KnowledgeSet` API this module deliberately does not
depend on, so it cannot be compiled here. What is proven instead is that it *is* Java: javac is
asked to compile the output and every diagnostic must be a failure to resolve a type that was never
on the classpath. Anything else — an unbalanced brace, a string literal broken by an unescaped
quote out of the dictionary's own prose — fails the test.
