package network.ike.foundation.ike.cql.maven.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import network.ike.foundation.ike.cql.maven.support.KeywordDictionary.Entry;
import network.ike.foundation.ike.cql.maven.support.KeywordDictionary.KometStatus;

/** The dictionary parser, against the real chapter and against shapes it must refuse. */
class KeywordDictionaryTest {

    /**
     * The chapter this generator exists to read. Tests that need it are skipped rather than failed
     * when the doc module is not beside this one, so the plugin stays buildable on its own.
     */
    private static final Path CHAPTER = Path.of("..", "ike-doc", "src", "docs", "asciidoc", "cql",
            "keyword-dictionary.adoc");

    private static List<Entry> realChapter() throws Exception {
        return KeywordDictionary.parse(Files.readAllLines(CHAPTER, StandardCharsets.UTF_8));
    }

    @Test
    void readsEveryEntryInTheRealChapter() throws Exception {
        assumeTrue(Files.exists(CHAPTER), "the ike-doc chapter is not beside this module");
        assertThat(realChapter()).hasSize(122);
    }

    @Test
    void splitsTheRealChapterIntoTheThreeKometStatuses() throws Exception {
        assumeTrue(Files.exists(CHAPTER), "the ike-doc chapter is not beside this module");
        List<Entry> entries = realChapter();
        assertThat(entries).filteredOn(e -> e.status() == KometStatus.NOT_YET).hasSize(115);
        assertThat(entries).filteredOn(e -> e.status() == KometStatus.RELATED).hasSize(5);
        assertThat(entries).filteredOn(e -> e.status() == KometStatus.IMPLEMENTED).hasSize(2);
    }

    @Test
    void readsMultiWordKeywordsWhoseTitlesBoldOnlyPartOfTheName() throws Exception {
        assumeTrue(Files.exists(CHAPTER), "the ike-doc chapter is not beside this module");
        // '=== *include*d in', '=== *or* after', '=== such that'. Reading the bolded run instead
        // of the whole title would name five separate keywords 'or'.
        assertThat(realChapter()).extracting(Entry::name)
                .contains("included in", "or after", "or before", "or less", "or more", "or on",
                        "such that")
                .doesNotHaveDuplicates();
    }

    @Test
    void readsBothFormsOfTheMetadataLine() {
        Entry withFamily = one("""
                [[term-after]]
                [discrete]
                === *after*

                `Timing Operator` -- (Data & Timing Operators > Timing Operator) Not yet in Komet

                Relational operator testing whether one date occurs later than another.

                [source,cql]
                ----
                "DischargeDateTime" after "AdmissionDateTime"
                ----
                """);
        assertThat(withFamily.category()).isEqualTo("Timing Operator");
        assertThat(withFamily.family()).contains("Data & Timing Operators");
        assertThat(withFamily.status()).isEqualTo(KometStatus.NOT_YET);

        Entry withoutFamily = one("""
                [[term-and]]
                [discrete]
                === *and*

                `Logical Operator` -- *Implemented in Komet*

                Boolean conjunction.

                [source,cql]
                ----
                AgeInYears() >= 18 and AgeInYears() < 65
                ----
                """);
        assertThat(withoutFamily.family()).isEmpty();
        assertThat(withoutFamily.status()).isEqualTo(KometStatus.IMPLEMENTED);
    }

    @Test
    void readsTheStatusFromItsWordingRatherThanItsMarkup() {
        assertThat(one(entry("`Type Name` -- _Related concept in Komet_")).status())
                .isEqualTo(KometStatus.RELATED);
    }

    @Test
    void refusesAnUnknownStatus() {
        assertThatThrownBy(() -> one(entry("`Timing Operator` -- Maybe in Komet")))
                .isInstanceOf(KeywordDictionary.MalformedEntryException.class)
                .hasMessageContaining("unknown Komet status 'Maybe in Komet'");
    }

    @Test
    void refusesAMetadataLineInNeitherKnownForm() {
        assertThatThrownBy(() -> one(entry("Timing Operator: Not yet in Komet")))
                .isInstanceOf(KeywordDictionary.MalformedEntryException.class)
                .hasMessageContaining("backticked category");
    }

    @Test
    void refusesAGeneratableKeywordWithNoFamilyToHangFrom() {
        assertThatThrownBy(() -> one(entry("`Timing Operator` -- Not yet in Komet")))
                .isInstanceOf(KeywordDictionary.MalformedEntryException.class)
                .hasMessageContaining("must declare its 'Family > Subcategory' path");
    }

