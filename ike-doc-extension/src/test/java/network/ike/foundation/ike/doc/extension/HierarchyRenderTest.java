package network.ike.foundation.ike.doc.extension;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class HierarchyRenderTest {

    @Test
    void renderIkeGuideHtml() throws IOException {
        Path projectRoot = Paths.get("..").toAbsolutePath().normalize();
        if (!Files.exists(projectRoot.resolve("ike-doc"))) {
            projectRoot = Paths.get(".").toAbsolutePath().normalize();
        }

        Path guidePath = projectRoot.resolve("ike-doc/src/docs/asciidoc/ike-guide.adoc");
        Path indexPath = projectRoot.resolve("ike-doc/target/ike-hierarchy-index.json");
        Path outputDir = projectRoot.resolve("ike-doc/target/generated-docs");

        if (!Files.exists(indexPath)) {
            return;
        }

        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve("ike-guide.html");

        Asciidoctor asciidoctor = Asciidoctor.Factory.create();
        try {
            new HierarchyExtensionRegistry().register(asciidoctor);

            Attributes attributes = Attributes.builder()
                    .attribute("ike-hierarchy-index", indexPath.toString())
                    .attribute("ike-hierarchy-base", projectRoot.toString())
                    .attribute("toc", "left")
                    .attribute("sectnums", "true")
                    .build();

            Options options = Options.builder()
                    .baseDir(guidePath.getParent().toFile())
                    .safe(SafeMode.UNSAFE)
                    .backend("html5")
                    .toFile(outputFile.toFile())
                    .attributes(attributes)
                    .build();

            asciidoctor.convertFile(guidePath.toFile(), options);

            assertThat(outputFile).exists();
            String html = Files.readString(outputFile);
            assertThat(html).contains("Ike Starter Set");
            assertThat(html).contains("How to Read This Guide");
            assertThat(html).contains("Tinkar Base Model");
            assertThat(html).contains("CQL in Komet");
            assertThat(html).contains("Running CQL Queries");
            assertThat(html).contains("Types &amp; Literals");
            assertThat(html).contains("Query &amp; Control Flow");
            assertThat(html).doesNotContain("All 122 CQL keywords");
        } finally {
            asciidoctor.shutdown();
        }
    }
}
