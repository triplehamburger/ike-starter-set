package network.ike.foundation.ike.cql.maven.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The CQL keyword dictionary chapter, read as data.
 *
 * <p>Every entry in {@code cql/keyword-dictionary.adoc} has the same shape, and this parser
 * insists on it. Nothing is skipped: a block that does not match is a
 * {@link MalformedEntryException}, never a quietly dropped keyword. The dictionary's metadata line
 * is genuinely not uniform — most entries carry a {@code (Family &gt; Subcategory)} path and some
 * carry only a status — so both forms are parsed explicitly and any third form fails.
 *
 * <p>The keyword name is the section title with AsciiDoc bold markup removed, not the bolded run
 * inside it. Seven titles bold only part of the name ({@code === *include*d in},
 * {@code === *or* after}) and one bolds none of it ({@code === such that}); taking the bolded run
 * would name five different keywords {@code or}, and five concepts sharing a name share an
 * identity. The anchor is the independent check on that reading: every entry declares
 * {@code [[term-<name with spaces hyphenated>]]}, so a name read wrongly no longer matches its own
 * anchor and the parse fails.
 */
public final class KeywordDictionary {

    private static final String ANCHOR_PREFIX = "[[term-";
    private static final String TITLE_PREFIX = "=== ";
    private static final String SOURCE_BLOCK = "[source,cql]";
    private static final String DELIMITER = "----";

    private KeywordDictionary() {
    }

    /** How far along the dictionary's author is on giving a keyword a Komet identity. */
    public enum KometStatus {

        /** No Komet concept yet: the generator may mint one. */
        NOT_YET("Not yet in Komet"),

        /**
         * Komet has something adjacent. Whether the new concept should be linked to it, or not
         * minted at all, is an editorial call — so the generator defers these rather than guessing
         * at a relationship.
         *
         * <p>The chapter names those counterparts in a {@code Related Komet concept(s):} note after
         * the entry's example block, and this parser does <em>not</em> read it: parsing stops at
         * the closing example delimiter and the note is knowingly dropped. Whoever takes up the
         * editorial call reads it from the chapter.
         */
        RELATED("Related concept in Komet"),

        /** The concept already exists in Komet: minting again would duplicate an identity. */
        IMPLEMENTED("Implemented in Komet");

        private final String text;

        KometStatus(String text) {
            this.text = text;
        }

        /**
         * Returns the status wording as the dictionary writes it, without the AsciiDoc emphasis
         * that distinguishes the three in rendered output.
         *
         * @return the unmarked status text
         */
        public String text() {
            return text;
        }

        static KometStatus of(String unmarked, String entry) {
            for (KometStatus status : values()) {
                if (status.text.equals(unmarked)) {
                    return status;
                }
            }
            throw new MalformedEntryException(entry,
                    "unknown Komet status '" + unmarked + "'; expected one of "
                            + List.of(NOT_YET.text, RELATED.text, IMPLEMENTED.text));
        }
    }

    /**
     * One dictionary entry.
     *
     * @param name       the keyword, exactly as the dictionary titles it
     * @param anchor     the entry's stable AsciiDoc anchor, {@code term-<name>}
     * @param category   the backticked category, e.g. {@code Timing Operator}
     * @param family     the top-level family the category sits under, absent when the entry gives
     *                   no family path
     * @param status     where the keyword stands in Komet
     * @param definition the prose definition, one paragraph
     * @param example    the CQL example, verbatim, newline-separated
     */
    public record Entry(String name,
                        String anchor,
                        String category,
                        Optional<String> family,
                        KometStatus status,
                        String definition,
                        String example) {
    }

