/*
 * Copyright © 2026 IKE Network (support@ike.network)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package network.ike.plugin;

import network.ike.plugin.CqlKeywordDictionary.Entry;
import network.ike.plugin.CqlKeywordDictionary.MalformedEntryException;
import network.ike.plugin.CqlKeywordDictionary.Status;
import network.ike.plugin.CqlKeywordSetGenerator.Generated;
import network.ike.plugin.CqlKeywordSetGenerator.Options;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** Covers the dictionary reader's strictness, the generator's output, and identity determinism. */
class CqlKeywordGeneratorTest {

    /** The dictionary this prototype generates from. */
    private static final Path DICTIONARY =
            Path.of("..", "ike-doc", "src", "docs", "asciidoc", "cql", "keyword-dictionary.adoc");

    /**
     * The IkeFoundation set's namespace, as declared in ike-terms' {@code Ike.SET} — and
     * deliberately the CQL set's namespace too. The {@code IkeCql} tag names concepts within this
     * one namespace rather than opening a second identity space; see the README.
     */
    private static final UUID IKE_FOUNDATION =
            UUID.fromString("d890e06f-ec35-429a-b541-d0ead19695e2");

    private static final Options OPTIONS = new Options(
            "network.ike.cql.terms", "CqlKeywordSet", IKE_FOUNDATION, "IkeCql",
            "CQL keyword (IkeCql)", "network.ike.foundation.ike.terms.IkeTerm.MODEL_CONCEPT",
            "network.ike.cql.terms.IkeCql.INCEPTION");

    // ── Identity ────────────────────────────────────────────────────────────

    /**
     * Pins the generator's UUID derivation to tinkar's. {@code Ike.SET} declares the IkeFoundation
     * namespace and {@code CoordinateModelSet} authors "Stamp coordinate properties
     * (IkeFoundation)" with no explicit identity, so the UUID the shipped ledger resolves that
     * name to — quoted in FoundationFidelityIT — is exactly what
     * {@code UuidT5Generator.get(namespace, fqn)} returns. Matching it proves this generator
     * reports the identities the DSL will actually author, without depending on a store runtime.
     */
    @Test
    void mintsTheSameUuidTinkarDoes() {
        assertThat(CqlKeywordSetGenerator.nameUuid(
                IKE_FOUNDATION, "Stamp coordinate properties (IkeFoundation)"))
                .hasToString("0edae285-236b-5e13-90a1-2eb7db9d2879");
    }

    /** Identity follows the name, and only the name — the trap this generator has to avoid. */
    @Test
    void rewordingANameMintsADifferentConcept() {
        assertThat(CqlKeywordSetGenerator.nameUuid(IKE_FOUNDATION, "after (IkeCql)"))
                .isEqualTo(CqlKeywordSetGenerator.nameUuid(IKE_FOUNDATION, "after (IkeCql)"))
                .isNotEqualTo(CqlKeywordSetGenerator.nameUuid(IKE_FOUNDATION, "after (IkeCQL)"));
    }

    // ── The real dictionary ─────────────────────────────────────────────────

    @Test
    void readsEveryEntryInTheShippedDictionary() {
        List<Entry> entries = CqlKeywordDictionary.parse(DICTIONARY);

        assertThat(entries).hasSize(122);
        assertThat(entries).filteredOn(e -> e.status() == Status.NOT_YET_IN_KOMET).hasSize(115);
        assertThat(entries).filteredOn(e -> e.status() == Status.RELATED_CONCEPT_IN_KOMET)
                .hasSize(5);
        assertThat(entries).filteredOn(e -> e.status() == Status.IMPLEMENTED_IN_KOMET).hasSize(2);
        assertThat(entries).extracting(Entry::anchor).doesNotHaveDuplicates();
        // null for the 7 entries carrying no taxonomy path — all of them withheld from minting.
        assertThat(entries).extracting(Entry::family)
                .containsOnly("Core Operators", "Data & Timing Operators", "Declarations",
                        "Query & Control Flow", "Types & Literals", null);
    }

