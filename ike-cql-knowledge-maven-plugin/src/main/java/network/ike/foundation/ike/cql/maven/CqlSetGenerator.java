package network.ike.foundation.ike.cql.maven;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reads the CQL keyword dictionary chapter and emits it as ledger source: one
 * {@code compose(KnowledgeSet)} class carrying a concept per CQL keyword, hung under the family and
 * subfamily taxonomy the dictionary itself declares.
 *
 * <h2>Identity</h2>
 *
 * <p>No UUID appears in the output. {@code KnowledgeSet.concept(name)} derives the identity from
 * the fully qualified name, exactly as the hand-authored sections of {@code ike-terms} rely on, so
 * these are fresh mints rather than adopted identities.
 *
 * <p>That is also why a mis-parsed entry has to fail the build rather than warn. The keyword name
 * <em>is</em> the identity: a name read slightly wrong becomes a concept nobody can correct later
 * without minting a second one. Every entry is therefore parsed strictly, and the entry's own
 * {@code [[term-...]]} anchor is the independent check on the reading — the anchor is derived
 * from the keyword, so a title read wrongly no longer matches it.
 *
 * <h2>Keyword names</h2>
 *
 * <p>The keyword is the whole section title with AsciiDoc bold markup removed, never the bolded
 * run inside it. Seven titles bold only part of the name ({@code === *include*d in},
 * {@code === *or* after}) or none of it ({@code === such that}); taking the bolded run would name
 * five different keywords {@code or}, and five concepts sharing a name share an identity.
 */
final class CqlSetGenerator {

    /** The parenthesised tag every generated fully qualified name ends with. */
    private static final String TAG = " (CQL)";

    /** The concept the whole generated taxonomy hangs from. */
    private static final String ROOT = "Clinical Quality Language";

    private static final String PACKAGE = "network.ike.foundation.ike.terms";
    private static final String CLASS = "CqlSet";

    /** The three Komet-status wordings the chapter uses, with their AsciiDoc emphasis stripped. */
    private static final List<String> STATUSES =
            List.of("Not yet in Komet", "Implemented in Komet", "Related concept in Komet");

    /** The trailing notes naming a deferred keyword's Komet counterpart. */
    private static final List<String> NOTES =
            List.of("Komet concept:", "Related Komet concept(s):");

    private CqlSetGenerator() {
    }

    /**
     * One dictionary entry. {@code family} is null until {@link #resolveFamilies} fills it in from
     * the subfamily; the entries that carry a Komet status other than "Not yet" state no family
     * path of their own.
     */
    private record Entry(String name, String subfamily, String family, String definition) {

        Entry withFamily(String resolved) {
            return new Entry(name, subfamily, resolved, definition);
        }
    }

    /** Where the generated source belongs, relative to an output root. */
    static String relativePath() {
        return PACKAGE.replace('.', '/') + "/" + CLASS + ".java";
    }

    /**
     * Generates the ledger source for a dictionary chapter.
     *
     * @param lines the chapter's lines, in order
     * @return the generated Java source
     * @throws IllegalArgumentException if any entry deviates from the shape every entry has
     */
    static String generate(List<String> lines) {
        List<Entry> entries = resolveFamilies(parse(lines));

        // Sorted, so the emitted order follows the content and never where an entry happens to sit
        // in the chapter. Sorting also puts every parent ahead of its children.
        Map<String, Map<String, List<Entry>>> tree = new TreeMap<>();
        for (Entry entry : entries) {
            tree.computeIfAbsent(entry.family(), f -> new TreeMap<>())
                    .computeIfAbsent(entry.subfamily(), s -> new ArrayList<>())
                    .add(entry);
        }
        tree.values().forEach(bySubfamily -> bySubfamily.values()
                .forEach(list -> list.sort(Comparator.comparing(Entry::name))));

        StringBuilder body = new StringBuilder();
        Set<String> minted = new LinkedHashSet<>();
        concept(body, minted, ROOT,
                "The Clinical Quality Language keyword vocabulary, as the CQL keyword dictionary"
                        + " chapter defines it.",
                "IkeTerm.MODEL_CONCEPT");
        tree.forEach((family, bySubfamily) -> {
            concept(body, minted, family,
                    "The " + family + " family of CQL keywords, as the CQL keyword dictionary"
                            + " groups them.",
                    parentRef(ROOT));
            bySubfamily.forEach((subfamily, keywords) -> {
                concept(body, minted, subfamily,
                        "The " + subfamily + " subfamily of CQL keywords, within the " + family
                                + " family.",
                        parentRef(family));
                keywords.forEach(keyword -> concept(body, minted, keyword.name(),
                        keyword.definition(), parentRef(subfamily)));
            });
        });
        return render(body, entries.size(), minted.size());
    }

