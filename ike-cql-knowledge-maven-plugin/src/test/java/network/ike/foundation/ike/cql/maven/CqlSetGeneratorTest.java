package network.ike.foundation.ike.cql.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The one check that fails if either the parse or the emitted source breaks.
 *
 * <p>{@link #emitsTheDictionary()} runs the generator over the real chapter — the only input this
 * build ever has — and {@link #readsTheAwkwardEntries()} pins the four shapes a naive parser gets
 * wrong, using a fixture small enough to read in one screen. Names are load-bearing here: a
 * keyword's name derives its identity, so a name read wrongly is an identity nobody can correct.
 */
class CqlSetGeneratorTest {

    /**
     * Every shape in the chapter that is not the plain {@code === *keyword*} case: a title whose
     * bold markers wrap only part of the keyword, a title with no bold at all, a case-distinct
     * keyword pair, and the two short metadata forms that state a Komet status instead of a family
     * path. Curly quotes, an em dash, and a quoted example ride along to pin the escaping.
     */
    private static final String FIXTURE = """
            = Keyword Dictionary

            [[term-code]]
            [discrete]
            === *code*

            `Type Name` -- (Types & Literals > Type Name) Not yet in Komet

            Keyword introducing a terminology code — written ‘code’ in lower case.

            [source,cql]
            ----
            define "C": Code '1234' from "LOINC"
            ----

            * * *

            [[term-Code]]
            [discrete]
            === *Code*

            `Type Name` -- (Types & Literals > Type Name) Not yet in Komet

            Built-in type for a terminology code, distinct from the lower-case keyword.

            [source,cql]
            ----
            define "T": Code
            ----

            * * *

            [[term-includes]]
            [discrete]
            === *includes*

            `List/Interval Operator` -- (Data & Timing Operators > List/Interval Operator) Not yet in Komet

            Tests whether one interval wholly contains another.

            [source,cql]
            ----
            "MeasurementPeriod" includes "EncounterPeriod"
            ----

            * * *

            [[term-included-in]]
            [discrete]
            === *include*d in

            `List/Interval Operator` -- _Related concept in Komet_

            The inverse of ‘includes’.

            [source,cql]
            ----
            "EncounterPeriod" included in "MeasurementPeriod"
            ----

            Related Komet concept(s): *Clause Included in*

            * * *

            [[term-not]]
            [discrete]
            === *not*

            `Logical Operator` -- (Core Operators > Logical Operator) Not yet in Komet

            Boolean negation.

            [source,cql]
            ----
            not "HasCondition"
            ----

            * * *

            [[term-or]]
            [discrete]
            === *or*

            `Logical Operator` -- *Implemented in Komet*

            Boolean inclusive disjunction.

            [source,cql]
            ----
            "HasCondition" or "HasMedication"
            ----

            Komet concept: *Or*

            * * *

            [[term-such-that]]
            [discrete]
            === such that

            `Query Clause` -- (Query & Control Flow > Query Clause) Not yet in Komet

            Introduces the condition of a relationship clause.

            [source,cql]
            ----
            with "Encounter" E such that E.id = C.encounter
            ----

            * * *
            """;

    @Test
    void readsTheAwkwardEntries() {
        String source = CqlSetGenerator.generate(FIXTURE.lines().toList());
        // Long definitions are emitted as wrapped literal concatenations; re-join them so the
        // assertions below read the text rather than where the generator happened to break a line.
        String flat = source.replaceAll("(?<!\\\\)\"\\s*\\+ \"", "");

        // The keyword is the whole title de-marked, never the bolded run inside it.
        assertThat(source).contains("set.concept(\"included in (CQL)\")")
                .contains("set.concept(\"such that (CQL)\")")
                // Case-distinct keywords are two concepts, not one.
                .contains("set.concept(\"code (CQL)\")")
                .contains("set.concept(\"Code (CQL)\")")
                // The two short metadata forms resolve their family from the subfamily, which some
                // other entry always states in a parenthesised path.
                .contains("set.concept(\"or (CQL)\").at(inception)")
                .contains("set.concept(\"Logical Operator (CQL)\")")
                .contains("set.concept(\"List/Interval Operator (CQL)\")");

        // ... and the Komet status the short forms carry is not dropped.
        assertThat(flat).contains("Komet status: Implemented in Komet. Komet concept: Or")
                .contains("Komet status: Related concept in Komet."
                        + " Related Komet concept(s): Clause Included in")
                // Curly quotes and em dashes survive; quotes in the example are escaped.
                .contains("— written ‘code’ in lower case")
                .contains("\\\"MeasurementPeriod\\\" includes \\\"EncounterPeriod\\\"");

        // 1 root + 4 families + 4 subfamilies + 7 keywords, every parent ahead of its children.
        assertThat(source.split("set\\.concept\\(", -1)).hasSize(1 + 16);
        assertThat(source.indexOf("set.concept(\"Core Operators (CQL)\")"))
                .isGreaterThan(source.indexOf("set.concept(\"Clinical Quality Language (CQL)\")"));
        assertThat(source.indexOf("set.concept(\"Logical Operator (CQL)\")"))
                .isGreaterThan(source.indexOf("set.concept(\"Core Operators (CQL)\")"));
        assertThat(source.indexOf("set.concept(\"or (CQL)\")"))
                .isGreaterThan(source.indexOf("set.concept(\"Logical Operator (CQL)\")"));

        // A title read wrongly no longer matches its own anchor, and that has to stop the build:
        // the name is the identity, so a near-miss mints a concept nobody can correct later.
        assertThatThrownBy(() -> CqlSetGenerator.generate(
                FIXTURE.replace("=== such that", "=== such  that!").lines().toList()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("term-such-that")
                .hasMessageContaining("title and anchor must agree");
    }

    @Test
    void emitsTheDictionary() throws IOException {
        Path chapter = Path.of("..", "ike-doc", "src", "docs", "asciidoc", "cql",
                "keyword-dictionary.adoc");
        List<String> lines = Files.readAllLines(chapter, StandardCharsets.UTF_8);
        String source = CqlSetGenerator.generate(lines);

        // 122 keywords under 16 subfamilies under 5 families under one root.
        assertThat(lines.stream().filter(line -> line.startsWith("[[term-")).count()).isEqualTo(122);
        assertThat(source.split("set\\.concept\\(", -1)).hasSize(1 + 144);
        assertThat(source).startsWith("/*\n * Copyright")
                .contains("package network.ike.foundation.ike.terms;")
                .contains("final class CqlSet {")
                .contains("ActiveStamp inception = Ike.INCEPTION;")
                .contains(".isA(IkeTerm.MODEL_CONCEPT);")
                .endsWith("}\n");
    }
}