    @Test
    void refusesAFamilyPathThatDisagreesWithTheCategory() {
        assertThatThrownBy(() -> one(entry(
                "`Timing Operator` -- (Data & Timing Operators > Query Clause) Not yet in Komet")))
                .isInstanceOf(KeywordDictionary.MalformedEntryException.class)
                .hasMessageContaining("must agree");
    }

    @Test
    void refusesATitleThatDisagreesWithItsAnchor() {
        assertThatThrownBy(() -> one("""
                [[term-or]]
                [discrete]
                === *or* after

                `Timing Operator` -- (Data & Timing Operators > Timing Operator) Not yet in Komet

                Relational operator.

                [source,cql]
                ----
                X or after Y
                ----
                """))
                .isInstanceOf(KeywordDictionary.MalformedEntryException.class)
                .hasMessageContaining("title and anchor must agree");
    }

    @Test
    void refusesADefinitionOfMoreThanOneParagraph() {
        assertThatThrownBy(() -> one("""
                [[term-after]]
                [discrete]
                === *after*

                `Timing Operator` -- (Data & Timing Operators > Timing Operator) Not yet in Komet

                First paragraph.

                Second paragraph.

                [source,cql]
                ----
                X after Y
                ----
                """))
                .isInstanceOf(KeywordDictionary.MalformedEntryException.class)
                .hasMessageContaining("more than one paragraph");
    }

    @Test
    void refusesAnEntryWithNoDefinition() {
        assertThatThrownBy(() -> one("""
                [[term-after]]
                [discrete]
                === *after*

                `Timing Operator` -- (Data & Timing Operators > Timing Operator) Not yet in Komet

                [source,cql]
                ----
                X after Y
                ----
                """))
                .isInstanceOf(KeywordDictionary.MalformedEntryException.class)
                .hasMessageContaining("carries no definition paragraph");
    }

    @Test
    void refusesAnEntryWithNoExample() {
        assertThatThrownBy(() -> one("""
                [[term-after]]
                [discrete]
                === *after*

                `Timing Operator` -- (Data & Timing Operators > Timing Operator) Not yet in Komet

                Relational operator.
                """))
                .isInstanceOf(KeywordDictionary.MalformedEntryException.class)
                .hasMessageContaining("example block");
    }

    @Test
    void refusesTwoEntriesClaimingTheSameKeyword() {
        String twice = entry("`Timing Operator` -- (Data & Timing Operators > Timing Operator)"
                + " Not yet in Komet");
        assertThatThrownBy(() -> KeywordDictionary.parse(lines(twice + "\n" + twice)))
                .isInstanceOf(KeywordDictionary.MalformedEntryException.class)
                .hasMessageContaining("already defined by an earlier entry");
    }

    @Test
    void keepsAMultiLineExampleVerbatim() {
        Entry entry = one("""
                [[term-define]]
                [discrete]
                === *define*

                `Declaration` -- (Declarations > Declaration) Not yet in Komet

                Introduces a named expression.

                [source,cql]
                ----
                define "Adult":
                  AgeInYears() >= 18
                ----
                """);
        assertThat(entry.example()).isEqualTo("define \"Adult\":\n  AgeInYears() >= 18");
        assertThat(entry.definition()).isEqualTo("Introduces a named expression.");
        assertThat(entry.anchor()).isEqualTo("term-define");
    }

    private static String entry(String metadata) {
        return """
                [[term-after]]
                [discrete]
                === *after*

                %s

                Relational operator.

                [source,cql]
                ----
                X after Y
                ----
                """.formatted(metadata);
    }

    private static Entry one(String text) {
        List<Entry> entries = KeywordDictionary.parse(lines(text));
        assertThat(entries).hasSize(1);
        return entries.getFirst();
    }

    private static List<String> lines(String text) {
        return new ArrayList<>(List.of(text.split("\n", -1)));
    }

    @Test
    void everyRealEntryCarriesADefinitionAndAnExample() throws Exception {
        assumeTrue(Files.exists(CHAPTER), "the ike-doc chapter is not beside this module");
        assertThat(realChapter()).allSatisfy(entry -> {
            assertThat(entry.definition()).isNotBlank();
            assertThat(entry.example()).isNotBlank();
            assertThat(entry.family()).isNotEqualTo(Optional.of(""));
        });
    }
}
