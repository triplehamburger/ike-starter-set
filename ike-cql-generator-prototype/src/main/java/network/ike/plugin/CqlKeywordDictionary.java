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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict reader for the CQL keyword dictionary
 * ({@code ike-doc/src/docs/asciidoc/cql/keyword-dictionary.adoc}).
 *
 * <p>Every entry must match the grammar below exactly. Anything else throws
 * {@link MalformedEntryException} naming the line — this reader never skips, never guesses,
 * and never degrades to a partial parse. That is deliberate: the alternative failure mode is a
 * green build carrying silently wrong content, which is the worse outcome for a generator whose
 * output becomes permanent asserted identity.
 *
 * <pre>
 * [[term-&lt;Anchor&gt;]]
 * [discrete]
 * === &lt;keyword, optionally carrying AsciiDoc bold markers&gt;
 *
 * `&lt;Category&gt;` -- [(&lt;Family&gt; &gt; &lt;Subcategory&gt;) ]&lt;Status&gt;
 *
 * &lt;single-paragraph definition&gt;
 *
 * [source,cql]
 * ----
 * &lt;example&gt;
 * ----
 *
 * [Komet concept: &lt;names&gt; | Related Komet concept(s): &lt;names&gt;]
 *
 * * * *
 * </pre>
 *
 * <p>Two shapes in that grammar are easy to get wrong and are enforced rather than assumed:
 *
 * <ul>
 *   <li><b>The metadata line has two forms.</b> 115 of 122 entries carry the parenthesised
 *       {@code (Family > Subcategory)} path; 7 use a bare {@code -- Status}. A reader that
 *       expects only the first form drops or mangles those 7.
 *   <li><b>The heading is prose, the anchor is the identifier.</b> Bold markers do not reliably
 *       wrap the whole keyword ({@code === *include*d in}, {@code === *or* after},
 *       {@code === such that}), so the keyword is the heading with markers stripped — and that
 *       result is cross-checked against the anchor. Because an anchor renders every run of
 *       non-alphanumerics as one {@code -}, agreement alone would still admit rewordings the
 *       anchor cannot tell apart ({@code or after} and {@code or-after} both derive
 *       {@code term-or-after}, yet mint different concepts). So the keyword must also be
 *       alphanumeric words separated by single spaces, which makes the anchor pin the keyword
 *       exactly. All 122 entries satisfy both halves, and together they are what makes the
 *       keyword safe to feed into identity.
 * </ul>
 */
final class CqlKeywordDictionary {

    /** The Komet status of one entry — the field that decides whether it may be minted. */
    enum Status {
        /** Generatable: no counterpart exists in Komet. */
        NOT_YET_IN_KOMET("Not yet in Komet"),
        /** Not generatable: a related concept exists, and which one to link is a human call. */
        RELATED_CONCEPT_IN_KOMET("_Related concept in Komet_"),
        /** Not generatable: the concept already exists; minting it again would duplicate it. */
        IMPLEMENTED_IN_KOMET("*Implemented in Komet*");

        private final String markup;

        Status(String markup) {
            this.markup = markup;
        }

        static Status ofMarkup(String text, int line) {
            for (Status s : values()) {
                if (s.markup.equals(text)) {
                    return s;
                }
            }
            throw new MalformedEntryException(line, "unknown Komet status " + quote(text)
                    + " — expected one of \"Not yet in Komet\", \"_Related concept in Komet_\","
                    + " \"*Implemented in Komet*\"");
        }
    }

    /**
     * One dictionary entry.
     *
     * @param keyword       the CQL keyword itself, bold markers stripped — e.g. {@code included in}
     * @param anchor        the entry's stable anchor minus its {@code term-} prefix
     * @param family        the top-level family, or {@code null} when the entry carries no path
     * @param category      the subcategory, or {@code null} when the entry carries no path
     * @param status        the Komet status
     * @param definition    the single-paragraph definition
     * @param kometConcepts the Komet concepts the entry names, or {@code null} when it names none
     * @param line          the 1-based line of the entry's anchor, for diagnostics
     */
    record Entry(String keyword, String anchor, String family, String category, Status status,
                 String definition, String kometConcepts, int line) {

        /** @return whether this entry may be minted — see {@link Status} */
        boolean generatable() {
            return status == Status.NOT_YET_IN_KOMET;
        }
    }

