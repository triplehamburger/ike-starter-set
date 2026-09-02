package network.ike.foundation.ike.terms.foundation;

import dev.ikm.tinkar.entity.builder.KnowledgeSet;

/** Composes every ingested-foundation section onto the caller's KnowledgeSet (IKE-Network/ike-issues#872). */
public final class FoundationSet {

    private FoundationSet() {
    }

    public static void compose(KnowledgeSet set) {
        Section1.compose(set); // Uninitialized
        Section2.compose(set); // Language
        Section3.compose(set); // ImmutableCoordinate properties
        Section4.compose(set); // Has Active Ingredient
        Section5.compose(set); // Object
        Section6.compose(set); // Author
        Section7.compose(set); // Tinkar Model concept
        Section8.compose(set); // Identifier Value
        Section9.compose(set); // Concept type
        Section10.compose(set); // EL++ terminological axioms
        Section11.compose(set); // Concept details tree table
        Section12.compose(set); // Description-logic profile
        Section13.compose(set); // Language coordinate properties
        Section14.compose(set); // Concrete value operator
        Section15.compose(set); // Stated Definition
        Section16.compose(set); // Status
        Section17.compose(set); // Laterality
        Section18.compose(set); // Description type
        Section19.compose(set); // Display fields
        Section20.compose(set); // Chronicle properties
        Section21.compose(set); // Axiom Syntax
        Section22.compose(set); // Logic coordinate properties
        Section23.compose(set); // Sandbox component
        Section24.compose(set); // Semantic properties
        Section25.compose(set); // Value Constraint Source
        Section26.compose(set); // Path
        Section27.compose(set); // Path coordinate properties
        Section28.compose(set); // Feature Type
        Section29.compose(set); // Object properties
        Section30.compose(set); // Action properties
        Section31.compose(set); // Version properties
        Section32.compose(set); // Property sequence implication
        Section33.compose(set); // Value Range
        Section34.compose(set); // Value Constraint
        Section35.compose(set); // Component type focus
        Section36.compose(set); // Correlation properties
        Section37.compose(set); // Description
        Section38.compose(set); // Grouping
        Section39.compose(set); // Axiom origin
        Section40.compose(set); // Description acceptability
        Section41.compose(set); // Meaning
        Section42.compose(set); // Dialect
        Section43.compose(set); // Reflexive Feature
        Section44.compose(set); // Version
        Section45.compose(set); // Description case significance
        Section46.compose(set); // Annotation type
        Section47.compose(set); // Tree amalgam properties
        Section48.compose(set); // Transitive Feature
        Section49.compose(set); // Annotation property set
        Section50.compose(set); // Data property set
        Section51.compose(set); // Text
        Section52.compose(set); // Is a
        Section53.compose(set); // Inferred Definition
        Section54.compose(set); // Description semantic
        Section55.compose(set); // Model concept
        Section56.compose(set); // Module
        Section57.compose(set); // Any component
        Section58.compose(set); // Native Identifier
        Section59.compose(set); // Has Dose Form
        Section60.compose(set); // Logical Definition
        Section61.compose(set); // Identifier source
        Section62.compose(set); // Description list for concept
        Section63.compose(set); // Creative Commons BY license
        Section64.compose(set); // Purpose
        Section65.compose(set); // Property Sequence
        Section66.compose(set); // Directed graph
        Section67.compose(set); // Phenomenon
        Section68.compose(set); // Relationship destination
        Section69.compose(set); // Relationship origin
        Section70.compose(set); // Tinkar root concept (part 1)
        Section71.compose(set); // Tinkar root concept (part 2)
    }
}
