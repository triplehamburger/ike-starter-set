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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Emits a ledger-form {@code compose(KnowledgeSet)} class from parsed dictionary entries, in the
 * shape {@code FoundationSet}'s sections have: one uniform fluent chain per concept, carrying the
 * fully qualified name, a regular-name synonym, a definition, and a stated is-a axiom.
 *
 * <p><b>Determinism.</b> Every identity in a knowledge set derives from the set's namespace UUID
 * and the concept's fully qualified name: {@code concept UUID = UUIDv5(namespace, fqn)}, RFC 4122
 * type 5 over UTF-8, which is what {@code KnowledgeSet.concept(String)} does under the covers.
 * Re-wording a concept therefore mints a different concept. Two rules keep that from happening
 * here:
 *
 * <ol>
 *   <li><b>Names come from identifiers, never from prose.</b> A keyword's fully qualified name is
 *       built from the keyword text alone — the text the parser has already cross-checked against
 *       the entry's stable {@code [[term-...]]} anchor. Category and family names come from the
 *       dictionary's fixed taxonomy vocabulary. Definitions and CQL examples are the parts of an
 *       entry an editor is expected to reword, and they feed descriptions only, never identity.
 *   <li><b>Output order is imposed, not inherited.</b> Concepts are emitted sorted, so reordering
 *       the dictionary — or adding an entry in the middle of it — leaves every other line of the
 *       generated file untouched.
 * </ol>
 *
 * <p>Together those make the generated file a pure function of the dictionary's identifying
 * content: re-running over an unchanged dictionary reproduces it byte for byte, and the identity
 * report lists the minted UUIDs so a reviewer can diff identities across runs without compiling.
 *
 * <p><b>Idempotency.</b> Only {@code Not yet in Komet} entries are minted. {@code Implemented in
 * Komet} already exists in the ledger and re-minting it would duplicate it; {@code Related concept
 * in Komet} needs a human to decide what it links to. Both are reported rather than dropped.
 */
final class CqlKeywordSetGenerator {

    /**
     * The generated artifacts.
     *
     * @param javaSource      the {@code compose(KnowledgeSet)} class
     * @param identityReport  minted FQN-to-UUID rows plus the entries deliberately not minted
     * @param mintedConcepts  how many concepts {@link #javaSource} authors
     * @param deferredEntries how many dictionary entries were not minted
     */
    record Generated(String javaSource, String identityReport, int mintedConcepts,
                     int deferredEntries) {
    }

    /**
     * The generator's inputs.
     *
     * @param packageName     package of the generated class
     * @param className       simple name of the generated class
     * @param namespace       the target knowledge set's namespace UUID — the root of every identity
     * @param semanticTag     the set's semantic tag, the parenthesised suffix of every name
     * @param rootConceptName name of the root concept the keyword families hang from, tag included
     * @param rootParentExpression Java expression for the root's own parent — a concept outside
     *                             this set, so a constant reference rather than a name
     * @param stampExpression Java expression for the authoring stamp, emitted verbatim
     */
    record Options(String packageName, String className, UUID namespace, String semanticTag,
                   String rootConceptName, String rootParentExpression,
                   String stampExpression) {
    }

    /**
     * One concept to author: its name, the descriptions it carries, and the Java expression for
     * its is-a parent — {@code set.conceptRef(...)} for a parent in this set, or a constant
     * reference for one outside it.
     */
    private record Composed(String fqn, String synonym, String definition,
                            String parentAxiomTarget) {
    }

    private CqlKeywordSetGenerator() {
    }