    /** A dictionary block that does not match the shape every entry is supposed to have. */
    public static final class MalformedEntryException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        MalformedEntryException(String entry, String problem) {
            super("Keyword dictionary entry " + entry + ": " + problem);
        }
    }

    /**
     * Parses every entry in the dictionary.
     *
     * @param lines the chapter's lines, in order
     * @return the entries, in the order the dictionary lists them
     * @throws MalformedEntryException if any block deviates from the expected shape, if a keyword
     *         section carries no anchor of its own, or if two entries claim the same keyword
     */
    public static List<Entry> parse(List<String> lines) {
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(ANCHOR_PREFIX)) {
                starts.add(i);
            }
        }
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = i + 1 < starts.size() ? starts.get(i + 1) : lines.size();
            Entry entry = parseEntry(lines.subList(from, to));
            if (!seen.add(entry.name())) {
                throw new MalformedEntryException(entry.anchor(),
                        "keyword '" + entry.name() + "' is already defined by an earlier entry");
            }
            entries.add(entry);
        }
        Set<Integer> titled = new LinkedHashSet<>();
        starts.forEach(start -> titled.add(start + 2));
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(TITLE_PREFIX) && !titled.contains(i)) {
                throw new MalformedEntryException(lines.get(i).trim(),
                        "the section carries no '" + ANCHOR_PREFIX + "...]]' anchor of its own, so"
                                + " it would be read as part of another entry");
            }
        }
        return entries;
    }

    private static Entry parseEntry(List<String> block) {
        String anchorLine = block.get(0);
        if (!anchorLine.endsWith("]]")) {
            throw new MalformedEntryException(anchorLine, "the anchor is not closed");
        }
        String anchor = anchorLine.substring(2, anchorLine.length() - 2);

        if (block.size() < 3 || !"[discrete]".equals(block.get(1))
                || !block.get(2).startsWith(TITLE_PREFIX)) {
            throw new MalformedEntryException(anchor,
                    "expected '[discrete]' then a '=== ' title on the two lines after the anchor");
        }
        String name = block.get(2).substring(TITLE_PREFIX.length()).replace("*", "").trim();
        if (name.isEmpty()) {
            throw new MalformedEntryException(anchor, "the title carries no keyword");
        }
        String expectedAnchor = "term-" + name.replace(' ', '-');
        if (!expectedAnchor.equals(anchor)) {
            throw new MalformedEntryException(anchor,
                    "the title reads as keyword '" + name + "', which would anchor at '"
                            + expectedAnchor + "'; title and anchor must agree");
        }

        int metadata = nextContentLine(block, 3, anchor, "the category/status line");
        Metadata parsed = parseMetadata(block.get(metadata), anchor);

        int source = block.indexOf(SOURCE_BLOCK);
        if (source < 0) {
            throw new MalformedEntryException(anchor, "no '" + SOURCE_BLOCK + "' example block");
        }
        int definition = nextContentLine(block, metadata + 1, anchor, "the definition");
        if (definition >= source) {
            throw new MalformedEntryException(anchor,
                    "the entry carries no definition paragraph between its category/status line"
                            + " and its '" + SOURCE_BLOCK + "' example block");
        }
        for (int i = definition + 1; i < source; i++) {
            if (!block.get(i).isBlank()) {
                throw new MalformedEntryException(anchor,
                        "the definition runs to more than one paragraph, which this generator does"
                                + " not know how to carry into a single definition semantic");
            }
        }

        int open = source + 1;
        if (open >= block.size() || !DELIMITER.equals(block.get(open))) {
            throw new MalformedEntryException(anchor,
                    "'" + SOURCE_BLOCK + "' is not followed by a '" + DELIMITER + "' delimiter");
        }
        int close = block.subList(open + 1, block.size()).indexOf(DELIMITER);
        if (close < 0) {
            throw new MalformedEntryException(anchor, "the example block is never closed");
        }
        String example = String.join("\n", block.subList(open + 1, open + 1 + close));

        return new Entry(name, anchor, parsed.category(), parsed.family(), parsed.status(),
                block.get(definition).trim(), example);
    }

    private record Metadata(String category, Optional<String> family, KometStatus status) {
    }

    /**
     * Reads {@code `Category` -- (Family > Subcategory) Status} or its short form,
     * {@code `Category` -- Status}. The status carries AsciiDoc emphasis that encodes which of the
     * three values it is in rendered output; here the emphasis is stripped and the wording alone
     * decides, so a future entry that changes only the markup still parses.
     */
    private static Metadata parseMetadata(String line, String anchor) {
        if (!line.startsWith("`")) {
            throw new MalformedEntryException(anchor,
                    "expected a backticked category, found '" + line + "'");
        }
        int categoryEnd = line.indexOf('`', 1);
        int separator = line.indexOf(" -- ", categoryEnd);
        if (categoryEnd < 0 || separator < 0) {
            throw new MalformedEntryException(anchor,
                    "expected '`Category` -- ...', found '" + line + "'");
        }
        String category = line.substring(1, categoryEnd).trim();
        String tail = line.substring(separator + 4).trim();

        Optional<String> family = Optional.empty();
        if (tail.startsWith("(")) {
            int end = tail.indexOf(')');
            if (end < 0) {
                throw new MalformedEntryException(anchor, "the family path is not closed");
            }
            String path = tail.substring(1, end).trim();
            int arrow = path.indexOf('>');
            if (arrow < 0) {
                throw new MalformedEntryException(anchor,
                        "expected a 'Family > Subcategory' path, found '" + path + "'");
            }
            String subcategory = path.substring(arrow + 1).trim();
            if (!subcategory.equals(category)) {
                throw new MalformedEntryException(anchor,
                        "the family path ends in '" + subcategory + "' but the category is '"
                                + category + "'; the two name the same thing and must agree");
            }
            family = Optional.of(path.substring(0, arrow).trim());
            tail = tail.substring(end + 1).trim();
        }

        KometStatus status = KometStatus.of(unmark(tail), anchor);
        if (status == KometStatus.NOT_YET && family.isEmpty()) {
            throw new MalformedEntryException(anchor,
                    "a generatable keyword must declare its 'Family > Subcategory' path — without"
                            + " one there is nothing to hang the concept from");
        }
        return new Metadata(category, family, status);
    }

    private static String unmark(String text) {
        String unmarked = text.trim();
        for (String marker : List.of("*", "_")) {
            if (unmarked.length() > 2 * marker.length() && unmarked.startsWith(marker)
                    && unmarked.endsWith(marker)) {
                unmarked = unmarked.substring(marker.length(),
                        unmarked.length() - marker.length()).trim();
            }
        }
        return unmarked;
    }

    private static int nextContentLine(List<String> block, int from, String anchor, String what) {
        for (int i = from; i < block.size(); i++) {
            if (!block.get(i).isBlank()) {
                return i;
            }
        }
        throw new MalformedEntryException(anchor, "the entry ends before " + what);
    }
}