    /**
     * The metadata line's two forms, and the fact that the seven entries using the bare form are
     * exactly the seven a naive reader would mangle.
     */
    @Test
    void readsBothMetadataLineForms() {
        List<Entry> entries = CqlKeywordDictionary.parse(DICTIONARY);

        assertThat(entries).filteredOn(e -> e.family() != null).hasSize(115);
        assertThat(entries).filteredOn(e -> e.family() == null).hasSize(7)
                .allMatch(e -> !e.generatable())
                .extracting(Entry::keyword)
                .containsExactlyInAnyOrder("and", "or", "included in", "Interval",
                        "or less", "or more", "overlaps");
    }

    /** Headings carry bold markup inconsistently; the anchor is what the keyword is checked against. */
    @Test
    void takesTheKeywordFromHeadingsWhoseMarkupIsNotUniform() {
        List<Entry> entries = CqlKeywordDictionary.parse(DICTIONARY);

        assertThat(entries).extracting(Entry::keyword)
                .contains("included in", "or after", "such that", "after", "Interval");
    }

    @Test
    void generatesTheTaxonomyFromTheShippedDictionary() {
        Generated generated = generateFromDictionary();

        // 1 root + 5 families + 16 categories + 115 generatable keywords.
        assertThat(generated.mintedConcepts()).isEqualTo(137);
        assertThat(generated.deferredEntries()).isEqualTo(7);
        assertThat(generated.javaSource())
                .contains("public static void compose(KnowledgeSet set)")
                .contains("set.concept(\"after (IkeCql)\").at(inception)")
                .contains(".synonym(\"after\")")
                .contains(".isA(set.conceptRef(\"Timing Operator (IkeCql)\"))")
                // the root is authored, not merely referenced, and hangs off an external term
                .contains("set.concept(\"CQL keyword (IkeCql)\").at(inception)")
                .contains(".isA(network.ike.foundation.ike.terms.IkeTerm.MODEL_CONCEPT)")
                .doesNotContain("\"and (IkeCql)\"");   // Implemented in Komet — not re-minted
        assertThat(generated.identityReport())
                .contains("IMPLEMENTED_IN_KOMET  and  -> *And*")
                .contains("RELATED_CONCEPT_IN_KOMET  included in  -> *Clause Included in*");
    }

    /**
     * The emitted fluent chain's closing parentheses are counted by hand in the emitter and a
     * miscount produces a file that will not compile. Checked outside the string literals, since
     * definitions carry prose parentheses of their own — two entries quote half-open interval
     * notation like {@code Interval[3, 5)}.
     */
    @Test
    void emitsStructurallyBalancedJava() {
        String code = generateFromDictionary().javaSource()
                .replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");

        assertThat(count(code, '(')).as("parentheses").isEqualTo(count(code, ')'));
        assertThat(count(code, '{')).as("braces").isEqualTo(count(code, '}'));
    }

    private static long count(String text, char c) {
        return text.chars().filter(ch -> ch == c).count();
    }

    /**
     * The committed generated output is the deliverable, and this is the gate on it: any change
     * to the dictionary, the naming scheme, or the emitter shows up as a diff to review rather
     * than as a silently different set of identities.
     */
    @Test
    void reproducesTheCommittedOutput() throws IOException {
        Generated generated = generateFromDictionary();

        assertThat(generated.javaSource()).isEqualTo(expected("CqlKeywordSet.java.txt"));
        assertThat(generated.identityReport())
                .isEqualTo(expected("cql-keyword-identities.txt"));
    }

    /** Output is a function of content, not of the order the dictionary happens to be in. */
    @Test
    void isIndifferentToDictionaryOrder() {
        List<Entry> entries = new ArrayList<>(CqlKeywordDictionary.parse(DICTIONARY));
        Generated inOrder = CqlKeywordSetGenerator.generate(entries, OPTIONS);
        Collections.shuffle(entries, new Random(20260826L));

        assertThat(CqlKeywordSetGenerator.generate(entries, OPTIONS)).isEqualTo(inOrder);
    }

    /**
     * Definitions are free prose that ends up inside a Java string literal. Today none of the 122
     * carries a quote or a backslash; the day one does, an unescaped emitter would produce a file
     * that either fails to compile or, worse, compiles to different text than the dictionary says.
     */
    @Test
    void escapesDefinitionsThatWouldBreakTheEmittedLiteral() {
        List<String> lines = new ArrayList<>(entry(ENTRY));
        lines.set(lines.indexOf("Relational operator testing whether one date, time,"
                + " or interval occurs later than another."),
                "Tests a \\ backslash and a \"quoted\" phrase.");

        Generated generated = CqlKeywordSetGenerator.generate(
                CqlKeywordDictionary.parse(lines), OPTIONS);

        assertThat(generated.javaSource())
                .contains(".definition(\"Tests a \\\\ backslash and a \\\"quoted\\\" phrase.\")");
    }