    // ── Parsing ────────────────────────────────────────────────────────────────

    private static List<Entry> parse(List<String> lines) {
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("[[term-")) {
                starts.add(i);
            }
        }
        if (starts.isEmpty()) {
            throw new IllegalArgumentException("The dictionary declares no '[[term-...]]' entry.");
        }
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int to = i + 1 < starts.size() ? starts.get(i + 1) : lines.size();
            entries.add(parseEntry(lines.subList(starts.get(i), to)));
        }
        return entries;
    }

    private static Entry parseEntry(List<String> block) {
        String anchorLine = block.get(0);
        if (!anchorLine.endsWith("]]")) {
            throw malformed(anchorLine, "the anchor is not closed");
        }
        String anchor = anchorLine.substring(2, anchorLine.length() - 2);
        if (block.size() < 3 || !"[discrete]".equals(block.get(1))
                || !block.get(2).startsWith("=== ")) {
            throw malformed(anchor, "expected '[discrete]' then a '=== ' title after the anchor");
        }
        // The whole title, de-marked — not the bolded run inside it. See the class javadoc.
        String name = block.get(2).substring(4).replace("*", "").trim().replaceAll("\\s+", " ");
        String expected = "term-" + name.replace(' ', '-');
        if (!expected.equals(anchor)) {
            throw malformed(anchor, "the title reads as keyword '" + name + "', which would anchor"
                    + " at '" + expected + "'; title and anchor must agree");
        }
        for (int i = 3; i < block.size(); i++) {
            if (block.get(i).startsWith("=== ")) {
                throw malformed(anchor, "'" + block.get(i) + "' carries no anchor of its own, so it"
                        + " would be read as part of this entry");
            }
        }

        int meta = nextContent(block, 3, anchor, "the category line");
        int source = block.indexOf("[source,cql]");
        if (source < 0) {
            throw malformed(anchor, "no '[source,cql]' example block");
        }
        int definition = nextContent(block, meta + 1, anchor, "the definition");
        if (definition >= source) {
            throw malformed(anchor, "the entry carries no definition between its category line and"
                    + " its example block");
        }
        if (source + 1 >= block.size() || !"----".equals(block.get(source + 1))) {
            throw malformed(anchor, "'[source,cql]' is not followed by a '----' delimiter");
        }
        int close = block.subList(source + 2, block.size()).indexOf("----");
        if (close < 0) {
            throw malformed(anchor, "the example block is never closed");
        }
        String example = String.join("\n", block.subList(source + 2, source + 2 + close));

        String[] meta3 = parseMeta(block.get(meta), anchor);
        String note = "";
        for (int i = source + 2 + close + 1; i < block.size(); i++) {
            String line = demark(block.get(i));
            if (NOTES.stream().anyMatch(line::startsWith)) {
                note = " " + line;
            }
        }
        String prose = block.get(definition).trim() + "\n\nExample: " + example
                + "\n\nKomet status: " + meta3[2] + "." + note;
        return new Entry(name, meta3[0], meta3[1], prose);
    }

    /**
     * Reads {@code `Subfamily` -- (Family > Subfamily) Status} or its short form,
     * {@code `Subfamily` -- Status}, into {@code {subfamily, family-or-null, status}}. Emphasis is
     * stripped before the status is recognised, so an entry that changes only its markup still
     * parses.
     */
    private static String[] parseMeta(String line, String anchor) {
        int end = line.startsWith("`") ? line.indexOf('`', 1) : -1;
        int sep = end < 0 ? -1 : line.indexOf(" -- ", end);
        if (sep < 0) {
            throw malformed(anchor, "expected '`Subfamily` -- ...', found '" + line + "'");
        }
        String subfamily = line.substring(1, end).trim();
        String tail = line.substring(sep + 4).trim();

        String family = null;
        if (tail.startsWith("(")) {
            int paren = tail.indexOf(')');
            int arrow = paren < 0 ? -1 : tail.lastIndexOf('>', paren);
            if (arrow < 0) {
                throw malformed(anchor, "expected a '(Family > Subfamily)' path, found '" + tail + "'");
            }
            String stated = tail.substring(arrow + 1, paren).trim();
            if (!stated.equals(subfamily)) {
                throw malformed(anchor, "the path ends in '" + stated + "' but the category is '"
                        + subfamily + "'; the two name the same thing and must agree");
            }
            family = tail.substring(1, arrow).trim();
            tail = tail.substring(paren + 1).trim();
        }
        String status = demark(tail);
        if (!STATUSES.contains(status)) {
            throw malformed(anchor, "unknown Komet status '" + status + "'; expected one of "
                    + STATUSES);
        }
        return new String[] {subfamily, family, status};
    }

    /**
     * Fills in the family of every entry that states only a subfamily. The chapter always names
     * that subfamily's family in some other entry's parenthesised path, so the map is read from the
     * chapter rather than hardcoded here.
     */
    private static List<Entry> resolveFamilies(List<Entry> entries) {
        Map<String, String> familyOf = new LinkedHashMap<>();
        for (Entry entry : entries) {
            if (entry.family() == null) {
                continue;
            }
            String previous = familyOf.put(entry.subfamily(), entry.family());
            if (previous != null && !previous.equals(entry.family())) {
                throw malformed("term-" + entry.name(), "subfamily '" + entry.subfamily()
                        + "' is placed under both '" + previous + "' and '" + entry.family() + "'");
            }
        }
        List<Entry> resolved = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            if (entry.family() != null) {
                resolved.add(entry);
                continue;
            }
            String family = familyOf.get(entry.subfamily());
            if (family == null) {
                throw malformed("term-" + entry.name(), "states no family path, and no other entry"
                        + " places its subfamily '" + entry.subfamily() + "' under one");
            }
            resolved.add(entry.withFamily(family));
        }
        return resolved;
    }

    private static int nextContent(List<String> block, int from, String anchor, String what) {
        for (int i = from; i < block.size(); i++) {
            if (!block.get(i).isBlank()) {
                return i;
            }
        }
        throw malformed(anchor, "the entry ends before " + what);
    }

    private static String demark(String text) {
        return text.replace("*", "").replace("_", "").trim();
    }

    private static IllegalArgumentException malformed(String entry, String problem) {
        return new IllegalArgumentException("Keyword dictionary entry " + entry + ": " + problem);
    }

    // ── Emission ───────────────────────────────────────────────────────────────

    private static void concept(StringBuilder out, Set<String> minted, String name,
                                String definition, String parent) {
        String fqn = name + TAG;
        if (!minted.add(fqn)) {
            throw new IllegalArgumentException("Two concepts would both be named '" + fqn
                    + "'; one would silently take the other's identity.");
        }
        out.append("        set.concept(").append(literal(fqn, "")).append(").at(inception)\n")
                .append("                .synonym(").append(literal(name, "")).append(")\n")
                .append("                .definition(")
                .append(literal(definition, "                        ")).append(")\n")
                .append("                .isA(").append(parent).append(");\n\n");
    }

    private static String parentRef(String name) {
        return "set.conceptRef(" + literal(name + TAG, "") + ")";
    }

    private static String render(StringBuilder body, int keywords, int concepts) {
        return """
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
                package %s;

                import dev.ikm.tinkar.entity.builder.ActiveStamp;
                import dev.ikm.tinkar.entity.builder.KnowledgeSet;

                /**
                 * The CQL keyword vocabulary: %d concepts covering %d keywords and the families and
                 * subfamilies they sit under.
                 *
                 * <p>Generated from {@code cql/keyword-dictionary.adoc} by
                 * {@code ike-cql-knowledge:generate}. Do not edit — regenerate.
                 *
                 * <p>Each concept's identity is derived by the knowledge set from its fully qualified
                 * name, so these are fresh IKE mints. Where a keyword already has, or is adjacent to,
                 * a Komet concept, the dictionary says so and the definition carries that note; no
                 * identity mapping to Komet is asserted here.
                 */
                final class %s {

                    private %s() {
                    }

                    /**
                     * Composes this section's declarations into the session.
                     *
                     * @param set the knowledge set (the session)
                     */
                    static void compose(KnowledgeSet set) {
                        ActiveStamp inception = Ike.INCEPTION;

                %s    }
                }
                """.formatted(PACKAGE, concepts, keywords, CLASS, CLASS, body);
    }

    /**
     * Renders {@code text} as a Java string literal, wrapped onto continuation lines at spaces so
     * the generated source stays readable. Concatenating the pieces reproduces the text exactly:
     * a break moves its space to the front of the next piece.
     */
    private static String literal(String text, String continuation) {
        List<String> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        for (String word : text.split(" ", -1)) {
            if (piece.isEmpty()) {
                piece.append(word);
            } else if (continuation.isEmpty() || piece.length() + 1 + word.length() <= 70) {
                piece.append(' ').append(word);
            } else {
                pieces.add(piece.toString());
                piece = new StringBuilder(" ").append(word);
            }
        }
        pieces.add(piece.toString());
        StringBuilder out = new StringBuilder();
        for (String p : pieces) {
            if (!out.isEmpty()) {
                out.append('\n').append(continuation).append("+ ");
            }
            out.append('"');
            for (char c : p.toCharArray()) {
                switch (c) {
                    case '\\' -> out.append("\\\\");
                    case '"' -> out.append("\\\"");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> out.append(c);
                }
            }
            out.append('"');
        }
        return out.toString();
    }
}
