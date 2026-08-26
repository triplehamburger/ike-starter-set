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
import network.ike.plugin.CqlKeywordSetGenerator.Generated;
import network.ike.plugin.CqlKeywordSetGenerator.Options;
import org.apache.maven.api.Project;
import org.apache.maven.api.ProjectScope;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.ProjectManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Generates ledger-form Java from the CQL keyword dictionary: reads
 * {@code keyword-dictionary.adoc}, authors a {@code compose(KnowledgeSet)} class in the shape
 * {@code FoundationSet}'s sections have, writes it into generated-sources, and registers that
 * root as a compile source root.
 *
 * <p>This is the inbound counterpart of {@code ike:knowledge-export}, which replays a ledger
 * outward. Like {@code ike:knowledge-bindings} it is a source generator, and like that goal it
 * carries no store dependency — it emits text, and the module it generates into is what compiles
 * against the chronology builders.
 *
 * <p>The goal never authors into a ledger. It produces source a human reviews and commits, and a
 * companion identity report listing the UUID each generated name resolves to, so an identity move
 * shows up as a diff rather than as a surprise in a released set.
 *
 * <p><b>Idempotency</b> comes from the dictionary's own Komet status field. {@code Implemented in
 * Komet} entries already exist in the ledger and are not re-minted; {@code Related concept in
 * Komet} entries need a human to decide what they link to and are not guessed at. Both are listed
 * in the identity report — nothing is dropped silently, and any entry whose shape the reader does
 * not recognise fails the build outright.
 *
 * <pre>{@code
 * <plugin>
 *     <groupId>network.ike.tooling</groupId>
 *     <artifactId>ike-maven-plugin</artifactId>
 *     <executions>
 *         <execution>
 *             <goals><goal>knowledge-generate</goal></goals>
 *             <configuration>
 *                 <dictionaryFile>src/docs/asciidoc/cql/keyword-dictionary.adoc</dictionaryFile>
 *                 <packageName>network.ike.cql.terms</packageName>
 *                 <namespace>d890e06f-ec35-429a-b541-d0ead19695e2</namespace>
 *                 <semanticTag>IkeCql</semanticTag>
 *                 <rootConceptName>CQL keyword (IkeCql)</rootConceptName>
 *                 <stampExpression>network.ike.cql.terms.IkeCql.INCEPTION</stampExpression>
 *             </configuration>
 *         </execution>
 *     </executions>
 * </plugin>
 * }</pre>
 *
 * <p>Prototype note: staged in {@code ike-starter-set} rather than in {@code ike-tooling}. The
 * lift is a copy of this package's three classes into
 * {@code ike-maven-plugin/src/main/java/network/ike/plugin/}, plus a {@code KNOWLEDGE_GENERATE}
 * constant and its {@code NAME_KNOWLEDGE_GENERATE} mirror in {@code IkeGoal}, at which point
 * {@link #GOAL} below is replaced by {@code IkeGoal.NAME_KNOWLEDGE_GENERATE}.
 */
@Mojo(name = KnowledgeGenerateMojo.GOAL,
      defaultPhase = "generate-sources")
public class KnowledgeGenerateMojo implements org.apache.maven.api.plugin.Mojo {

    /** The goal name — {@code IkeGoal.NAME_KNOWLEDGE_GENERATE} once this lifts into that enum. */
    static final String GOAL = "knowledge-generate";

    /** Creates this goal instance. */
    public KnowledgeGenerateMojo() {}

    @Inject
    private org.apache.maven.api.plugin.Log log;

    /**
     * Access the Maven logger.
     *
     * @return the logger
     */
    protected org.apache.maven.api.plugin.Log getLog() {
        return log;
    }

    @Inject
    private Session session;

    @Inject
    private Project project;

    /**
     * The CQL keyword dictionary to read. A relative path resolves against the project's own
     * directory, not the reactor root.
     */
    @Parameter(property = "ike.knowledgeGenerate.dictionaryFile", required = true)
    String dictionaryFile;

    /**
     * Package of the generated class.
     */
    @Parameter(property = "ike.knowledgeGenerate.packageName", required = true)
    String packageName;

    /**
     * Simple name of the generated class.
     */
    @Parameter(property = "ike.knowledgeGenerate.className", defaultValue = "CqlKeywordSet")
    String className;

    /**
     * The target knowledge set's namespace UUID — the type-5 root every identity in the set
     * derives from. It must be the namespace the consuming module's {@code KnowledgeSet} is
     * declared with, or the reported UUIDs will not be the ones actually authored.
     */
    @Parameter(property = "ike.knowledgeGenerate.namespace", required = true)
    String namespace;

    /**
     * The set's semantic tag — the parenthesised suffix every fully qualified name in the set
     * carries, as in {@code after (IkeCql)}. Part of the name, and therefore part of identity.
     */
    @Parameter(property = "ike.knowledgeGenerate.semanticTag", required = true)
    String semanticTag;

    /**
     * Fully qualified name of the root concept the keyword families hang from, semantic tag
     * included. Authored by this goal, so the generated set closes over every name it mentions
     * except {@link #rootParentExpression}.
     */
    @Parameter(property = "ike.knowledgeGenerate.rootConceptName", required = true)
    String rootConceptName;

    /**
     * Java expression for the root concept's own parent, emitted verbatim. The root hangs off a
     * term from outside this set, which is referenced by constant rather than by name — normally
     * a fully qualified one, so the generated file needs no import for it.
     */
    @Parameter(property = "ike.knowledgeGenerate.rootParentExpression", required = true)
    String rootParentExpression;

    /**
     * Java expression yielding the {@code ActiveStamp} the generated class authors at, emitted
     * verbatim — normally a fully qualified constant, so the generated file needs no import for
     * it.
     */
    @Parameter(property = "ike.knowledgeGenerate.stampExpression", required = true)
    String stampExpression;

    /**
     * Root directory for the generated sources; registered as a compile source root.
     */
    @Parameter(property = "ike.knowledgeGenerate.outputDirectory",
               defaultValue = "${project.build.directory}/generated-sources/ike-knowledge")
    String outputDirectory;

    /**
     * Where the identity report is written — the minted name-to-UUID rows and the entries the
     * dictionary's Komet status withholds.
     */
    @Parameter(property = "ike.knowledgeGenerate.identityReportFile",
               defaultValue = "${project.build.directory}/ike-knowledge/cql-keyword-identities.txt")
    String identityReportFile;

    /**
     * Skip knowledge generation.
     */
    @Parameter(property = "ike.knowledgeGenerate.skip", defaultValue = "false")
    boolean skip;

    /**
     * Reads the dictionary, generates the ledger source and identity report, and registers the
     * generated-sources root.
     *
     * @throws MojoException if the dictionary is missing or malformed, the namespace is not a
     *                       UUID, or the outputs cannot be written
     */
    @Override
    public void execute() {
        if (skip) {
            getLog().info("ike:knowledge-generate skipped (ike.knowledgeGenerate.skip=true)");
            return;
        }

        Path dictionary = project.getBasedir().resolve(dictionaryFile);
        if (!Files.isRegularFile(dictionary)) {
            throw new MojoException("No keyword dictionary at " + dictionary.toAbsolutePath());
        }
        UUID setNamespace;
        try {
            setNamespace = UUID.fromString(namespace);
        } catch (IllegalArgumentException e) {
            throw new MojoException("namespace is not a UUID: " + namespace, e);
        }

        Generated generated;
        try {
            List<Entry> entries = CqlKeywordDictionary.parse(dictionary);
            getLog().info("Read " + entries.size() + " keyword entries from "
                    + dictionary.getFileName());
            generated = CqlKeywordSetGenerator.generate(entries, new Options(packageName, className,
                    setNamespace, semanticTag, rootConceptName, rootParentExpression,
                    stampExpression));
        } catch (RuntimeException e) {
            throw new MojoException("Keyword dictionary could not be read as knowledge: "
                    + e.getMessage(), e);
        }

        Path sourceRoot = project.getBasedir().resolve(outputDirectory);
        Path javaFile = sourceRoot.resolve(packageName.replace('.', '/'))
                .resolve(className + ".java");
        Path reportFile = project.getBasedir().resolve(identityReportFile);
        write(javaFile, generated.javaSource());
        write(reportFile, generated.identityReport());

        session.getService(ProjectManager.class)
                .addSourceRoot(project, ProjectScope.MAIN,
                        org.apache.maven.api.Language.JAVA_FAMILY, sourceRoot);
        getLog().info("Generated " + packageName + "." + className + " into " + sourceRoot
                + " (registered as compile source root) — " + generated.mintedConcepts()
                + " concepts authored, " + generated.deferredEntries()
                + " entries withheld by Komet status");
        getLog().info("Identity report: " + reportFile);
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoException("Cannot write " + file, e);
        }
    }
}