    // ── Strictness ──────────────────────────────────────────────────────────

    @Test
    void acceptsAWellFormedEntry() {
        List<Entry> entries = CqlKeywordDictionary.parse(entry(ENTRY));

        assertThat(entries).singleElement().satisfies(e -> {
            assertThat(e.keyword()).isEqualTo("after");
            assertThat(e.anchor()).isEqualTo("after");
            assertThat(e.family()).isEqualTo("Data & Timing Operators");
            assertThat(e.category()).isEqualTo("Timing Operator");
            assertThat(e.status()).isEqualTo(Status.NOT_YET_IN_KOMET);
            assertThat(e.definition()).startsWith("Relational operator");
            assertThat(e.kometConcepts()).isNull();
            assertThat(e.generatable()).isTrue();
        });
    }

    @Test
    void rejectsAnUnknownKometStatus() {
        assertThatFails("`", "`Timing Operator` -- (Data & Timing Operators > Timing Operator) Partly in Komet")
                .hasMessageContaining("unknown Komet status");
    }

    @Test
    void rejectsAKeywordThatDisagreesWithItsAnchor() {
        assertThatFails("=== ", "=== *before*").hasMessageContaining("does not agree with keyword");
    }

    @Test
    void rejectsAGeneratableEntryWithNoTaxonomyPath() {
        assertThatFails("`", "`Timing Operator` -- Not yet in Komet")
                .hasMessageContaining("carries no (Family > Subcategory) path");
    }

    @Test
    void rejectsACategoryThatDisagreesWithItsSubcategory() {
        assertThatFails("`", "`Timing Operator` -- (Data & Timing Operators > Sorting) Not yet in Komet")
                .hasMessageContaining("disagree");
    }

    @Test
    void rejectsAMissingKometConceptOnAnEntryClaimingOne() {
        assertThatFails("`", "`Timing Operator` -- *Implemented in Komet*")
                .hasMessageContaining("names no Komet concept");
    }

    @Test
    void rejectsAMetadataLineItCannotRead() {
        assertThatFails("`", "Timing Operator, not yet in Komet")
                .hasMessageContaining("expected a `Category` -- ... metadata line");
    }

    @Test
    void rejectsAnEntryMissingItsExample() {
        assertThatFails("[source,cql]", "[source,sql]")
                .hasMessageContaining("expected \"[source,cql]\"");
    }

    @Test
    void rejectsADuplicateAnchor() {
        assertThatThrownBy(() -> CqlKeywordDictionary.parse(entry(ENTRY + "\n" + ENTRY)))
                .isInstanceOf(MalformedEntryException.class)
                .hasMessageContaining("duplicate anchor term-after");
    }

    /**
     * An anchor line the reader cannot match is the one degradation this generator must not have:
     * the entry would vanish, the parse would come back short, and the build would stay green with
     * one keyword missing from the ledger. Every heading belongs to an entry, so the counts have
     * to agree.
     */
    @Test
    void rejectsAnAnchorTypoRatherThanSilentlyDroppingTheEntry() {
        assertThatDroppedEntryFails("[[term-after_on]]")
                .hasMessageContaining("read 1 entries but the file carries 2");
    }

    @Test
    void rejectsAnAnchorWithTrailingWhitespace() {
        assertThatDroppedEntryFails("[[term-after]] ")
                .hasMessageContaining("read 1 entries but the file carries 2");
    }

    /**
     * Category names are concept names, so the same name under two families would collapse two
     * intended concepts into one UUID hung under whichever family the generator saw last.
     */
    @Test
    void rejectsACategoryNameSharedByTwoFamilies() {
        List<String> lines = twoEntries();
        lines.set(lines.indexOf(METADATA_LINE),
                "`Timing Operator` -- (Core Operators > Timing Operator) Not yet in Komet");

        assertThatThrownBy(() -> CqlKeywordSetGenerator.generate(
                CqlKeywordDictionary.parse(lines), OPTIONS))
                .isInstanceOf(MalformedEntryException.class)
                .hasMessageContaining("category \"Timing Operator\" appears under two families")
                .hasMessageContaining("Core Operators")
                .hasMessageContaining("Data & Timing Operators");
    }

