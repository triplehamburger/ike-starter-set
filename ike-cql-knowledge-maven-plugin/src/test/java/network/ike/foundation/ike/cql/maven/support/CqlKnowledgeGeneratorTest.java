package network.ike.foundation.ike.cql.maven.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import network.ike.foundation.ike.cql.maven.support.CqlKnowledgeGenerator.Result;
import network.ike.foundation.ike.cql.maven.support.CqlKnowledgeGenerator.Target;
import network.ike.foundation.ike.cql.maven.support.KeywordDictionary.Entry;

/** Identity derivation, determinism, and what the generated source says. */
class CqlKnowledgeGeneratorTest {

    private static final Path CHAPTER = Path.of("..", "ike-doc", "src", "docs", "asciidoc", "cql",
            "keyword-dictionary.adoc");

    private static final Target TARGET = new Target(
            CqlKnowledgeGenerator.IKE_FOUNDATION_NAMESPACE, "CQL", "Clinical Quality Language",
            "IkeTerm.MODEL_CONCEPT", "network.ike.foundation.ike.terms.cql", "CqlKeywordSet",
            "Ike.INCEPTION");

    /**
     * The proof that this generator derives identities the way the {@code KnowledgeSet} DSL does.
     *
     * <p>{@code ike-terms}' own fidelity test records that {@code STAMP (IkeFoundation)} in the
     * IkeFoundation set is {@code 3f93c9fb-48c9-53e2-a3e7-a7ae39311b97} — a value produced by
     * tinkar's {@code UuidT5Generator}, not by anything here. Reproducing it from the name and the
     * set's namespace alone is what says the two derivations are the same function, which is in
     * turn what makes every UUID this generator writes out equal to the one
     * {@code set.concept(name)} would have derived on its own.
     */
    @Test
    void derivesIdentitiesTheWayTheKnowledgeSetDslDoes() {
        assertThat(CqlKnowledgeGenerator.identityFor(
                CqlKnowledgeGenerator.IKE_FOUNDATION_NAMESPACE, "STAMP (IkeFoundation)"))
                .isEqualTo(UUID.fromString("3f93c9fb-48c9-53e2-a3e7-a7ae39311b97"));
    }

    @Test
    void derivesTheIdentityFromTheNameAndNothingElse() {
        UUID once = CqlKnowledgeGenerator.identityFor(TARGET.namespace(), "after (CQL)");
        UUID again = CqlKnowledgeGenerator.identityFor(TARGET.namespace(), "after (CQL)");
        assertThat(once).isEqualTo(again).hasToString("6953bac7-cc79-5b7d-a0da-77bfed7e0267");

        // A different wording, or a different namespace, is a different concept — never a rename.
        assertThat(CqlKnowledgeGenerator.identityFor(TARGET.namespace(), "After (CQL)"))
                .isNotEqualTo(once);
        assertThat(CqlKnowledgeGenerator.identityFor(UUID.nameUUIDFromBytes(new byte[] {1}),
                "after (CQL)")).isNotEqualTo(once);
    }

    @Test
    void regeneratesByteIdenticalSourceFromTheSameDictionary() throws Exception {
        if (!Files.exists(CHAPTER)) {
            return;
        }
        assertThat(generateFromChapter().source()).isEqualTo(generateFromChapter().source());
    }

    @Test
    void isIndifferentToTheOrderTheDictionaryListsKeywordsIn() throws Exception {
        if (!Files.exists(CHAPTER)) {
            return;
        }
        List<Entry> entries = parseChapter();
        List<Entry> shuffled = new java.util.ArrayList<>(entries);
        java.util.Collections.reverse(shuffled);
        assertThat(CqlKnowledgeGenerator.generate(shuffled, TARGET).source())
                .isEqualTo(CqlKnowledgeGenerator.generate(entries, TARGET).source());
    }

    @Test
    void mintsOnlyTheKeywordsWithNoKometConceptYet() throws Exception {
        if (!Files.exists(CHAPTER)) {
            return;
        }
        Result result = generateFromChapter();
        // 115 generatable keywords, plus the root, 5 families and 16 categories they hang from.
        assertThat(result.concepts()).hasSize(115 + 1 + 5 + 16);
        assertThat(result.deferrals()).hasSize(7)
                .anySatisfy(line -> assertThat(line).isEqualTo("and — Implemented in Komet"))
                .anySatisfy(line -> assertThat(line).isEqualTo("or less — Related concept in Komet"));
        assertThat(result.concepts())
                .doesNotContain("and (CQL)", "or less (CQL)", "Interval (CQL)")
                .doesNotHaveDuplicates();
    }

