package network.ike.foundation.ike.hierarchy.maven.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import network.ike.foundation.ike.hierarchy.scan.ScanLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChapterRegistrarTest {

    @TempDir
    Path temp;

    @Test
    void shouldStampAFreshFile() throws IOException {
        Path fresh = write("new.adoc", "= New\n");

        GoalReport report = register(fresh, "my-chapter");

        assertThat(report.failed()).isFalse();
        assertThat(Files.readString(fresh)).contains(":chapter-id: my-chapter");
    }

    @Test
    void shouldRepairAFileThatDeclaresAParentButNoId() throws IOException {
        Path handWritten = write("my.adoc", ":chapter-parent: ike-guide\n\n= My chapter\n");

        GoalReport report = register(handWritten, "my-chapter");

        assertThat(report.failed()).isFalse();
        assertThat(Files.readString(handWritten)).contains(":chapter-id: my-chapter");
    }

    @Test
    void shouldStampButWarnWhenAnotherFileCannotBeReadAsAChapter() throws IOException {
        write("broken.adoc", ":chapter-parent: ike-guide\n\n= Broken\n");
        Path fresh = write("new.adoc", "= New\n");

        GoalReport report = register(fresh, "my-chapter");

        assertThat(report.failed()).isFalse();
        assertThat(report.warnings()).anySatisfy(warning ->
                assertThat(warning).contains("broken.adoc").contains("my-chapter"));
        assertThat(Files.readString(fresh)).contains(":chapter-id: my-chapter");
    }

    @Test
    void shouldStampAnOversizedFileRatherThanTreatingTheScanAsIncomplete() throws IOException {
        Path big = write("big.adoc", "= Big\n\n" + "x".repeat(4096) + "\n");
        ScanLimits tight = new ScanLimits(5_000, 1_024, 200, 500, 24, Set.of());

        GoalReport report = register(big, "big", tight);

        assertThat(report.failed()).isFalse();
        assertThat(Files.readString(big)).contains(":chapter-id: big");
    }

    @Test
    void shouldRefuseAnIdAlreadyDeclaredElsewhere() throws IOException {
        write("taken.adoc", ":chapter-id: cql\n:chapter-parent: guide\n\n= Taken\n");
        Path fresh = write("new.adoc", "= New\n");

        GoalReport report = register(fresh, "cql");

        assertThat(report.failed()).isTrue();
        assertThat(Files.readString(fresh)).doesNotContain(":chapter-id:");
    }

    @Test
    void shouldRefuseToDeclareAnIdFreeWhenTheScanCouldNotComplete() throws IOException {
        write("taken.adoc", ":chapter-id: cql\n:chapter-parent: guide\n\n= Taken\n");
        Path fresh = write("new.adoc", "= New\n");
        Path locked = Files.createDirectory(temp.resolve("locked"));
        assumeTrue(locked.toFile().setReadable(false) && !Files.isReadable(locked),
                "cannot make a directory unreadable here");
        try {
            GoalReport report = register(fresh, "cql");

            assertThat(report.failed()).isTrue();
            assertThat(Files.readString(fresh)).doesNotContain(":chapter-id:");
        } finally {
            locked.toFile().setReadable(true);
        }
    }

    private GoalReport register(Path file, String id) {
        return register(file, id, ScanLimits.defaults());
    }

    private GoalReport register(Path file, String id, ScanLimits limits) {
        return ChapterRegistrar.run(temp, List.of(), file.toString(), Optional.of(id),
                Optional.empty(), Optional.of("guide"), Optional.empty(), Optional.empty(),
                false, limits, true);
    }

    private Path write(String name, String content) throws IOException {
        Path file = temp.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
