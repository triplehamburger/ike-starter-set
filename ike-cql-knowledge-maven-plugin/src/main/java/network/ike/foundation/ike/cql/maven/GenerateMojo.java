package network.ike.foundation.ike.cql.maven;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.apache.maven.api.Lifecycle.Phase;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;

import network.ike.foundation.ike.cql.maven.support.CqlKnowledgeGenerator;
import network.ike.foundation.ike.cql.maven.support.KeywordDictionary;

/**
 * Generates the CQL keyword vocabulary as ledger source from the keyword dictionary chapter.
 *
 * <p>The goal reads {@code cql/keyword-dictionary.adoc}, mints a concept for every keyword the
 * dictionary marks {@code Not yet in Komet}, hangs each under the family and category the
 * dictionary itself assigns it, and writes one {@code compose(KnowledgeSet)} class. Keywords
 * already implemented in Komet, or only related to something in Komet, are reported and left
 * alone — see {@code CqlKnowledgeGenerator} for why.
 *
 * <p>The goal fails the build on any entry it does not recognise. A keyword that parsed "nearly"
 * would become a concept with the wrong name, and because the name derives the identity, a wrong
 * name is a wrong identity that no later edit can quietly correct.
 *
 * <p>Nothing is authored into a ledger by running this: the output is source, written under the
 * build directory, for a human to review and place.
 *
 * <p>Bound to {@code initialize} for the same reason {@code ike-hierarchy:index} is: the Maven 4
 * {@code Phase.SOURCES} constant is not one maven-plugin-tools 4.0.0-beta-2 can map to a phase, and
 * this goal only has to run before anything reads its output.
 */
@Mojo(name = "generate", defaultPhase = Phase.INITIALIZE)
public class GenerateMojo implements org.apache.maven.api.plugin.Mojo {

    @Inject
    private Log log;

    /** The keyword dictionary chapter to read. */
    @Parameter(defaultValue = "${project.basedir}/src/docs/asciidoc/cql/keyword-dictionary.adoc",
            property = "ike-cql-knowledge.dictionary")
    private String dictionary;

    /** Where the generated source tree is written. */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/ike-cql-knowledge",
            property = "ike-cql-knowledge.outputDirectory")
    private String outputDirectory;

    /**
     * The knowledge set's permanent type-5 namespace. Every generated identity derives from it, so
     * pointing this at a different set regenerates every UUID. Defaults to the IkeFoundation set.
     */
    @Parameter(defaultValue = "d890e06f-ec35-429a-b541-d0ead19695e2",
            property = "ike-cql-knowledge.namespace")
    private String namespace;

    /** The parenthesised tag every generated fully qualified name ends with. */
    @Parameter(defaultValue = "CQL", property = "ike-cql-knowledge.semanticTag")
    private String semanticTag;

    /** The display name of the concept the whole generated taxonomy hangs from. */
    @Parameter(defaultValue = "Clinical Quality Language",
            property = "ike-cql-knowledge.rootName")
    private String rootName;

    /**
     * A Java expression naming the concept the taxonomy root is a kind of. The default is what
     * every hand-authored IkeFoundation concept uses; where the CQL vocabulary really belongs in
     * the wider taxonomy is an editorial call, not this goal's.
     */
    @Parameter(defaultValue = "IkeTerm.MODEL_CONCEPT", property = "ike-cql-knowledge.rootParent")
    private String rootParent;

    /** The package the generated class is written into. */
    @Parameter(defaultValue = "network.ike.foundation.ike.terms.cql",
            property = "ike-cql-knowledge.packageName")
    private String packageName;

    /** The generated class's simple name. */
    @Parameter(defaultValue = "CqlKeywordSet", property = "ike-cql-knowledge.className")
    private String className;

    /** A Java expression yielding the stamp every generated version carries. */
    @Parameter(defaultValue = "Ike.INCEPTION", property = "ike-cql-knowledge.stamp")
    private String stamp;

    /** Report what would be written without writing it. */
    @Parameter(defaultValue = "false", property = "ike-cql-knowledge.dryRun")
    private boolean dryRun;

    /** Skip this goal entirely. */
    @Parameter(defaultValue = "false", property = "ike-cql-knowledge.skip")
    private boolean skip;

    @Override
    public void execute() throws MojoException {
        if (skip) {
            log.info("ike-cql-knowledge:generate skipped.");
            return;
        }
        Path source = Path.of(dictionary);
        Path target = Path.of(outputDirectory)
                .resolve(packageName.replace('.', '/'))
                .resolve(className + ".java");

        List<KeywordDictionary.Entry> entries;
        CqlKnowledgeGenerator.Result result;
        try {
            entries = KeywordDictionary.parse(Files.readAllLines(source, StandardCharsets.UTF_8));
            result = CqlKnowledgeGenerator.generate(entries, new CqlKnowledgeGenerator.Target(
                    UUID.fromString(namespace), semanticTag, rootName, rootParent, packageName,
                    className, stamp));
        } catch (IOException e) {
            throw new MojoException("Could not read the keyword dictionary at " + source, e);
        } catch (KeywordDictionary.MalformedEntryException | IllegalArgumentException
                | IllegalStateException e) {
            throw new MojoException(e.getMessage() + " (dictionary: " + source + ")", e);
        }

        log.info("Read " + entries.size() + " keyword entries from " + source + ".");
        result.deferrals().forEach(deferral -> log.info("  deferred: " + deferral));
        if (!result.deferrals().isEmpty()) {
            log.info("  each deferred entry names its Komet counterpart in a trailing note in the"
                    + " chapter ('Komet concept:' when implemented, 'Related Komet concept(s):'"
                    + " when related); this goal does not extract them.");
        }
        log.info("Composing " + result.concepts().size() + " concepts under namespace "
                + namespace + ".");

        if (dryRun) {
            log.info("Dry run: " + target + " was not written.");
            return;
        }
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, result.source(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoException("Could not write " + target, e);
        }
        log.info("Wrote " + target + ".");
    }
}
