package com.github.pjfanning.jackson.sealed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.pjfanning.jackson.sealed.poly.Fixtures;
import org.junit.jupiter.api.Test;

/**
 * The naming rules themselves, pinned directly rather than through a mapper. The round trip tests
 * cover the names that fixtures happen to produce; these cover the derivation, including shapes no
 * fixture hits.
 */
class SealedTypesTest {

    @Test
    void derivesPrefixesLongestFirstForATopLevelRoot() {
        assertThat(SealedTypes.prefixesFor("com.example.Dup"))
                .containsExactly("com.example.Dup$", "com.example.");
    }

    @Test
    void derivesAPrefixForEveryClassEnclosingTheRoot() {
        assertThat(SealedTypes.prefixesFor("com.example.Fixtures$Wrapper$Event")).containsExactly(
                "com.example.Fixtures$Wrapper$Event$",
                "com.example.Fixtures$Wrapper$",
                "com.example.Fixtures$",
                "com.example.");
    }

    @Test
    void derivesPrefixesForARootInTheDefaultPackage() {
        assertThat(SealedTypes.prefixesFor("Root")).containsExactly("Root$", "");
    }

    @Test
    void namesAnImplementationDeclaredBesideTheRootBySimpleName() {
        assertThat(SealedTypes.typeNameFor(Fixtures.Animal.class, Fixtures.Dog.class)).isEqualTo("Dog");
        assertThat(SealedTypes.typeNameFor(Fixtures.Animal.class, Fixtures.Unknown.class)).isEqualTo("Unknown");
    }

    @Test
    void namesAnImplementationDeclaredInsideTheRootBySimpleName() {
        assertThat(SealedTypes.typeNameFor(Fixtures.Payment.class, Fixtures.Payment.Card.class)).isEqualTo("Card");
    }

    @Test
    void namesAnImplementationBesideANestedRootBySimpleName() {
        assertThat(SealedTypes.typeNameFor(Fixtures.Wrapper.Event.class, Fixtures.Wrapper.Created.class))
                .isEqualTo("Created");
    }

    @Test
    void keepsTheEnclosingClassOfAnImplementationDeclaredElsewhere() {
        assertThat(SealedTypes.typeNameFor(Fixtures.Dup.class, Fixtures.FirstGroup.Same.class))
                .isEqualTo("FirstGroup.Same");
        assertThat(SealedTypes.typeNameFor(Fixtures.Dup.class, Fixtures.SecondGroup.Same.class))
                .isEqualTo("SecondGroup.Same");
    }

    /** Only a `$` that separates a class from the one enclosing it becomes a dot. */
    @Test
    void turnsOnlyNestingBoundariesIntoDots() {
        assertThat(SealedTypes.typeNameFor(Fixtures.Expr.class, Fixtures.Grouped.Inner.class))
                .isEqualTo("Grouped.Inner");
        assertThat(SealedTypes.typeNameFor(Fixtures.Expr.class, Fixtures.Odd$Name.class))
                .isEqualTo("Odd$Name");
        assertThat(SealedTypes.typeNameFor(Fixtures.Expr.class, Fixtures.Lit.class)).isEqualTo("Lit");
    }

    @Test
    void namesAConcreteRootAndItsSubclasses() {
        assertThat(SealedTypes.typeNameFor(Fixtures.Node.class, Fixtures.Node.class)).isEqualTo("Node");
        assertThat(SealedTypes.typeNameFor(Fixtures.Node.class, Fixtures.Twig.class)).isEqualTo("Twig");
    }

    /** Nothing in a sealed hierarchy can reach this, but the derivation still has to be total. */
    @Test
    void fallsBackToTheSimpleNameWhenNoPrefixIsShared() {
        assertThat(SealedTypes.typeNameFor(Fixtures.Animal.class, String.class)).isEqualTo("String");
    }

    @Test
    void neverDerivesAFullyQualifiedName() {
        assertThat(SealedTypes.typeNameFor(Fixtures.Animal.class, Fixtures.Dog.class)).doesNotContain(".");
    }

    @Test
    void findsTheTopOfTheHierarchy() {
        assertThat(SealedTypes.rootOf(Fixtures.Dog.class)).isEqualTo(Fixtures.Animal.class);
        assertThat(SealedTypes.rootOf(Fixtures.Animal.class)).isEqualTo(Fixtures.Animal.class);
        assertThat(SealedTypes.rootOf(Fixtures.Twig.class)).isEqualTo(Fixtures.Node.class);
        assertThat(SealedTypes.rootOf(Fixtures.Status.class)).isEqualTo(Fixtures.Signal.class);
    }

    @Test
    void treatsTheMarkerItselfAsUnmarked() {
        assertThat(SealedTypes.isMarked(SealedPolymorphismSupport.class)).isFalse();
        assertThat(SealedTypes.isMarked(Fixtures.Dog.class)).isTrue();
        assertThat(SealedTypes.isMarked(Fixtures.PlainDog.class)).isFalse();
    }