    /** Thrown for any entry that does not match the documented grammar. */
    static final class MalformedEntryException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        MalformedEntryException(int line, String message) {
            super("keyword-dictionary line " + line + ": " + message);
        }
    }

    private static final Pattern ANCHOR = Pattern.compile("\\[\\[term-([A-Za-z0-9-]+)]]");
    private static final Pattern HEADING = Pattern.compile("=== (\\S.*)");
    /** Category, then an optional {@code (Family > Subcategory)} path, then the status. */
    private static final Pattern METADATA =
            Pattern.compile("`([^`]+)` -- (?:\\(([^>)]+) > ([^>)]+)\\) )?(\\S.*)");
    /** Alphanumeric words separated by single spaces — the shape an anchor can pin exactly. */
    private static final Pattern PINNABLE_KEYWORD =
            Pattern.compile("[A-Za-z0-9]+( [A-Za-z0-9]+)*");
    private static final Pattern KOMET_CONCEPTS =
            Pattern.compile("(?:Related Komet concept\\(s\\)|Komet concept): (\\S.*)");
    private static final String ENTRY_SEPARATOR = "* * *";

    private CqlKeywordDictionary() {
    }

    /**
     * Reads every entry in the dictionary.
     *
     * @param dictionary the {@code keyword-dictionary.adoc} file
     * @return the entries in document order
     * @throws MalformedEntryException if any entry does not match the grammar
     * @throws UncheckedIOException    if the file cannot be read
     */
    static List<Entry> parse(Path dictionary) {
        try {
            return parse(Files.readAllLines(dictionary));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read keyword dictionary " + dictionary, e);
        }
    }

    /**
     * Reads every entry from already-loaded lines.
     *
     * @param lines the dictionary's lines
     * @return the entries in document order
     * @throws MalformedEntryException if any entry does not match the grammar
     */
    static List<Entry> parse(List<String> lines) {
        List<Entry> entries = new ArrayList<>();
        Set<String> seenAnchors = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher anchor = ANCHOR.matcher(lines.get(i));
            if (!anchor.matches()) {
                continue;
            }
            Entry entry = parseEntry(lines, i, anchor.group(1));
            if (!seenAnchors.add(entry.anchor())) {
                throw new MalformedEntryException(entry.line(),
                        "duplicate anchor term-" + entry.anchor());
            }
            entries.add(entry);
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException(
                    "No [[term-...]] entries found — is this the keyword dictionary?");
        }
        long headings = lines.stream().filter(l -> HEADING.matcher(l).matches()).count();
        if (headings != entries.size()) {
            throw new IllegalArgumentException("keyword-dictionary: read " + entries.size()
                    + " entries but the file carries " + headings + " \"=== \" keyword headings"
                    + " — every heading belongs to an entry, so the gap of "
                    + Math.abs(headings - entries.size()) + " means an anchor line was not read"
                    + " as [[term-...]] (a stray character, or trailing whitespace, will do it)."
                    + " Entries are never skipped silently");
        }
        return List.copyOf(entries);
    }

    private static Entry parseEntry(List<String> lines, int anchorIndex, String anchor) {
        Cursor c = new Cursor(lines, anchorIndex + 1);
        int anchorLine = anchorIndex + 1;

        c.expect("[discrete]");
        String keyword = c.match(HEADING, "a === keyword heading").group(1).replace("*", "").strip();
        requireAnchorAgrees(anchor, keyword, anchorLine);
        c.expectBlank();

        Matcher metadata = c.match(METADATA, "a `Category` -- ... metadata line");
        String category = metadata.group(1).strip();
        String family = metadata.group(2) == null ? null : metadata.group(2).strip();
        String subcategory = metadata.group(3) == null ? null : metadata.group(3).strip();
        Status status = Status.ofMarkup(metadata.group(4).strip(), c.line());
        if (subcategory != null && !subcategory.equals(category)) {
            throw new MalformedEntryException(c.line(), "category " + quote(category)
                    + " and subcategory " + quote(subcategory) + " disagree — the dictionary states"
                    + " each entry's category twice and every entry states it identically, so a"
                    + " divergence is unreadable rather than resolvable");
        }
        if (status == Status.NOT_YET_IN_KOMET && family == null) {
            throw new MalformedEntryException(c.line(), "entry " + quote(keyword)
                    + " is generatable but carries no (Family > Subcategory) path — there is no"
                    + " parent to hang it from");
        }
        c.expectBlank();

        String definition = c.match(Pattern.compile("(\\S.*)"), "a definition paragraph").group(1);
        c.expectBlank();

        c.expect("[source,cql]");
        c.expect("----");
        c.skipUntil("----", "the closing ---- of the [source,cql] block");

        String kometConcepts = readKometConcepts(c, keyword, status);
        return new Entry(keyword, anchor, family, category, status, definition, kometConcepts,
                anchorLine);
    }

    /**
     * Reads the trailing {@code Komet concept:} line, which appears on exactly the entries that
     * are not generatable. Its presence and the status must agree: a status claiming a Komet
     * counterpart with no concept named, or a concept named on an entry claiming none exists,
     * is a contradiction the generator cannot resolve.
     *
     * <p>This is the scan a missing line would carry into the following entry, so it relies on
     * {@link Cursor} refusing to cross an anchor. Only the last entry ends at end of file; every
     * other one ends at its separator.
     */
    private static String readKometConcepts(Cursor c, String keyword, Status status) {
        String found = null;
        while (c.hasNext() && !c.peek().equals(ENTRY_SEPARATOR)) {
            Matcher m = KOMET_CONCEPTS.matcher(c.next());
            if (m.matches()) {
                found = m.group(1).strip();
            }
        }
        boolean expected = status != Status.NOT_YET_IN_KOMET;
        if (expected && found == null) {
            throw new MalformedEntryException(c.line(), "entry " + quote(keyword) + " is "
                    + quote(status.markup) + " but names no Komet concept");
        }
        if (!expected && found != null) {
            throw new MalformedEntryException(c.line(), "entry " + quote(keyword) + " is "
                    + quote(status.markup) + " but names Komet concept " + quote(found));
        }
        return found;
    }

    private static void requireAnchorAgrees(String anchor, String keyword, int line) {
        if (!PINNABLE_KEYWORD.matcher(keyword).matches()) {
            throw new MalformedEntryException(line, "keyword " + quote(keyword) + " cannot be"
                    + " pinned by anchor term-" + anchor + " — an anchor renders every run of"
                    + " non-alphanumerics as a single \"-\", so a doubled space, a hyphen or any"
                    + " other punctuation leaves the anchor agreeing with names that differ, while"
                    + " identity is minted from the name. A keyword must be alphanumeric words"
                    + " separated by single spaces; all 122 entries are");
        }
        String derived = keyword.replace(' ', '-');
        if (!derived.equals(anchor)) {
            throw new MalformedEntryException(line, "anchor term-" + anchor + " does not agree with"
                    + " keyword " + quote(keyword) + " (which derives term-" + derived + ") — the"
                    + " anchor is the entry's stable identifier and identity is derived from the"
                    + " keyword, so the two disagreeing means one of them is wrong");
        }
    }

    private static String quote(String s) {
        return "\"" + s + "\"";
    }

    /**
     * A forward-only line reader that fails loudly, with a line number, on any surprise.
     *
     * <p>It is also what keeps an entry inside its own bounds. Every scan runs through
     * {@link #next()}, which refuses to consume a {@code [[term-...]]} anchor, so no read within
     * an entry — a literal, a pattern match, or a skip looking for a delimiter — can run past the
     * end of that entry and into the next. A cursor starts just after its own anchor, so in a
     * well-formed file it never meets another one. Without that bound a single missing line, such
     * as an example block's closing {@code ----}, would let one entry swallow the next and adopt
     * its Komet counterpart: a wrong row in the identity report with nothing erroring.
     */
    private static final class Cursor {

        private final List<String> lines;
        private int index;

        Cursor(List<String> lines, int index) {
            this.lines = lines;
            this.index = index;
        }

        boolean hasNext() {
            return index < lines.size();
        }

        String peek() {
            return hasNext() ? lines.get(index) : "";
        }

        String next() {
            if (!hasNext()) {
                throw new MalformedEntryException(line(), "entry ends mid-way through the file");
            }
            String line = lines.get(index);
            if (ANCHOR.matcher(line).matches()) {
                throw new MalformedEntryException(index + 1, "reached the next entry's anchor "
                        + quote(line) + " before this entry ended — a line this entry needs is"
                        + " missing, and reading on would take the next entry's content as this"
                        + " one's");
            }
            return lines.get(index++);
        }

        /** @return the 1-based line number most recently consumed */
        int line() {
            return index;
        }

        void expect(String literal) {
            String actual = next();
            if (!actual.equals(literal)) {
                throw new MalformedEntryException(line(),
                        "expected " + quote(literal) + " but found " + quote(actual));
            }
        }

        void expectBlank() {
            String actual = next();
            if (!actual.isBlank()) {
                throw new MalformedEntryException(line(),
                        "expected a blank line but found " + quote(actual));
            }
        }

        Matcher match(Pattern pattern, String description) {
            String actual = next();
            Matcher m = pattern.matcher(actual);
            if (!m.matches()) {
                throw new MalformedEntryException(line(),
                        "expected " + description + " but found " + quote(actual));
            }
            return m;
        }

        void skipUntil(String literal, String description) {
            while (hasNext()) {
                if (next().equals(literal)) {
                    return;
                }
            }
            throw new MalformedEntryException(line(), "reached end of file looking for "
                    + description);
        }
    }
}
