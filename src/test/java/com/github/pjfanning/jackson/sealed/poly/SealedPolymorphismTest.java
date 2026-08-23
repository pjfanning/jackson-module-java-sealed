package com.github.pjfanning.jackson.sealed.poly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.github.pjfanning.jackson.sealed.SealedPolymorphismModule;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Animal;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Bird;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Branch;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Cheque;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Data;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Dog;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Drawing;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.DupHolder;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Envelope;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.FirstGroup;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.LeafA;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.LeafB;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.LeafHolder;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Ledger;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Limb;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.MaybeOwner;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Node;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Order;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Owner;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Payment;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Plain;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.PlainDog;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Point;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Rect;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.SecondGroup;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Shelter;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Signal;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.SignalHolder;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Status;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.StatusHolder;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Tree;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Twig;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Unknown;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Wrapper;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Zoo;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Ported from jackson-module-scala's {@code SealedPolymorphismSpec}. */
class SealedPolymorphismTest {

    private final ObjectMapper mapper = JsonMapper.builder().addModule(new SealedPolymorphismModule()).build();

    private <T> T roundTrip(T value, Class<T> clazz) {
        return mapper.readValue(mapper.writeValueAsString(value), clazz);
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error.getCause();
        return (cause == null || cause == error) ? error : rootCause(cause);
    }

    @Test
    void tagsAnImplementationDeclaredBesideTheBaseType() {
        assertThat(mapper.writeValueAsString(new Owner("ann", new Dog("rex"))))
                .isEqualTo("{\"name\":\"ann\",\"pet\":{\"@type\":\"Dog\",\"name\":\"rex\"}}");
    }

    @Test
    void tagsAnImplementationCarryingNoState() {
        assertThat(mapper.writeValueAsString(new Owner("ann", new Unknown())))
                .isEqualTo("{\"name\":\"ann\",\"pet\":{\"@type\":\"Unknown\"}}");
    }

    @Test
    void roundTripsAnImplementationDeclaredBesideTheBaseType() {
        assertThat(roundTrip(new Owner("ann", new Dog("rex")), Owner.class))
                .isEqualTo(new Owner("ann", new Dog("rex")));
        assertThat(roundTrip(new Owner("ann", new Bird("tweety", true)), Owner.class))
                .isEqualTo(new Owner("ann", new Bird("tweety", true)));
    }

    @Test
    void roundTripsAnImplementationCarryingNoState() {
        assertThat(roundTrip(new Owner("ann", new Unknown()), Owner.class).pet()).isEqualTo(new Unknown());
    }

    @Test
    void roundTripsASealedAbstractClassBase() {
        assertThat(roundTrip(new Drawing(new Rect(2.0, 3.0)), Drawing.class))
                .isEqualTo(new Drawing(new Rect(2.0, 3.0)));
        assertThat(roundTrip(new Drawing(new Point()), Drawing.class).shape()).isInstanceOf(Point.class);
    }

    @Test
    void roundTripsImplementationsDeclaredInsideTheBaseType() {
        assertThat(mapper.writeValueAsString(new Order(10, new Payment.Card("4242"))))
                .isEqualTo("{\"total\":10,\"payment\":{\"@type\":\"Card\",\"last4\":\"4242\"}}");
        assertThat(roundTrip(new Order(10, new Payment.Card("4242")), Order.class))
                .isEqualTo(new Order(10, new Payment.Card("4242")));
        assertThat(roundTrip(new Order(10, new Payment.Cash()), Order.class).payment())
                .isEqualTo(new Payment.Cash());
    }

    @Test
    void roundTripsAHierarchyNestedInAnUnrelatedClass() {
        assertThat(mapper.writeValueAsString(new Envelope(new Wrapper.Created(7))))
                .isEqualTo("{\"event\":{\"@type\":\"Created\",\"id\":7}}");
        assertThat(roundTrip(new Envelope(new Wrapper.Created(7)), Envelope.class))
                .isEqualTo(new Envelope(new Wrapper.Created(7)));
        assertThat(roundTrip(new Envelope(new Wrapper.Deleted()), Envelope.class).event())
                .isEqualTo(new Wrapper.Deleted());
    }

    @Test
    void roundTripsACollectionOfImplementations() {
        Shelter shelter = new Shelter(List.of(new Dog("rex"), new Unknown(), new Bird("tweety", false)));
        assertThat(roundTrip(shelter, Shelter.class)).isEqualTo(shelter);
    }

    @Test
    void readsTheBaseTypeAtTheTopLevel() {
        assertThat(mapper.readValue("{\"@type\":\"Dog\",\"name\":\"rex\"}", Animal.class)).isEqualTo(new Dog("rex"));
    }

    @Test
    void readsAValueDeclaredAsTheImplementationTypeTagAndAll() {
        assertThat(mapper.readValue("{\"@type\":\"Dog\",\"name\":\"rex\"}", Dog.class)).isEqualTo(new Dog("rex"));
    }

