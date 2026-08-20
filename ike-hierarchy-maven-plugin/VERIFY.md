# Verification checklist

Neither sandbox available to me can build this repo: the cloud container has Maven Central blocked
(HTTP 403), and the desktop bridge VM has JDK 11 and no network. So the build has not been run.
What *has* been verified, and what you need to run, is below.

## Verified here (executable)

The zero-dependency core was compiled with `javac -Xlint:all` (clean, no warnings) and exercised
against 81 assertions plus your real content:

| Check | Result |
|---|---|
| Cross-module discovery, excluded dirs, non-chapter files ignored | pass |
| `../../../../etc/passwd` traversal refused (read and write) | pass |
| Symlink escaping the root reported, not followed | pass |
| Duplicate id / dangling parent / cycle / orphan / depth violations | pass |
| Cycle reported once however many chapters lead into it | pass |
| Deterministic sibling ordering (order, then id) | pass |
| Stamper idempotent; re-placing rewrites rather than accumulates | pass |
| Attribute-looking lines inside listing blocks left alone | pass |
| Strict JSON rejects 10 malformed documents + depth/length limits | pass |
| Index round-trips and is byte-stable | pass |
| **Your migrated repo: 2 chapters, 0 violations, CQL renders `== CQL in Komet` at leveloffset=+1** | pass |
| **`decompose` dry run on your real 684-line guide: 21 chapters, all parse back, all unique, validates** | pass |

Typechecked against hand-written stubs of the exact API surface used (AsciidoctorJ 2.5, Maven 4
`org.apache.maven.api.*`, JUnit 5, AssertJ): all main and test sources compile.

## Run these

```bash
cd ike-starter-set
mvn -pl ike-hierarchy-model test          # 93 tests, the real verification
mvn -pl ike-hierarchy-maven-plugin install
mvn -pl ike-doc-extension install
mvn install                               # full reactor
```

Then the end-to-end loop:

```bash
mvn ike-hierarchy:validate                # should report the hierarchy is valid
mvn ike-hierarchy:add-chapter \
    -Dfile=ike-doc/src/docs/asciidoc/my-draft.adoc \
    -Dparent=ike-guide -Dorder=2000
mvn ike-hierarchy:add-chapter \
    -Dfile=ike-doc/src/docs/asciidoc/my-draft.adoc \
    -Dparent=ike-guide -Dorder=2000       # second run must report "nothing to do"
mvn -pl ike-doc install                   # my-draft now renders as a chapter of the guide
```

## If the plugin module fails to build

Two properties in `ike-hierarchy-maven-plugin/pom.xml`, and they are the likely cause:

```xml
<maven.api.version>4.0.0-rc-5</maven.api.version>
<maven-plugin-tools.version>4.0.0-beta-2</maven-plugin-tools.version>
```

`maven.api.version` must match the Maven 4 you actually run — the `org.apache.maven.api.*` API is
`@Experimental` and is not guaranteed compatible between release candidates. Maven 4.0.0 GA had not
shipped as of 2026-08-20; rc-6 is the newest. `maven-plugin-tools` must be 4.x, since only those
versions understand the v4 annotations.

## Things I changed that you should look at

- **`ike-doc-extension` now compiles at `release 25`, not 26 with `--enable-preview`.** It could not
  have compiled on the JDK your `.sdkmanrc` pins (25.0.4-tem). Neither module uses a preview
  feature. Note for the future: a Maven plugin must never be built with `--enable-preview`, because
  the classfile refuses to load unless Maven's own JVM is that exact JDK major and started with the
  same flag.
- **The plugin is named `ike-hierarchy-maven-plugin`, not `ike-doc-maven-plugin`.** The latter is
  already a reserved Tier-1 artifact of the `ike-docs` reactor per `IKE-NAMING.md`.
- **`_to_delete/` holds what this replaced** — `ike-federated-doc-extension/`, the two old
  processor classes, and its test. `device_bash` cannot delete on your machine, so I moved them
  there; remove the folder when you have reviewed the diff.
- **Left alone, now unused:** `import_chapter.py`, `test_import_chapter.py`, `cql/manifest.json`,
  `ike-guide-CQLv2.adoc`. All superseded, none referenced.
- **Typo spotted while decomposing:** `ike-guide.adoc` line ~350 reads `== EL++ Coxncepts`, which
  slugs to `el-coxncepts`. Worth fixing before you run `decompose` for real.
