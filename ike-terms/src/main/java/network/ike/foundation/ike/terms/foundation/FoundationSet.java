package network.ike.foundation.ike.terms.foundation;

import dev.ikm.tinkar.entity.builder.KnowledgeSet;

/** Composes every ingested-foundation section onto the caller's KnowledgeSet (IKE-Network/ike-issues#872). */
public final class FoundationSet {

    private FoundationSet() {
    }

    public static void compose(KnowledgeSet set) {
        Uninitialized.compose(set);
        Language.compose(set);
        ImmutableCoordinateProperties.compose(set);
        HasActiveIngredient.compose(set);
        Object.compose(set);
        Author.compose(set);
        TinkarModelConcept.compose(set);
        IdentifierValue.compose(set);
        ConceptType.compose(set);
        ElPlusPlusTerminologicalAxioms.compose(set);
        ConceptDetailsTreeTable.compose(set);
        DescriptionLogicProfile.compose(set);
        LanguageCoordinateProperties.compose(set);
        ConcreteValueOperator.compose(set);
        StatedDefinition.compose(set);
        Status.compose(set);
        Laterality.compose(set);
        DescriptionType.compose(set);
        DisplayFields.compose(set);
        ChronicleProperties.compose(set);
        AxiomSyntax.compose(set);
        LogicCoordinateProperties.compose(set);
        SandboxComponent.compose(set);
        SemanticProperties.compose(set);
        ValueConstraintSource.compose(set);
        Path.compose(set);
        PathCoordinateProperties.compose(set);
        FeatureType.compose(set);
        ObjectProperties.compose(set);
        ActionProperties.compose(set);
        VersionProperties.compose(set);
        PropertySequenceImplication.compose(set);
        ValueRange.compose(set);
        ValueConstraint.compose(set);
        ComponentTypeFocus.compose(set);
        CorrelationProperties.compose(set);
        Description.compose(set);
        Grouping.compose(set);
        AxiomOrigin.compose(set);
        DescriptionAcceptability.compose(set);
        Meaning.compose(set);
        Dialect.compose(set);
        ReflexiveFeature.compose(set);
        Version.compose(set);
        DescriptionCaseSignificance.compose(set);
        AnnotationType.compose(set);
        TreeAmalgamProperties.compose(set);
        TransitiveFeature.compose(set);
        AnnotationPropertySet.compose(set);
        DataPropertySet.compose(set);
        Text.compose(set);
        IsA.compose(set);
        InferredDefinition.compose(set);
        DescriptionSemantic.compose(set);
        ModelConcept.compose(set);
        Module.compose(set);
        AnyComponent.compose(set);
        NativeIdentifier.compose(set);
        HasDoseForm.compose(set);
        LogicalDefinition.compose(set);
        IdentifierSource.compose(set);
        DescriptionListForConcept.compose(set);
        CreativeCommonsByLicense.compose(set);
        Purpose.compose(set);
        PropertySequence.compose(set);
        DirectedGraph.compose(set);
        Phenomenon.compose(set);
        RelationshipDestination.compose(set);
        RelationshipOrigin.compose(set);
        TinkarRootConceptPart1.compose(set);
        TinkarRootConceptPart2.compose(set);
    }
}
