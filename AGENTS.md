# Project agent memory

This file is the project's committed home for project-intrinsic agent knowledge: build, test, release, architecture, and sharp-edge notes that should travel with the code.

- Add durable project-specific notes here as they are discovered through real work.

## Decentralized doc hierarchy (`ike-hierarchy-maven-plugin`)

Guide chapters (`ike-doc/src/docs/asciidoc/`) self-assemble via `:chapter-id:`/`:chapter-parent:`/
`:chapter-order:` headers rather than manual `include::` lists — see
`ike-hierarchy-maven-plugin/README.md`. `include::hierarchy:<id>[]` (implemented in
`ike-doc-extension`'s `HierarchyIncludeProcessor`) pulls in a chapter's **entire descendant
subtree transitively**, not just its direct children — `HierarchyIndex.descendantsOf` walks every
depth below the target until it hits a sibling or shallower node.

**Consequence:** only the topmost assembly root (e.g. `ike-guide.adoc`) should ever call
`include::hierarchy:<self>[]`. An intermediate chapter that itself has children (e.g. `cql.adoc`,
parent of the 8 CQL sub-chapters) must **not** also call `include::hierarchy:<its-own-id>[]` —
the root's own call already reaches those grandchildren directly, so the intermediate chapter
doing it too renders every descendant a second time. An intermediate chapter with children should
contain only its header and title; any of its own prose has to live in one of its children (e.g.
at the end of the last-ordered child) since it will render *before* the children's content, not
interleaved with it.

Every chapter fragment (any depth) must open with `= Title` (single `=`), never `==`/`===`, per
`.claude/standards/IKE-ASCIIDOC-FRAGMENT.md` — the assembly's computed `leveloffset` supplies the
real depth. A fragment's *internal* subheadings keep whatever depth they had relative to its own
`= Title`.

There's no Maven execution bound to `generate-resources` for `asciidoctor-maven-plugin` itself in
`ike-doc/pom.xml` (only the `ike-hierarchy-maven-plugin:index` goal is bound there) — asciidoctor
conversion runs off that plugin's own default phase binding when invoked, not an explicit
`<execution>`. To index only (no full render, no need for `ike-changeset`/koncepts.yml or
JDK 25): `mvn -pl ike-doc network.ike.foundation:ike-hierarchy-maven-plugin:index` (or `:validate`
for a dry run). The reactor requires JDK 25+ (`JAVA_HOME` to a 25+ JDK) and network access for
some unrelated modules (`ike-terms` → external `ike-knowledge-provider`/`tinkar` artifacts) that
isn't needed just to build/validate the doc hierarchy.

To actually *render* the real guide without that full build, run `ike-doc-extension`'s
`HierarchyRenderTest`: its `renderIkeGuideIfIndexExists`/`renderCqlDictionaryIfPresent` tests
convert the real `ike-guide.adoc`/`cql-dictionary.adoc` into `ike-doc/target/generated-docs/`
whenever `ike-doc/target/ike-hierarchy-index.json` exists (run the `index` goal first, else they
silently no-op). Diffing that HTML across a change is the way to prove a doc edit is render-neutral.
A chapter's location on disk carries no meaning (see the plugin README) — a pure `git mv` of a
chapter file changes only its `path` in the index.

## Knowledge-set identity (`ike-terms`)

Every identity in a knowledge set is `uuid5(setNamespace, conceptFullyQualifiedName)` — RFC 4122
version 5, SHA-1 over the namespace's raw bytes then the name's UTF-8 bytes, which is what tinkar's
`UuidT5Generator.get` computes and what `set.concept(name)` applies internally. The IkeFoundation
namespace is the UUID `Ike.SET` is built from (`Ike.java`, "never change it"). Checkable vector:
`uuid5(d890e06f-ec35-429a-b541-d0ead19695e2, "STAMP (IkeFoundation)")` is
`3f93c9fb-48c9-53e2-a3e7-a7ae39311b97`, the value `FoundationFidelityIT` records.

**Consequence:** the fully qualified name *is* the identity. Rewording an FQN does not rename a
concept, it mints a second one. Anything that authors concepts programmatically has to be
deterministic in its FQNs — see `ike-cql-knowledge-maven-plugin/README.md`, which does this for the
CQL keyword dictionary and writes each derived UUID into its output so a rewording shows up in
review as a changed identity.

The 71 `foundation/SectionN.java` files are generated *retrofit* source: their explicit SOLOR
UUIDs, their `IDENTIFIER_PATTERN` semantic and their `TINKAR_BASE_MODEL_COMPONENT_PATTERN`
membership all carry upstream provenance. New content does not copy those — no hand-authored set in
`ike-terms` uses base-model membership. The idiom for new concepts is the short one:
`set.concept(fqn).at(inception).synonym(...).definition(...).isA(...)`, with
`.statedAxioms(leb -> leb.NecessarySet(leb.And(leb.ConceptAxiom(parent), leb.SomeRole(role, ...))))`
when roles are needed — `leb.And` is varargs, so multi-clause axioms need no special handling
(`PatternShapeRefinementSet`, `CoordinateModelSet`).

`ike-terms` and `ike-changeset` cannot be built here at all: `chronology-store.version` is
`1.127.2-SNAPSHOT` and no configured repository serves it, nor `ike-knowledge-provider:1-SNAPSHOT`.
Modules that must work in this environment cannot depend on the tinkar authoring tier.

## Maven 4 plugin modules

`ike-hierarchy-maven-plugin` and `ike-cql-knowledge-maven-plugin` are the in-repo precedent: parent
POM, `maven-plugin` packaging, `maven.api.version`/`maven-plugin-tools.version` pinned in the module
(they must move together), an explicit `<goalPrefix>`, and a mojo that is nothing but wiring around
a Maven-free support class. maven-plugin-tools 4.0.0-beta-2 cannot map every `Lifecycle.Phase`
constant to a phase — `Phase.SOURCES` fails descriptor generation with "Could not find a matching
phase for sources", so use a Maven-3-named phase such as `Phase.INITIALIZE`.

## Maintaining this file

Keep this file for knowledge useful to almost every future agent session in this project.
Do not repeat what the codebase already shows; point to the authoritative file or command instead.
Prefer rewriting or pruning existing entries over appending new ones.
When updating this file, preserve this bar for all agents and keep entries concise.
