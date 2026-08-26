package network.ike.foundation.ike.cql.maven;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.api.Language;
import org.apache.maven.api.Lifecycle.Phase;
import org.apache.maven.api.Project;
import org.apache.maven.api.ProjectScope;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.ProjectManager;

/**
 * Generates the CQL keyword vocabulary as ledger source from the keyword dictionary chapter, and
 * registers the output as a compile source root of the project it runs in.
 *
 * <p>The goal fails the build on any entry it does not recognise. A keyword that parsed "nearly"
 * would become a concept with the wrong name, and because the name derives the identity, a wrong
 * name is a wrong identity no later edit can quietly correct.
 *
 * <p>Nothing is authored into a ledger by running this: the output is source, written under the
 * build directory, for the consuming module to compose deliberately.
 *
 * <p>Bound to {@code initialize} for the same reason {@code ike-hierarchy:index} is: the Maven 4
 * {@code Phase.SOURCES} constant is not one maven-plugin-tools 4.0.0-beta-2 can map to a phase, and
 * this goal only has to run before anything reads its output.
 */
@Mojo(name = "generate", defaultPhase = Phase.INITIALIZE)
public class GenerateMojo implements org.apache.maven.api.plugin.Mojo {

    @Inject
    private Log log;

    @Inject
    private Project project;

    @Inject
    private Session session;

    /** The keyword dictionary chapter to read. */
    @Parameter(defaultValue = "${project.basedir}/src/docs/asciidoc/cql/keyword-dictionary.adoc",
            property = "ike-cql-knowledge.dictionary")
    private String dictionary;

    /** Where the generated source tree is written, and added as a compile source root. */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/ike-cql-knowledge",
            property = "ike-cql-knowledge.outputDirectory")
    private String outputDirectory;

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
        Path root = Path.of(outputDirectory);
        Path target = root.resolve(CqlSetGenerator.relativePath());

        String generated;
        try {
            generated = CqlSetGenerator.generate(
                    Files.readAllLines(source, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new MojoException("Could not read the keyword dictionary at " + source, e);
        } catch (IllegalArgumentException e) {
            throw new MojoException(e.getMessage() + " (dictionary: " + source + ")", e);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, generated, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoException("Could not write " + target, e);
        }
        session.getService(ProjectManager.class)
                .addSourceRoot(project, ProjectScope.MAIN, Language.JAVA_FAMILY, root);
        log.info("Wrote " + target + " from " + source + ".");
    }
}