    /**
     * Generates the ledger source and the identity report.
     *
     * @param entries the parsed dictionary entries
     * @param options the generator's inputs
     * @return the generated artifacts
     */
    static Generated generate(List<Entry> entries, Options options) {
        List<Entry> minted = sortedByAnchor(entries, true);
        List<Entry> deferred = sortedByAnchor(entries, false);

        // Category -> family, sorted: the taxonomy tiers authored above the keywords themselves.
        // Sorted rather than first-seen so the file is stable against dictionary reordering.
        // A category name is a concept name, so it identifies exactly one concept: the same name
        // under two families would collapse them into one UUID hung under whichever family was
        // written last. That invariant is the dictionary's, so it is enforced, not assumed.
        Map<String, String> familyOfCategory = new TreeMap<>();
        for (Entry e : minted) {
            String previous = familyOfCategory.put(e.category(), e.family());
            if (previous != null && !previous.equals(e.family())) {
                throw new CqlKeywordDictionary.MalformedEntryException(e.line(), "category \""
                        + e.category() + "\" appears under two families, \"" + previous
                        + "\" and \"" + e.family() + "\" — category names identify one concept"
                        + " each, so one of the two families is wrong");
            }
        }

        // Families, then categories, then keywords, so a parent is always authored before its
        // children. The dictionary's taxonomy is a fixed three-tier tree, so tier order is the
        // whole of the ordering problem — no general topological sort is needed.
        // The root is authored here rather than merely referenced, so the generated set closes
        // over every name it mentions but one: its parent, a term from outside this set.
        Composed root = new Composed(options.rootConceptName(),
                stripTag(options.rootConceptName(), options),
                "A keyword of Clinical Quality Language, as catalogued by the CQL keyword"
                        + " dictionary.",
                options.rootParentExpression());
        List<Composed> families = new TreeSet<>(familyOfCategory.values()).stream()
                .map(family -> new Composed(fqn(family, options), family,
                        "A family of CQL keywords, as grouped by the CQL keyword dictionary: "
                                + family + ".",
                        conceptRef(options.rootConceptName())))
                .toList();
        List<Composed> categories = familyOfCategory.entrySet().stream()
                .map(e -> new Composed(fqn(e.getKey(), options), e.getKey(),
                        "A category of CQL keywords within the " + e.getValue()
                                + " family, as grouped by the CQL keyword dictionary: "
                                + e.getKey() + ".",
                        conceptRef(fqn(e.getValue(), options))))
                .toList();
        List<Composed> keywords = minted.stream()
                .map(e -> new Composed(fqn(e.keyword(), options), e.keyword(), e.definition(),
                        conceptRef(fqn(e.category(), options))))
                .toList();

        List<Composed> all = Stream.of(List.of(root), families, categories, keywords)
                .flatMap(List::stream).toList();
        // Identity is UUIDv5(namespace, fqn), so a name repeated across any two tiers is one
        // concept, not two: it would be authored twice, counted twice, and — where the tiers are
        // adjacent — stated is-a itself. All four tiers meet here, so this is the one place the
        // invariant the naming scheme actually requires can be checked once.
        Set<String> names = new HashSet<>();
        for (Composed c : all) {
            if (!names.add(c.fqn())) {
                throw new IllegalArgumentException("two concepts share the fully qualified name \""
                        + c.fqn() + "\" — identity derives from that name, so they would collapse"
                        + " into one concept; the root, family, category and keyword names must"
                        + " all differ");
            }
        }
        String java = renderJava(options, root, families, categories, keywords);
        String report = renderReport(options, all, deferred);
        return new Generated(java, report, all.size(), deferred.size());
    }

    private static List<Entry> sortedByAnchor(List<Entry> entries, boolean generatable) {
        return entries.stream()
                .filter(e -> e.generatable() == generatable)
                .sorted(Comparator.comparing(Entry::anchor))
                .toList();
    }

    private static String fqn(String name, Options options) {
        return name + " (" + options.semanticTag() + ")";
    }

    private static String stripTag(String fqn, Options options) {
        String tag = " (" + options.semanticTag() + ")";
        return fqn.endsWith(tag) ? fqn.substring(0, fqn.length() - tag.length()) : fqn;
    }

    private static String conceptRef(String fqn) {
        return "set.conceptRef(" + literal(fqn) + ")";
    }

