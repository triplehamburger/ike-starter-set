package network.ike.plugin.hierarchy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import network.ike.hierarchy.scan.ScanLimits;

/**
 * The small amount of translation between Maven's configuration surface and the plain-Java support
 * classes that do the work.
 *
 * <p>Kept out of the mojos so that the mojos contain nothing but wiring. Every rule this system
 * enforces lives in a class with no Maven dependency at all, which is what allows it to be tested
 * without starting a build.
 */
public final class MojoSupport {

    private MojoSupport() {
    }

    /**
     * Splits a comma-separated configuration value into a list.
     *
     * <p>Comma-separated rather than a Maven list because these goals are run from the command
     * line, where {@code -Dike-hierarchy.roots=ike-doc,ike-terms} is something an author can type
     * and a nested XML list is not.
     *
     * @param value the raw value, possibly null or blank
     * @return the trimmed, non-empty entries
     */
    public static List<String> splitList(String value) {
        List<String> entries = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return entries;
        }
        for (String entry : value.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        return entries;
    }

    /**
     * Wraps a possibly blank configuration value.
     *
     * @param value the raw value
     * @return the value when it carries something, otherwise empty
     */
    public static Optional<String> optional(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    /**
     * Builds scan limits from configured overrides, falling back to the defaults.
     *
     * @param maxFiles       the file cap, or a non-positive value to use the default
     * @param maxFileBytes   the size cap, or a non-positive value to use the default
     * @param excludes       comma-separated directory names to exclude, or blank for the defaults
     * @return the limits to scan with
     */
    public static ScanLimits limits(int maxFiles, long maxFileBytes, String excludes) {
        ScanLimits defaults = ScanLimits.defaults();
        List<String> configuredExcludes = splitList(excludes);
        return new ScanLimits(
                maxFiles > 0 ? maxFiles : defaults.maxFiles(),
                maxFileBytes > 0 ? maxFileBytes : defaults.maxFileBytes(),
                defaults.maxHeaderLines(),
                defaults.maxValueLength(),
                defaults.maxDirectoryDepth(),
                configuredExcludes.isEmpty()
                        ? defaults.excludedDirectoryNames()
                        : java.util.Set.copyOf(configuredExcludes));
    }

    /**
     * Emits a report through a Maven log.
     *
     * @param report the report to emit
     * @param log    the log to write to
     */
    public static void log(HierarchyReport report, org.apache.maven.api.plugin.Log log) {
        report.infoLines().forEach(log::info);
        report.warnings().forEach(log::warn);
        report.failures().forEach(log::error);
    }
}
