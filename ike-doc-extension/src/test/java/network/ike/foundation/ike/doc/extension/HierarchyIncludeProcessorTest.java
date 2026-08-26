package network.ike.foundation.ike.doc.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import network.ike.foundation.ike.hierarchy.index.HierarchyIndex;
import network.ike.foundation.ike.hierarchy.index.IndexCodec;
import network.ike.foundation.ike.hierarchy.index.IndexedChapter;
import network.ike.foundation.ike.hierarchy.index.IndexedRoot;
import network.ike.foundation.ike.hierarchy.model.ChapterId;
import network.ike.foundation.ike.hierarchy.model.ChapterStatus;

/** Verifies the ways a guide could otherwise come out wrong while the render stays green. */
class HierarchyIncludeProcessorTest {

    /** Writes a self-contained one-chapter project: assembly root, chapter, and index. */
    private static void project(Path dir, String chapterId, String title) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("guide.adoc"),
                ":chapter-id: guide\n:chapter-root:\n\n= Guide\n\ninclude::hierarchy:guide[]\n");
        Files.writeString(dir.resolve("chapter.adoc"),
                ":chapter-id: " + chapterId + "\n:chapter-parent: guide\n\n= " + title + "\n");
        HierarchyIndex index = new HierarchyIndex(HierarchyIndex.SCHEMA, 5,
                List.of(new IndexedRoot("docs", ".")),
                List.of(
                        new IndexedChapter(new ChapterId("guide"), "Guide", Optional.empty(), 0,
                                ChapterStatus.PUBLISHED, true, 0, "docs", "guide.adoc"),
                        new IndexedChapter(new ChapterId(chapterId), title,
                                Optional.of(new ChapterId("guide")), 100,
                                ChapterStatus.PUBLISHED, false, 1, "docs", "chapter.adoc")));
        Files.writeString(dir.resolve("index.json"), IndexCodec.write(index));
    }

    private static String render(Asciidoctor asciidoctor, Path dir) {
        Attributes attributes = Attributes.builder()
                .attribute("ike-hierarchy-index", dir.resolve("index.json").toString())
                .attribute("ike-hierarchy-base", dir.toString())
                .build();
        return asciidoctor.convertFile(dir.resolve("guide.adoc").toFile(), Options.builder()
                .baseDir(dir.toFile())
                .safe(SafeMode.UNSAFE)
                .backend("html5")
                .toFile(false)
                .attributes(attributes)
                .build());
    }

    /** S2: a missing index used to render a guide of warnings and exit green. */
    @Test
    void shouldFailTheRenderWhenTheIndexCannotBeLoadedAtAll(@TempDir Path temp) throws IOException {
        project(temp, "alpha", "Alpha");
        Files.delete(temp.resolve("index.json"));

        Asciidoctor asciidoctor = Asciidoctor.Factory.create();
        try {
            new HierarchyExtensionRegistry().register(asciidoctor);
            // Asciidoctor reports the failure under its own generic message and keeps ours as the
            // cause, which is what reaches the build log.
            assertThatThrownBy(() -> render(asciidoctor, temp))
                    .rootCause()
                    .hasMessageContaining("ike-hierarchy")
                    .hasMessageContaining("mvn ike-hierarchy:index");
        } finally {
            asciidoctor.shutdown();
        }
    }

    /** S2: one unresolvable id is still a warning, not a build failure. */
    @Test
    void shouldStillOnlyWarnForASingleUnresolvableId(@TempDir Path temp) throws IOException {
        project(temp, "alpha", "Alpha");
        Files.writeString(temp.resolve("guide.adoc"),
                ":chapter-id: guide\n:chapter-root:\n\n= Guide\n\ninclude::chapter:nope[]\n");

        Asciidoctor asciidoctor = Asciidoctor.Factory.create();
        try {
            new HierarchyExtensionRegistry().register(asciidoctor);
            assertThat(render(asciidoctor, temp)).contains("no chapter with id 'nope'");
        } finally {
            asciidoctor.shutdown();
        }
    }

    /** S4: one Asciidoctor instance used to serve the first project's index to every later one. */
    @Test
    void shouldNotServeOneProjectsIndexToAnother(@TempDir Path temp) throws IOException {
        Path a = temp.resolve("a");
        Path b = temp.resolve("b");
        project(a, "alpha", "Alpha");
        project(b, "beta", "Beta");

        Asciidoctor asciidoctor = Asciidoctor.Factory.create();
        try {
            new HierarchyExtensionRegistry().register(asciidoctor);
            assertThat(render(asciidoctor, a)).contains("Alpha");
            assertThat(render(asciidoctor, b)).contains("Beta").doesNotContain("Alpha");
        } finally {
            asciidoctor.shutdown();
        }
    }
}