    /**
     * Type 5 (SHA-1) UUID over a namespace and a name, per RFC 4122 — byte-for-byte what tinkar's
     * {@code UuidT5Generator.get(UUID, String)} produces, and therefore what
     * {@code KnowledgeSet.concept(fqn)} will resolve the generated names to. Reproduced here so
     * the generator can report identities without depending on a store runtime; the pinning test
     * checks it against a known set-namespace/FQN/UUID triple from the shipped ledger.
     */
    static UUID nameUuid(UUID namespace, String name) {
        MessageDigest sha1;
        try {
            sha1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required of every Java platform", e);
        }
        sha1.update(toBytes(namespace));
        byte[] hash = sha1.digest(name.getBytes(StandardCharsets.UTF_8));
        hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);   // version 5
        hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);   // IETF variant
        long most = 0;
        long least = 0;
        for (int i = 0; i < 8; i++) {
            most = (most << 8) | (hash[i] & 0xff);
            least = (least << 8) | (hash[i + 8] & 0xff);
        }
        return new UUID(most, least);
    }

    private static byte[] toBytes(UUID uuid) {
        byte[] bytes = new byte[16];
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (most >>> (56 - 8 * i));
            bytes[i + 8] = (byte) (least >>> (56 - 8 * i));
        }
        return bytes;
    }

    private static String renderJava(Options options, Composed root, List<Composed> families,
                                     List<Composed> categories, List<Composed> keywords) {
        StringBuilder out = new StringBuilder(64 * 1024);
        out.append("package ").append(options.packageName()).append(";\n\n")
                .append("import dev.ikm.tinkar.entity.builder.ActiveStamp;\n")
                .append("import dev.ikm.tinkar.entity.builder.KnowledgeSet;\n\n")
                .append("/**\n")
                .append(" * The CQL keyword taxonomy, generated from the CQL keyword dictionary by\n")
                .append(" * ike:knowledge-generate. Do not edit: regenerate.\n")
                .append(" *\n")
                .append(" * <p>Authors ").append(families.size()).append(" keyword families, ")
                .append(categories.size()).append(" categories and ").append(keywords.size())
                .append(" keywords. Dictionary entries already\n")
                .append(" * carried by Komet are not authored here — see the identity report\n")
                .append(" * beside this file for which, and why.\n")
                .append(" */\n")
                .append("public final class ").append(options.className()).append(" {\n\n")
                .append("    private ").append(options.className()).append("() {\n    }\n\n")
                .append("    /**\n")
                .append("     * Composes the CQL keyword taxonomy onto the caller's knowledge set.\n")
                .append("     *\n")
                .append("     * @param set the knowledge set to author into\n")
                .append("     */\n")
                .append("    public static void compose(KnowledgeSet set) {\n")
                .append("        ActiveStamp inception = ").append(options.stampExpression())
                .append(";\n")
                .append("        composeRoot(set, inception);\n")
                .append("        composeFamilies(set, inception);\n")
                .append("        composeCategories(set, inception);\n")
                .append("        composeKeywords(set, inception);\n")
                .append("    }\n");

        // ponytail: one method per tier, which the parent-before-child ordering needs anyway.
        // Split the keyword tier further only if it ever approaches the 64KB method limit — 115
        // chains is well inside it.
        appendTier(out, "composeRoot", List.of(root));
        appendTier(out, "composeFamilies", families);
        appendTier(out, "composeCategories", categories);
        appendTier(out, "composeKeywords", keywords);
        return out.append("}\n").toString();
    }

    private static void appendTier(StringBuilder out, String methodName,
                                   List<Composed> concepts) {
        out.append("\n    private static void ").append(methodName)
                .append("(KnowledgeSet set, ActiveStamp inception) {\n");
        for (Composed c : concepts) {
            out.append("        set.concept(").append(literal(c.fqn())).append(").at(inception)\n")
                    .append("                .synonym(").append(literal(c.synonym())).append(")\n")
                    .append("                .definition(").append(literal(c.definition()))
                    .append(")\n")
                    .append("                .statedAxioms(leb -> leb.NecessarySet(leb.And(\n")
                    .append("                        leb.ConceptAxiom(")
                    .append(c.parentAxiomTarget()).append("))));\n");
        }
        out.append("    }\n");
    }

    private static String renderReport(Options options, List<Composed> minted,
                                       List<Entry> deferred) {
        StringBuilder out = new StringBuilder(16 * 1024);
        out.append("# CQL keyword set — generated identities\n")
                .append("#\n")
                .append("# namespace:    ").append(options.namespace()).append('\n')
                .append("# semantic tag: ").append(options.semanticTag()).append('\n')
                .append("# root concept: ").append(options.rootConceptName())
                .append(", under ").append(options.rootParentExpression()).append('\n')
                .append("#\n")
                .append("# Every UUID below is UUIDv5(namespace, fully qualified name) — the same\n")
                .append("# derivation KnowledgeSet.concept(String) applies. Diff this file across\n")
                .append("# runs to see whether any identity moved.\n\n")
                .append("## minted\n");
        for (Composed c : minted) {
            out.append(nameUuid(options.namespace(), c.fqn())).append("  ").append(c.fqn())
                    .append('\n');
        }
        out.append("\n## not minted — the dictionary's Komet status withholds these\n");
        for (Entry e : deferred) {
            out.append(e.status()).append("  ").append(e.keyword())
                    .append("  -> ").append(e.kometConcepts()).append('\n');
        }
        return out.toString();
    }

    private static String literal(String text) {
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
