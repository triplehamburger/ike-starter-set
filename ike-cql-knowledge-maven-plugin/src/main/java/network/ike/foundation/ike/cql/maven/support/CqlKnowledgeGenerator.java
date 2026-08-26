package network.ike.foundation.ike.cql.maven.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import network.ike.foundation.ike.cql.maven.support.KeywordDictionary.Entry;
import network.ike.foundation.ike.cql.maven.support.KeywordDictionary.KometStatus;

/**
 * Turns dictionary entries into ledger source: one {@code compose(KnowledgeSet)} class carrying a
 * concept per generatable keyword, hung under the family/category taxonomy the dictionary itself
 * declares.
 *
 * <h2>Why the identities are stable</h2>
 *
 * <p>A concept's identity is {@code uuid5(namespace, fullyQualifiedName)} — RFC 4122 version 5,
 * SHA-1 over the namespace's raw bytes followed by the name's UTF-8 bytes. That is exactly what
 * {@code KnowledgeSet.concept(name)} derives internally (tinkar's {@code UuidT5Generator.get}), and
 * {@link #identityFor(UUID, String)} is verified against a published vector from that derivation in
 * {@code CqlKnowledgeGeneratorTest}. Nothing else feeds a UUID here: not source order, not the
 * definition, not the example, not the run. Two consequences worth stating plainly:
 *
 * <ul>
 *   <li>The same dictionary always regenerates byte-identical output.</li>
 *   <li>Renaming a keyword — or changing the semantic tag, or the namespace — mints a <em>new</em>
 *       concept rather than renaming the old one. The generated source therefore carries every
 *       derived UUID as a literal, so that a rewording shows up in review as a changed identity
 *       instead of quietly becoming a second concept for the same thing.</li>
 * </ul>
 *
 * <h2>What is not generated</h2>
 *
 * <p>Only {@link KometStatus#NOT_YET} keywords are minted. {@link KometStatus#IMPLEMENTED} ones
 * already have a Komet identity and minting again would create a duplicate;
 * {@link KometStatus#RELATED} ones need an editorial call about the relationship. Both are reported
 * as deferrals rather than dropped.
 */
public final class CqlKnowledgeGenerator {

    /** The permanent type-5 namespace of the IkeFoundation knowledge set, from {@code Ike.SET}. */
    public static final UUID IKE_FOUNDATION_NAMESPACE =
            UUID.fromString("d890e06f-ec35-429a-b541-d0ead19695e2");

    private CqlKnowledgeGenerator() {
    }

    /**
     * How the generator is pointed at a knowledge set.
     *
     * @param namespace       the knowledge set's type-5 namespace; every identity derives from it
     * @param semanticTag     the parenthesised tag every generated fully qualified name ends with
     * @param rootName        the display name of the concept the whole taxonomy hangs from
     * @param rootParent      a Java expression naming the concept the root is a kind of, e.g.
     *                        {@code IkeTerm.MODEL_CONCEPT}
     * @param packageName     the package the generated class is written into
     * @param className       the generated class's simple name
     * @param stampExpression a Java expression yielding the {@code ActiveStamp} every version
     *                        carries, e.g. {@code Ike.INCEPTION}
     */
    public record Target(UUID namespace,
                         String semanticTag,
                         String rootName,
                         String rootParent,
                         String packageName,
                         String className,
                         String stampExpression) {
    }

    /**
     * What one run produced.
     *
     * @param source    the generated Java source
     * @param concepts  the fully qualified name of every concept the source authors, in the order
     *                  they were generated in: root, then each family followed immediately by its
     *                  categories and their keywords. That deliberately differs from the order the
     *                  generated source authors them in, where {@code composeTaxonomy} emits the
     *                  root and every family and category first and the per-family methods emit
     *                  the keywords afterwards
     * @param deferrals the entries deliberately not minted, one human-readable line each
     */
    public record Result(String source, List<String> concepts, List<String> deferrals) {
    }