    @Test
    void standsDownForAHierarchyThatCarriesJsonTypeInfo() {
        assertThat(SealedTypes.isMarked(Fixtures.Cheque.class)).isTrue();
        assertThat(SealedTypes.isSupported(Fixtures.Cheque.class)).isFalse();
        assertThat(SealedTypes.hierarchyOf(Fixtures.Cheque.class).isJacksonOwned()).isTrue();
    }

    @Test
    void tellsBaseTypesFromValues() {
        assertThat(SealedTypes.isBaseType(Fixtures.Animal.class)).isTrue();
        assertThat(SealedTypes.isBaseType(Fixtures.Shape.class)).isTrue();
        assertThat(SealedTypes.isBaseType(Fixtures.Dog.class)).isFalse();
        assertThat(SealedTypes.isBaseType(Fixtures.Node.class)).isFalse();
    }

    @Test
    void dispatchesOnlyAtAConcreteTypeThatIsExtended() {
        assertThat(SealedTypes.needsSubtypeDispatch(Fixtures.Node.class)).isTrue();
        assertThat(SealedTypes.needsSubtypeDispatch(Fixtures.Branch.class)).isTrue();
        assertThat(SealedTypes.needsSubtypeDispatch(Fixtures.Twig.class)).isFalse();
        assertThat(SealedTypes.needsSubtypeDispatch(Fixtures.Dog.class)).isFalse();
    }

    @Test
    void findsTheEnumAConstantBelongsTo() {
        assertThat(SealedTypes.enumClassOf(Fixtures.Status.class)).isEqualTo(Fixtures.Status.class);
        assertThat(SealedTypes.enumClassOf(Fixtures.Status.IDLE.getClass())).isEqualTo(Fixtures.Status.class);
        assertThat(SealedTypes.enumClassOf(Fixtures.Dog.class)).isNull();
    }

    /** An enum permitted by the root is Jackson's to write, so it takes no name in the table. */
    @Test
    void givesNoNameToAnEnumMember() {
        SealedHierarchy hierarchy = SealedTypes.hierarchyOf(Fixtures.Signal.class);
        assertThat(hierarchy.resolve(Fixtures.Signal.class, "Status")).isNull();
        assertThat(hierarchy.resolve(Fixtures.Signal.class, "Status$IDLE")).isNull();
        assertThat(hierarchy.resolve(Fixtures.Signal.class, "IDLE")).isNull();
        // its sibling in the same hierarchy is named as usual
        assertThat(hierarchy.resolve(Fixtures.Signal.class, "Data")).isEqualTo(Fixtures.Data.class);
    }

    @Test
    void doesNotHandleAnEnumMember() {
        assertThat(SealedTypes.isMarked(Fixtures.Status.class)).isTrue();
        assertThat(SealedTypes.isSupported(Fixtures.Status.class)).isFalse();
        assertThat(SealedTypes.isBaseType(Fixtures.Status.class)).isFalse();
        assertThat(SealedTypes.needsSubtypeDispatch(Fixtures.Status.class)).isFalse();
    }

    @Test
    void resolvesANameToItsImplementation() {
        SealedHierarchy hierarchy = SealedTypes.hierarchyOf(Fixtures.Animal.class);
        assertThat(hierarchy.resolve(Fixtures.Animal.class, "Dog")).isEqualTo(Fixtures.Dog.class);
    }

    /** A name only resolves where the declared type could actually hold the result. */
    @Test
    void refusesANameFromASiblingBranch() {
        SealedHierarchy hierarchy = SealedTypes.hierarchyOf(Fixtures.Animal.class);
        assertThat(hierarchy.resolve(Fixtures.Animal.class, "Bird")).isEqualTo(Fixtures.Bird.class);
        assertThat(hierarchy.resolve(Fixtures.Dog.class, "Bird")).isNull();
    }

    @Test
    void refusesANameFromOutsideTheHierarchy() {
        SealedHierarchy hierarchy = SealedTypes.hierarchyOf(Fixtures.Animal.class);
        assertThat(hierarchy.resolve(Fixtures.Animal.class, "Rect")).isNull();
        assertThat(hierarchy.resolve(Fixtures.Animal.class,
                "com.github.pjfanning.jackson.sealed.poly.Fixtures$Dog")).isNull();
        assertThat(hierarchy.resolve(Fixtures.Animal.class, null)).isNull();
    }

    @Test
    void refusesToNameATypeOutsideTheHierarchy() {
        assertThatThrownBy(() -> SealedTypes.hierarchyOf(Fixtures.Animal.class).nameOf(String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not a permitted implementation");
    }

    @Test
    void survivesTheCacheBeingCleared() {
        SealedHierarchy before = SealedTypes.hierarchyOf(Fixtures.Animal.class);
        SealedPolymorphismModule.clearCache();
        SealedHierarchy after = SealedTypes.hierarchyOf(Fixtures.Animal.class);
        assertThat(after).isNotSameAs(before);
        assertThat(after.resolve(Fixtures.Animal.class, "Dog")).isEqualTo(Fixtures.Dog.class);
    }
}