    @Test
    void authorsEveryParentBeforeTheConceptsThatCiteIt() throws Exception {
        if (!Files.exists(CHAPTER)) {
            return;
        }
        Result result = generateFromChapter();
        // The taxonomy method runs first and carries every parent, so nothing needs sorting: the
        // root precedes its families, which precede their categories, which precede the keywords.
        String source = result.source();
        assertThat(source.indexOf("composeTaxonomy(set);"))
                .isLessThan(source.indexOf("composeCoreOperators(set);"));
        assertThat(source.indexOf("set.concept(\"Clinical Quality Language (CQL)\""))
                .isLessThan(source.indexOf("set.concept(\"Core Operators (CQL)\""))
                .isGreaterThan(0);
    }

    @Test
    void writesEachConceptAsOneFluentChainCarryingItsDerivedIdentity() throws Exception {
        if (!Files.exists(CHAPTER)) {
            return;
        }
        assertThat(generateFromChapter().source()).contains("""
                        set.concept("after (CQL)", PublicIds.of(UUID.fromString("6953bac7-cc79-5b7d-a0da-77bfed7e0267"))).at(inception)
                                .synonym("after")
                                .definition("Relational operator testing whether one date, time, or interval occurs later than another (e.g. \\u2018X after Y\\u2019).\\n\\nExample:\\n\\"DischargeDateTime\\" after \\"AdmissionDateTime\\"")
                                .isA(set.conceptRef("Timing Operator (CQL)"));
                """.replace("\\u2018", "‘").replace("\\u2019", "’"));
    }

    @Test
    void hangsTheWholeTaxonomyOffTheConfiguredRootParent() throws Exception {
        if (!Files.exists(CHAPTER)) {
            return;
        }
        assertThat(generateFromChapter().source())
                .contains(".isA(IkeTerm.MODEL_CONCEPT);")
                .contains(".isA(set.conceptRef(\"Clinical Quality Language (CQL)\"));");
    }

    /**
     * The generated source is written against a {@code KnowledgeSet} API this module cannot depend
     * on — the authoring tier lives in a tinkar snapshot, and taking that dependency is exactly
     * what "standalone" rules out. What can still be proven here is that the emitted text is valid
     * Java: javac is asked to compile it, and every complaint must be a failure to resolve a type
     * that was never on the classpath. Any other diagnostic — an unbalanced brace, a string literal
     * broken by an unescaped quote or newline out of the dictionary's own prose — is a generator
     * bug and fails this test.
     */
    @Test
    void emitsJavaThatParses(@TempDir Path work) throws Exception {
        if (!Files.exists(CHAPTER)) {
            return;
        }
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        String source = generateFromChapter().source();
        JavaFileObject unit = new SimpleJavaFileObject(
                URI.create("string:///CqlKeywordSet.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        try (StandardJavaFileManager files = javac.getStandardFileManager(null, null, null)) {
            javac.getTask(null, files, diagnostics,
                    List.of("-proc:none", "-d", work.toString()), null, List.of(unit)).call();
        }
        assertThat(diagnostics.getDiagnostics())
                .allSatisfy(diagnostic -> assertThat(diagnostic.getCode())
                        .describedAs("%s", diagnostic.getMessage(null))
                        .isIn(UNRESOLVED))
                .isNotEmpty();
    }

    /** javac's codes for "this type was never on the classpath", the only errors expected above. */
    private static final Set<String> UNRESOLVED = Set.of(
            "compiler.err.doesnt.exist",
            "compiler.err.cant.resolve",
            "compiler.err.cant.resolve.location",
            "compiler.err.cant.resolve.location.args",
            "compiler.err.package.not.visible");

    private static Result generateFromChapter() throws Exception {
        return CqlKnowledgeGenerator.generate(parseChapter(), TARGET);
    }

    private static List<Entry> parseChapter() throws Exception {
        return KeywordDictionary.parse(Files.readAllLines(CHAPTER, StandardCharsets.UTF_8));
    }
}
