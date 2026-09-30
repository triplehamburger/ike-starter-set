package network.ike.foundation.ike.terms.cql;

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.KnowledgeSet;
import network.ike.foundation.ike.terms.IkeTerm;
import java.util.List;
import java.util.UUID;

/**
 * The CQL keyword hierarchy: the CQL keyword root, filed under Legacy (IkeFoundation), and the
 * categories every keyword section is filed under. The four families follow the CQL Author's
 * Guide (Declarations, Queries, Values, Operations); the operator categories follow the CQL
 * Reference (Appendix B).
 *
 * <p>Each category is a concept of the same shape as a keyword section — fully qualified name,
 * regular name, definition, UUID identifier, stated parent, US dialect preferences — with every
 * identity derived from its fully qualified name through {@code set.uuidFor(key)}. The
 * fully qualified names end in "keywords" so no category's bindings constant collides with a
 * keyword's.
 */
final class CqlKeywordHierarchy {

    private CqlKeywordHierarchy() {
    }

    /** One category concept: its fully qualified name, regular name, parent, and definition. */
    private record Category(String fullyQualifiedName, String regularName, String parent, String definition) {
    }

    /** The root first, then the four families, then each family's categories. */
    private static final List<Category> CATEGORIES = List.of(
            new Category("CQL keyword (CQL)", "CQL keyword", "Legacy (IkeFoundation)",
                    "A word the CQL parser recognizes to build language constructs, as listed in the CQL Developer's Guide. Grouped by the CQL Author's Guide, with operators subdivided by the operator categories of the CQL Reference (Appendix B)."),
            new Category("Declaration keywords (CQL)", "Declarations", "CQL keyword (CQL)",
                    "Keywords that declare the parts of a CQL library: its identity, data models, included libraries, terminology, parameters, context, and named statements."),
            new Category("Query keywords (CQL)", "Queries", "CQL keyword (CQL)",
                    "Keywords that build queries over lists of data: choosing sources, filtering, relating, shaping, and sorting results."),
            new Category("Value keywords (CQL)", "Values", "CQL keyword (CQL)",
                    "Keywords that name CQL's types and write values directly."),
            new Category("Operation keywords (CQL)", "Operations", "CQL keyword (CQL)",
                    "Keywords for CQL's operators, grouped by the operator categories of the CQL Reference."),
            new Category("Library declaration keywords (CQL)", "Library", "Declaration keywords (CQL)",
                    "Keywords of a library's header: its name and version, the data models it uses, and the libraries it includes."),
            new Category("Terminology keywords (CQL)", "Terminology", "Declaration keywords (CQL)",
                    "Keywords that declare the code systems, value sets, codes, and concepts a library uses."),
            new Category("Parameter keywords (CQL)", "Parameters", "Declaration keywords (CQL)",
                    "Keywords that declare a library parameter and its default value."),
            new Category("Statement keywords (CQL)", "Statements", "Declaration keywords (CQL)",
                    "Keywords that set the context statements are evaluated in, such as Patient, and define named expressions and functions with their access level."),
            new Category("Filtering keywords (CQL)", "Filtering", "Query keywords (CQL)",
                    "Keywords that restrict a query's results: by a condition, or by whether a related item exists."),
            new Category("Shaping keywords (CQL)", "Shaping", "Query keywords (CQL)",
                    "Keywords that shape what a query returns: projecting values, introducing definitions, and aggregating results."),
            new Category("Sorting keywords (CQL)", "Sorting", "Query keywords (CQL)",
                    "Keywords that order a query's results."),
            new Category("Simple value keywords (CQL)", "Simple Values", "Value keywords (CQL)",
                    "The literal values of CQL's three-valued logic."),
            new Category("Type name keywords (CQL)", "Type Names", "Value keywords (CQL)",
                    "Names of CQL's built-in clinical, structured, list, and interval types."),
            new Category("Calendar duration keywords (CQL)", "Calendar duration keywords", "Value keywords (CQL)",
                    "Units of calendar time, singular or plural, used in quantities and in date and time arithmetic."),
            new Category("Logical operator keywords (CQL)", "Logical Operators", "Operation keywords (CQL)",
                    "Operators that combine or negate Boolean values using three-valued logic."),
            new Category("Type operator keywords (CQL)", "Type Operators", "Operation keywords (CQL)",
                    "Operators that test, cast, and convert values between types."),
            new Category("Comparison operator keywords (CQL)", "Comparison Operators", "Operation keywords (CQL)",
                    "Operators that compare values."),
            new Category("Arithmetic operator keywords (CQL)", "Arithmetic Operators", "Operation keywords (CQL)",
                    "Operators that compute numeric results and value boundaries."),
            new Category("Date and time operator keywords (CQL)", "Date and Time Operators", "Operation keywords (CQL)",
                    "Operators that compare, extract from, and compute with dates and times."),
            new Category("Interval operator keywords (CQL)", "Interval Operators", "Operation keywords (CQL)",
                    "Operators on intervals: boundaries, comparisons between intervals, and interval construction."),
            new Category("List operator keywords (CQL)", "List Operators", "Operation keywords (CQL)",
                    "Operators on lists: membership, set operations, and restructuring."),
            new Category("Conditional expression keywords (CQL)", "Conditional Expressions", "Operation keywords (CQL)",
                    "Keywords that choose between results based on a condition."));

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
