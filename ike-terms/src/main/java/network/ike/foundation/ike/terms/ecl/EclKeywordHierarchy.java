package network.ike.foundation.ike.terms.ecl;

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.KnowledgeSet;
import network.ike.foundation.ike.terms.IkeTerm;
import java.util.List;
import java.util.UUID;

/**
 * The ECL keyword hierarchy: the ECL keyword root, filed under Legacy (IkeFoundation), and the
 * 10 categories every keyword section is filed under, one per section of the ECL 2.3
 * specification's ECL Core Brief Syntax chapter that introduces keywords, named after it.
 *
 * <p>Each category is a concept of the same shape as a keyword section, with every identity
 * derived from its fully qualified name through {@code set.uuidFor(key)}. The fully qualified
 * names end in "ECL keywords" so no bindings constant collides with a CQL category's.
 */
final class EclKeywordHierarchy {

    private EclKeywordHierarchy() {
    }

    /** One category concept: its fully qualified name, regular name, parent, and definition. */
    private record Category(String fullyQualifiedName, String regularName, String parent, String definition) {
    }

    /** The root first, then the sections. */
    private static final List<Category> CATEGORIES = List.of(
            new Category("ECL keyword (ECL)", "ECL keyword", "Legacy (IkeFoundation)",
                    "A word the SNOMED CT Expression Constraint Language (ECL 2.3) parser recognizes, as "
                    + "listed in the brief and long syntax ABNF. Grouped by the specification's ECL Core "
                    + "Brief Syntax sections. Every textual ECL keyword is case insensitive."),
            new Category("Simple expression constraints ECL keywords (ECL)", "Simple Expression Constraints", "ECL keyword (ECL)",
                    "Keywords of the Simple Expression Constraints section: the hierarchy operators "
                    + "selecting descendants, ancestors, children or parents of a focus concept, and the "
                    + "reference set operators."),
            new Category("Top and bottom ECL keywords (ECL)", "Top and Bottom", "ECL keyword (ECL)",
                    "Keywords of the Top and Bottom section: the operators selecting the concepts of a "
                    + "set that have no ancestor, or no descendant, within the set."),
            new Category("Refinements ECL keywords (ECL)", "Refinements", "ECL keyword (ECL)",
                    "Keywords of the Refinements section: the reverse flag selecting an attribute's "
                    + "values rather than its sources."),
            new Category("Cardinality ECL keywords (ECL)", "Cardinality", "ECL keyword (ECL)",
                    "Keywords of the Cardinality section: the range separator and the unbounded maximum."),
            new Category("Conjunction and disjunction ECL keywords (ECL)", "Conjunction and Disjunction", "ECL keyword (ECL)",
                    "Keywords of the Conjunction and Disjunction section: the intersection and union of "
                    + "expression constraints or refinements."),
            new Category("Exclusion and not equals ECL keywords (ECL)", "Exclusion and Not Equals", "ECL keyword (ECL)",
                    "Keywords of the Exclusion and Not Equals section: the difference of two expression "
                    + "constraints."),
            new Category("Description filters ECL keywords (ECL)", "Description Filters", "ECL keyword (ECL)",
                    "Keywords of the Description Filters section: filters on a concept's descriptions, "
                    + "the term search types, and the description type and acceptability tokens."),
            new Category("Concept filters ECL keywords (ECL)", "Concept Filters", "ECL keyword (ECL)",
                    "Keywords of the Concept Filters section: the definition status filters and tokens."),
            new Category("Member filters ECL keywords (ECL)", "Member Filters", "ECL keyword (ECL)",
                    "Keywords of the Member Filters section: filters on the members of a reference set."),
            new Category("History supplements ECL keywords (ECL)", "History Supplements", "ECL keyword (ECL)",
                    "Keywords of the History Supplements section: the history supplement and its "
                    + "profiles, which add inactive concepts through historical associations."));

    /**
     * Composes the hierarchy's declarations into the session.
     *
     * @param set the knowledge set (the session)
     */
    static void compose(KnowledgeSet set) {
        CATEGORIES.forEach(category -> compose(set, category));
    }

    private static void compose(KnowledgeSet set, Category category) {
        ActiveStamp inception = network.ike.foundation.ike.terms.Ike.INCEPTION;
        String fqn = category.fullyQualifiedName();

        UUID concept = set.uuidFor(fqn);
        UUID fullyQualifiedName = set.uuidFor(fqn + " fully qualified name description");
        UUID regularName = set.uuidFor(fqn + " regular name description");
        UUID definition = set.uuidFor(fqn + " definition description");

        set.concept(fqn, PublicIds.of(concept)).at(inception)
                .semantic(IkeTerm.DESCRIPTION_PATTERN, PublicIds.of(fullyQualifiedName), IkeTerm.ENGLISH_LANGUAGE, fqn, IkeTerm.DESCRIPTION_NOT_CASE_SENSITIVE, IkeTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE)  // FQN
                .semantic(IkeTerm.DESCRIPTION_PATTERN, PublicIds.of(regularName), IkeTerm.ENGLISH_LANGUAGE, category.regularName(), IkeTerm.DESCRIPTION_NOT_CASE_SENSITIVE, IkeTerm.REGULAR_NAME_DESCRIPTION_TYPE)  // regular name
                .semantic(IkeTerm.DESCRIPTION_PATTERN, PublicIds.of(definition), IkeTerm.ENGLISH_LANGUAGE, category.definition(), IkeTerm.DESCRIPTION_NOT_CASE_SENSITIVE, IkeTerm.DEFINITION_DESCRIPTION_TYPE)  // definition
                .semantic(IkeTerm.IDENTIFIER_PATTERN, PublicIds.of(set.uuidFor(fqn + " UUID identifier")), IkeTerm.UNIVERSALLY_UNIQUE_IDENTIFIER, concept.toString())  // UUID identifier
                .statedAxioms(PublicIds.of(set.uuidFor(fqn + " stated axioms")), leb -> leb.NecessarySet(leb.And(leb.ConceptAxiom(set.conceptRef(category.parent())))))
                .semanticOn(PublicIds.of(fullyQualifiedName), IkeTerm.US_DIALECT_PATTERN, PublicIds.of(set.uuidFor(fqn + " fully qualified name US dialect")), IkeTerm.PREFERRED)  // dialect pref
                .semanticOn(PublicIds.of(regularName), IkeTerm.US_DIALECT_PATTERN, PublicIds.of(set.uuidFor(fqn + " regular name US dialect")), IkeTerm.PREFERRED)  // dialect pref
                .semanticOn(PublicIds.of(definition), IkeTerm.US_DIALECT_PATTERN, PublicIds.of(set.uuidFor(fqn + " definition US dialect")), IkeTerm.PREFERRED)  // dialect pref
                ;
    }
}