    @Test
    void roundTripsAnOptionalOfTheBaseType() {
        assertThat(roundTrip(new MaybeOwner("ann", Optional.of(new Dog("rex"))), MaybeOwner.class))
                .isEqualTo(new MaybeOwner("ann", Optional.of(new Dog("rex"))));
        assertThat(roundTrip(new MaybeOwner("ann", Optional.empty()), MaybeOwner.class))
                .isEqualTo(new MaybeOwner("ann", Optional.empty()));
    }

    @Test
    void roundTripsAMapValuedByTheBaseType() {
        Zoo zoo = new Zoo(Map.of("a", new Dog("rex"), "b", new Unknown()));
        assertThat(roundTrip(zoo, Zoo.class)).isEqualTo(zoo);
    }

    @Test
    void defersToJsonTypeInfoWhenTheHierarchyIsAlsoAnnotated() {
        assertThat(mapper.writeValueAsString(new Ledger(new Cheque(7))))
                .isEqualTo("{\"entry\":{\"kind\":\"Cheque\",\"number\":7}}");
        assertThat(roundTrip(new Ledger(new Cheque(7)), Ledger.class)).isEqualTo(new Ledger(new Cheque(7)));
    }

    @Test
    void reportsJsonTypeInfoOnAnImplementationRatherThanTheBase() {
        assertThatThrownBy(() -> mapper.writeValueAsString(new LeafHolder(new LeafA(1))))
                .satisfies(error -> assertThat(String.valueOf(rootCause(error).getMessage()))
                        .contains("LeafA")
                        .contains("carries @JsonTypeInfo")
                        .contains("rooted at"));
    }

    @Test
    void leavesOtherImplementationsOfThatHierarchyWorking() {
        assertThat(roundTrip(new LeafHolder(new LeafB(2)), LeafHolder.class))
                .isEqualTo(new LeafHolder(new LeafB(2)));
    }

    @Test
    void qualifiesImplementationsDeclaredInClassesThatDoNotEncloseTheBase() {
        assertThat(mapper.writeValueAsString(new DupHolder(new FirstGroup.Same(1))))
                .isEqualTo("{\"d\":{\"@type\":\"FirstGroup.Same\",\"v\":1}}");
        assertThat(mapper.writeValueAsString(new DupHolder(new SecondGroup.Same("x"))))
                .isEqualTo("{\"d\":{\"@type\":\"SecondGroup.Same\",\"v\":\"x\"}}");
        assertThat(mapper.writeValueAsString(new DupHolder(new FirstGroup.Only())))
                .isEqualTo("{\"d\":{\"@type\":\"FirstGroup.Only\"}}");
    }

    /** A dot separates a nested implementation from what encloses it, as jackson-databind does. */
    @Test
    void usesADotForTheClassEnclosingAnImplementation() {
        String json = mapper.writeValueAsString(new Fixtures.ExprHolder(new Fixtures.Grouped.Inner(5)));
        assertThat(json).isEqualTo("{\"expr\":{\"@type\":\"Grouped.Inner\",\"v\":5}}");
        assertThat(mapper.readValue(json, Fixtures.ExprHolder.class))
                .isEqualTo(new Fixtures.ExprHolder(new Fixtures.Grouped.Inner(5)));
    }

    /** A dollar that is part of a class's own name is not nesting, and must survive intact. */
    @Test
    void keepsADollarThatIsPartOfTheClassName() {
        String json = mapper.writeValueAsString(new Fixtures.ExprHolder(new Fixtures.Odd$Name(1, 2)));
        assertThat(json).isEqualTo("{\"expr\":{\"@type\":\"Odd$Name\",\"head\":1,\"tail\":2}}");
        assertThat(mapper.readValue(json, Fixtures.ExprHolder.class))
                .isEqualTo(new Fixtures.ExprHolder(new Fixtures.Odd$Name(1, 2)));
        assertThat(mapper.writeValueAsString(new Fixtures.ExprHolder(new Fixtures.Lit(3))))
                .isEqualTo("{\"expr\":{\"@type\":\"Lit\",\"v\":3}}");
    }

    @Test
    void roundTripsSameNamedImplementationsFromDifferentClasses() {
        assertThat(roundTrip(new DupHolder(new FirstGroup.Same(1)), DupHolder.class))
                .isEqualTo(new DupHolder(new FirstGroup.Same(1)));
        assertThat(roundTrip(new DupHolder(new SecondGroup.Same("x")), DupHolder.class))
                .isEqualTo(new DupHolder(new SecondGroup.Same("x")));
        assertThat(roundTrip(new DupHolder(new FirstGroup.Only()), DupHolder.class).d())
                .isEqualTo(new FirstGroup.Only());
    }

    @Test
    void tagsAConcreteRootAsWellAsItsSubclasses() {
        assertThat(mapper.writeValueAsString(new Tree(new Node(1))))
                .isEqualTo("{\"node\":{\"@type\":\"Node\",\"id\":1}}");
        assertThat(mapper.writeValueAsString(new Tree(new Branch(2, "b"))))
                .isEqualTo("{\"node\":{\"@type\":\"Branch\",\"id\":2,\"label\":\"b\"}}");
    }