    /**
     * An anchor renders every run of non-alphanumerics as one {@code -}, so anchor agreement on
     * its own admits rewordings that mint a different concept: {@code or after} and
     * {@code or-after} both derive {@code term-or-after}. The keyword shape is what closes that.
     */
    @Test
    void rejectsAKeywordWhoseSpacingTheAnchorCannotPin() {
        assertThatAnchorCannotPin("=== *or*  after")
                .hasMessageContaining("keyword \"or  after\" cannot be pinned");
    }

    @Test
    void rejectsAKeywordWhosePunctuationTheAnchorCannotPin() {
        assertThatAnchorCannotPin("=== *or*-after")
                .hasMessageContaining("keyword \"or-after\" cannot be pinned");
    }

    /**
     * Names are only unique per tier; across tiers nothing stopped a family and a category sharing
     * one name, which under {@code UUIDv5(namespace, fqn)} is one concept authored twice and
     * stated is-a itself.
     */
    @Test
    void rejectsAFamilyAndACategorySharingOneName() {
        List<String> lines = twoEntries();
        lines.set(lines.indexOf(METADATA_LINE),
                "`Declarations` -- (Declarations > Declarations) Not yet in Komet");

        assertThatThrownBy(() -> CqlKeywordSetGenerator.generate(
                CqlKeywordDictionary.parse(lines), OPTIONS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two concepts share the fully qualified name")
                .hasMessageContaining("Declarations (IkeCql)");
    }

    /**
     * The trailing {@code Komet concept:} line is scanned forward from the example block, so
     * without the separator an entry would run into the next one and report its counterpart as
     * its own — a wrong row in the identity report, reached with nothing erroring.
     */
    @Test
    void rejectsAnEntryRunningIntoTheNextWithNoSeparator() {
        List<String> lines = twoWithheldEntries();
        lines.remove(lines.indexOf("* * *"));

        assertThatRunsIntoTheNextEntry(lines);
    }

    /**
     * The same misattribution, one line earlier: with the example block left unclosed the scan
     * looking for {@code ----} is what crosses the boundary, and it lands inside the next entry's
     * example — past the anchor, so a check further down could no longer see it.
     */
    @Test
    void rejectsAnEntryWhoseExampleBlockIsNeverClosed() {
        List<String> lines = twoWithheldEntries();
        lines.remove(lines.indexOf(
                "\"FollowUpDateTime\" 3 days or less after \"DischargeDateTime\"") + 1);

        assertThatRunsIntoTheNextEntry(lines);
    }

    /**
     * The withheld entries' counterparts are a deliverable of the identity report, so a second
     * counterpart line has to fail rather than quietly displace the first.
     */
    @Test
    void rejectsAnEntryNamingTwoKometConcepts() {
        List<String> lines = twoWithheldEntries();
        lines.add(lines.indexOf("Related Komet concept(s): *Clause Less or equal*"),
                "Komet concept: *And*");

        assertThatThrownBy(() -> CqlKeywordDictionary.parse(lines))
                .isInstanceOf(MalformedEntryException.class)
                .hasMessageContaining(
                        "names a second Komet concept \"*Clause Less or equal*\" after \"*And*\"");
    }

    private static void assertThatRunsIntoTheNextEntry(List<String> lines) {
        assertThatThrownBy(() -> CqlKeywordDictionary.parse(lines))
                .isInstanceOf(MalformedEntryException.class)
                .hasMessageContaining("reached the next entry's anchor \"[[term-or-more]]\"");
    }

    /** With the separator in place the same two entries keep their own counterparts. */
    @Test
    void readsAdjacentWithheldEntriesWithoutCrossingTheirBoundary() {
        List<Entry> entries = CqlKeywordDictionary.parse(twoWithheldEntries());

        assertThat(entries).extracting(Entry::keyword, Entry::kometConcepts)
                .containsExactly(tuple("or less", "*Clause Less or equal*"),
                        tuple("or more", "*Clause Greater or equal*"));
    }

    @Test
    void rejectsAFileWithNoEntries() {
        assertThatThrownBy(() -> CqlKeywordDictionary.parse(List.of("= Keyword Dictionary", "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No [[term-...]] entries found");
    }

    // ── Fixtures ────────────────────────────────────────────────────────────

    private static final String ENTRY = """
            [[term-after]]
            [discrete]
            === *after*

            `Timing Operator` -- (Data & Timing Operators > Timing Operator) Not yet in Komet

            Relational operator testing whether one date, time, or interval occurs later than another.

            [source,cql]
            ----
            "DischargeDateTime" after "AdmissionDateTime"
            ----



            * * *""";

    private static final String METADATA_LINE =
            "`Timing Operator` -- (Data & Timing Operators > Timing Operator) Not yet in Komet";

    private static List<String> entry(String text) {
        return List.of(text.split("\n", -1));
    }

    /**
     * The shipped dictionary's two adjacent withheld entries, the pair whose counterparts a scan
     * crossing the boundary would swap.
     */
    private static final String WITHHELD_PAIR = """
            [[term-or-less]]
            [discrete]
            === *or* less

            `Timing Operator` -- _Related concept in Komet_

            Qualifies a quantity offset to mean that amount or a smaller amount.

            [source,cql]
            ----
            "FollowUpDateTime" 3 days or less after "DischargeDateTime"
            ----

            Related Komet concept(s): *Clause Less or equal*

            * * *

            [[term-or-more]]
            [discrete]
            === *or* more

            `Timing Operator` -- _Related concept in Komet_

            Qualifies a quantity offset to mean that amount or a larger amount.

            [source,cql]
            ----
            "ReadmissionDateTime" 30 days or more after "DischargeDateTime"
            ----

            Related Komet concept(s): *Clause Greater or equal*""";

    private static List<String> twoWithheldEntries() {
        return new ArrayList<>(entry(WITHHELD_PAIR));
    }

    /** Two well-formed entries — "after" and the same entry reworded to "before". */
    private static List<String> twoEntries() {
        return new ArrayList<>(entry(ENTRY + "\n" + ENTRY.replace("after", "before")));
    }

    /**
     * Rewrites the fixture to the given heading under anchor {@code term-or-after} — an anchor the
     * old derivation agreed with — then asserts the parse refuses it anyway.
     */
    private static org.assertj.core.api.AbstractThrowableAssert<?, ?> assertThatAnchorCannotPin(
            String heading) {
        List<String> lines = new ArrayList<>(entry(ENTRY));
        lines.set(lines.indexOf("[[term-after]]"), "[[term-or-after]]");
        lines.set(lines.indexOf("=== *after*"), heading);

        return assertThatThrownBy(() -> CqlKeywordDictionary.parse(lines))
                .isInstanceOf(MalformedEntryException.class);
    }

    /** Corrupts the first entry's anchor line, then asserts the short parse is refused. */
    private static org.assertj.core.api.AbstractThrowableAssert<?, ?> assertThatDroppedEntryFails(
            String corruptedAnchor) {
        List<String> lines = twoEntries();
        lines.set(lines.indexOf("[[term-after]]"), corruptedAnchor);

        return assertThatThrownBy(() -> CqlKeywordDictionary.parse(lines))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Replaces the one fixture line starting with {@code prefix}, then asserts the parse fails. */
    private static org.assertj.core.api.AbstractThrowableAssert<?, ?> assertThatFails(
            String prefix, String replacement) {
        List<String> lines = new ArrayList<>(entry(ENTRY));
        int index = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(prefix)) {
                index = i;
                break;
            }
        }
        assertThat(index).as("fixture line starting with \"%s\"", prefix).isNotNegative();
        lines.set(index, replacement);
        return assertThatThrownBy(() -> CqlKeywordDictionary.parse(lines))
                .isInstanceOf(MalformedEntryException.class);
    }

    private static Generated generateFromDictionary() {
        return CqlKeywordSetGenerator.generate(CqlKeywordDictionary.parse(DICTIONARY), OPTIONS);
    }

    private static String expected(String name) throws IOException {
        return Files.readString(Path.of("src", "test", "resources", "expected", name),
                StandardCharsets.UTF_8);
    }
}