    /**
     * Derives a concept identity the way the {@code KnowledgeSet} DSL does.
     *
     * @param namespace          the knowledge set's namespace
     * @param fullyQualifiedName the concept's fully qualified name
     * @return the RFC 4122 version 5 UUID for that name in that namespace
     */
    public static UUID identityFor(UUID namespace, String fullyQualifiedName) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JRE ships SHA-1", e);
        }
        digest.update(rawBytes(namespace));
        digest.update(fullyQualifiedName.getBytes(StandardCharsets.UTF_8));
        byte[] hash = digest.digest();
        hash[6] = (byte) ((hash[6] & 0x0F) | 0x50);
        hash[8] = (byte) ((hash[8] & 0x3F) | 0x80);
        long high = 0;
        long low = 0;
        for (int i = 0; i < 8; i++) {
            high = (high << 8) | (hash[i] & 0xFF);
        }
        for (int i = 8; i < 16; i++) {
            low = (low << 8) | (hash[i] & 0xFF);
        }
        return new UUID(high, low);
    }

    private static byte[] rawBytes(UUID uuid) {
        byte[] bytes = new byte[16];
        long high = uuid.getMostSignificantBits();
        long low = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (high >>> (8 * (7 - i)));
            bytes[8 + i] = (byte) (low >>> (8 * (7 - i)));
        }
        return bytes;
    }

    /**
     * Generates the ledger source for a parsed dictionary.
     *
     * @param entries the dictionary entries
     * @param target  where the concepts are being authored
     * @return the generated source, the concepts it authors, and what was deferred
     * @throws IllegalStateException if the dictionary yields no keyword to mint, if two concepts
     *         would claim the same fully qualified name, or if a family would produce a method name
     *         the generated class already uses
     */
    public static Result generate(List<Entry> entries, Target target) {
        List<String> deferrals = new ArrayList<>();
        // Sorted, so emission order depends on the content and never on where an entry happens to
        // sit in the chapter. TreeMap/TreeSet ordering is the whole of the ordering policy.
        Map<String, Map<String, List<Entry>>> byFamily = new TreeMap<>();
        for (Entry entry : entries) {
            if (entry.status() != KometStatus.NOT_YET) {
                deferrals.add(entry.name() + " — " + entry.status().text());
                continue;
            }
            byFamily.computeIfAbsent(entry.family().orElseThrow(), f -> new TreeMap<>())
                    .computeIfAbsent(entry.category(), c -> new ArrayList<>())
                    .add(entry);
        }
        byFamily.values().forEach(byCategory ->
                byCategory.values().forEach(list -> list.sort(Comparator.comparing(Entry::name))));
        if (byFamily.isEmpty()) {
            throw new IllegalStateException("No keyword to mint: of " + entries.size()
                    + " entries read, " + deferrals.size() + " were deferred and none was marked '"
                    + KometStatus.NOT_YET.text() + "'. Writing a taxonomy root with nothing under"
                    + " it would be wrong content on a green build");
        }

        Set<String> concepts = new LinkedHashSet<>();
        StringBuilder taxonomy = new StringBuilder();
        Map<String, StringBuilder> familyBodies = new LinkedHashMap<>();
        Set<String> methodNames = new LinkedHashSet<>(List.of("compose", "composeTaxonomy"));

        String rootFqn = fqn(target.rootName(), target);
        emit(taxonomy, target, concepts, rootFqn, target.rootName(),
                "The CQL keyword vocabulary, as the CQL keyword dictionary chapter defines it.",
                target.rootParent());

        for (Map.Entry<String, Map<String, List<Entry>>> family : byFamily.entrySet()) {
            String familyFqn = fqn(family.getKey(), target);
            emit(taxonomy, target, concepts, familyFqn, family.getKey(),
                    "The " + family.getKey() + " family of CQL keywords, as the CQL keyword"
                            + " dictionary groups them.",
                    conceptRef(rootFqn));

            StringBuilder body = new StringBuilder();
            for (Map.Entry<String, List<Entry>> category : family.getValue().entrySet()) {
                String categoryFqn = fqn(category.getKey(), target);
                emit(taxonomy, target, concepts, categoryFqn, category.getKey(),
                        "The " + category.getKey() + " category of CQL keywords, within the "
                                + family.getKey() + " family.",
                        conceptRef(familyFqn));

                for (Entry entry : category.getValue()) {
                    emit(body, target, concepts, fqn(entry.name(), target), entry.name(),
                            definitionOf(entry), conceptRef(categoryFqn));
                }
            }
            String method = methodName(family.getKey());
            if (!methodNames.add(method)) {
                throw new IllegalStateException(
                        "Two methods of the generated class would both be named '" + method + "'");
            }
            familyBodies.put(method, body);
        }
        return new Result(render(target, taxonomy, familyBodies), List.copyOf(concepts),
                List.copyOf(deferrals));
    }

    // ponytail: the CQL example rides in the definition text because the ledger has no example
    // pattern to attach it to. Definition text does not feed any identity, so moving it to a
    // dedicated semantic later changes content without disturbing a single UUID.
    private static String definitionOf(Entry entry) {
        return entry.definition() + "\n\nExample:\n" + entry.example();
    }

    private static void emit(StringBuilder out, Target target, Set<String> concepts,
                             String fullyQualifiedName, String synonym, String definition,
                             String parent) {
        if (!concepts.add(fullyQualifiedName)) {
            throw new IllegalStateException("Two concepts would both be named '"
                    + fullyQualifiedName + "'; one of them would silently take the other's"
                    + " identity");
        }
        UUID identity = identityFor(target.namespace(), fullyQualifiedName);
        out.append("        set.concept(").append(literal(fullyQualifiedName))
                .append(", PublicIds.of(UUID.fromString(\"").append(identity)
                .append("\"))).at(inception)\n")
                .append("                .synonym(").append(literal(synonym)).append(")\n")
                .append("                .definition(").append(literal(definition)).append(")\n")
                .append("                .isA(").append(parent).append(");\n\n");
    }

    private static String conceptRef(String fullyQualifiedName) {
        return "set.conceptRef(" + literal(fullyQualifiedName) + ")";
    }

    private static String fqn(String name, Target target) {
        return name + " (" + target.semanticTag() + ")";
    }

    private static String render(Target target, StringBuilder taxonomy,
                                 Map<String, StringBuilder> familyBodies) {
        StringBuilder out = new StringBuilder();
        out.append("package ").append(target.packageName()).append(";\n\n")
                .append("import dev.ikm.tinkar.common.id.PublicIds;\n")
                .append("import dev.ikm.tinkar.entity.builder.ActiveStamp;\n")
                .append("import dev.ikm.tinkar.entity.builder.KnowledgeSet;\n")
                .append("import java.util.UUID;\n")
                .append("import network.ike.foundation.ike.terms.Ike;\n")
                .append("import network.ike.foundation.ike.terms.IkeTerm;\n\n")
                .append("/**\n")
                .append(" * The CQL keyword vocabulary, generated from"
                        + " {@code cql/keyword-dictionary.adoc} by\n")
                .append(" * {@code ike-cql-knowledge:generate}. Do not edit: regenerate.\n")
                .append(" *\n")
                .append(" * <p>Every identity below is"
                        + " {@code uuid5(").append(target.namespace()).append(", name)} — derived"
                        + " from the\n")
                .append(" * fully qualified name alone, and written out so that a rewording is"
                        + " visible in review as a\n")
                .append(" * changed UUID rather than a silently duplicated concept.\n")
                .append(" *\n")
                .append(" * <p>Parents are cited as {@code set.conceptRef(name)}, which the"
                        + " knowledge set re-derives from\n")
                .append(" * <em>its own</em> namespace. Compose this class only into a set whose"
                        + " namespace is\n")
                .append(" * {@code ").append(target.namespace()).append("}: composed into any"
                        + " other, the literal identities above\n")
                .append(" * and the parents cited below them would belong to different"
                        + " concepts.\n")
                .append(" */\n")
                .append("public final class ").append(target.className()).append(" {\n\n")
                .append("    private ").append(target.className()).append("() {\n    }\n\n")
                .append("    /**\n")
                .append("     * Composes the CQL keyword vocabulary onto the caller's knowledge"
                        + " set.\n")
                .append("     *\n")
                .append("     * @param set the knowledge set to author into\n")
                .append("     */\n")
                .append("    public static void compose(KnowledgeSet set) {\n")
                .append("        composeTaxonomy(set);\n");
        familyBodies.keySet().forEach(method ->
                out.append("        ").append(method).append("(set);\n"));
        out.append("    }\n\n");

        method(out, target, "composeTaxonomy", taxonomy);
        familyBodies.forEach((name, body) -> method(out, target, name, body));

        out.append("}\n");
        return out.toString();
    }

    private static void method(StringBuilder out, Target target, String name, StringBuilder body) {
        out.append("    private static void ").append(name).append("(KnowledgeSet set) {\n")
                .append("        ActiveStamp inception = ").append(target.stampExpression())
                .append(";\n\n")
                .append(body.toString().stripTrailing()).append("\n")
                .append("    }\n\n");
    }

    /**
     * Turns a family name into a Java method name, e.g. {@code Data & Timing Operators} into
     * {@code composeDataTimingOperators}. Anything that is not a letter or digit ends the current
     * word; the result is checked for collisions by the caller.
     */
    private static String methodName(String family) {
        StringBuilder out = new StringBuilder("compose");
        boolean startOfWord = true;
        for (char c : family.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                out.append(startOfWord ? Character.toUpperCase(c) : c);
                startOfWord = false;
            } else {
                startOfWord = true;
            }
        }
        return out.toString();
    }

    private static String literal(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