    @Test
    void readsAConcreteRootBackAsItself() {
        Node node = mapper.readValue("{\"node\":{\"@type\":\"Node\",\"id\":1}}", Tree.class).node();
        assertThat(node.getClass()).isEqualTo(Node.class);
        assertThat(node.getId()).isEqualTo(1);
    }

    @Test
    void readsASubclassOfAConcreteRootWithoutLosingIt() {
        Node node = mapper.readValue(mapper.writeValueAsString(new Tree(new Branch(2, "b"))), Tree.class).node();
        assertThat(node).isInstanceOf(Branch.class);
        assertThat(node.getId()).isEqualTo(2);
        assertThat(((Branch) node).getLabel()).isEqualTo("b");
    }

    @Test
    void dispatchesAtAConcreteTypePartWayDownTheHierarchy() {
        String json = mapper.writeValueAsString(new Limb(new Twig(3, "t", 9)));
        assertThat(json).isEqualTo("{\"branch\":{\"@type\":\"Twig\",\"id\":3,\"label\":\"t\",\"length\":9}}");
        Branch branch = mapper.readValue(json, Limb.class).branch();
        assertThat(branch).isInstanceOf(Twig.class);
        assertThat(((Twig) branch).getLength()).isEqualTo(9);
        assertThat(branch.getLabel()).isEqualTo("t");
    }

    @Test
    void readsAnUntaggedObjectAtAConcreteDeclaredType() {
        Branch branch = mapper.readValue("{\"branch\":{\"id\":4,\"label\":\"u\"}}", Limb.class).branch();
        assertThat(branch.getClass()).isEqualTo(Branch.class);
        assertThat(branch.getLabel()).isEqualTo("u");
    }

    /** An enum is left to Jackson, which writes it as a string - it gets no tag of its own. */
    @Test
    void leavesAnEnumMemberToJackson() {
        assertThat(mapper.writeValueAsString(new SignalHolder(Status.IDLE)))
                .isEqualTo("{\"signal\":\"IDLE\"}");
        // its sibling in the same hierarchy is tagged as usual
        assertThat(mapper.writeValueAsString(new SignalHolder(new Data(1))))
                .isEqualTo("{\"signal\":{\"@type\":\"Data\",\"value\":1}}");
    }

    @Test
    void roundTripsAnEnumDeclaredAsItsOwnType() {
        assertThat(mapper.writeValueAsString(new StatusHolder(Status.BUSY)))
                .isEqualTo("{\"status\":\"BUSY\"}");
        assertThat(roundTrip(new StatusHolder(Status.BUSY), StatusHolder.class).status()).isSameAs(Status.BUSY);
    }

    /**
     * The consequence of leaving enums alone: a string is not something the base type can dispatch
     * on, so an enum value written at the base type cannot be read back there. The failure says so.
     */
    @Test
    void cannotReadAnEnumMemberBackThroughTheBaseType() {
        String json = mapper.writeValueAsString(new SignalHolder(Status.IDLE));
        assertThatThrownBy(() -> mapper.readValue(json, SignalHolder.class))
                .satisfies(error -> assertThat(String.valueOf(rootCause(error).getMessage()))
                        .contains("Expected a JSON object")
                        .contains("permits Status, Mode")
                        .contains("writes as a string")
                        .contains("Declare the property as the enum type itself"));
        assertThat(Signal.class).isNotNull();
    }

    @Test
    void leavesAnUnmarkedHierarchyAlone() {
        assertThat(mapper.writeValueAsString(new PlainDog("rex"))).isEqualTo("{\"name\":\"rex\"}");
        assertThat(mapper.readValue("{\"name\":\"rex\"}", PlainDog.class)).isEqualTo(new PlainDog("rex"));
        assertThat(Plain.class).isNotNull();
    }

    @Test
    void rejectsATypeThatNamesSomethingOutsideTheHierarchy() {
        assertThatThrownBy(() -> mapper.readValue("{\"@type\":\"Rect\",\"width\":1.0,\"height\":2.0}", Animal.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    void rejectsATypeThatTriesToNameAFullyQualifiedClass() {
        assertThatThrownBy(() -> mapper.readValue(
                "{\"@type\":\"com.github.pjfanning.jackson.sealed.poly.Fixtures$Dog\",\"name\":\"rex\"}",
                Animal.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    void rejectsAMissingType() {
        assertThatThrownBy(() -> mapper.readValue("{\"name\":\"rex\"}", Animal.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    void deserializesIntoAGenericCollectionOfTheBaseType() {
        String json = "[{\"@type\":\"Dog\",\"name\":\"rex\"},{\"@type\":\"Unknown\"}]";
        List<Animal> animals = mapper.readValue(json, new TypeReference<List<Animal>>() {
        });
        assertThat(animals).isEqualTo(List.of(new Dog("rex"), new Unknown()));
    }
}
