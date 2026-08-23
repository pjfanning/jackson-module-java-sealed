package com.github.pjfanning.jackson.sealed.poly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.pjfanning.jackson.sealed.SealedPolymorphismModule;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.AjarHolder;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Ajar;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Beacon;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.BeaconHolder;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.BigCrate;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Bike;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Car;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Cargo;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Colour;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Crate;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Deck;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Garage;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Hold;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Loose;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Manifest;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Propped;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Pulse;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Scooter;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Shipment;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Stamped;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Tier1;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Tier2;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Tier3A;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.TierHolder;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Vehicle;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Hierarchies opted in by registration rather than by extending the marker, for types whose source
 * cannot be changed. A registered hierarchy is handled exactly as a marked one, and is held to the
 * same requirement that it be sealed.
 */
class RegisteredTypeTest {

    private static ObjectMapper mapperFor(Class<?>... roots) {
        SealedPolymorphismModule module = new SealedPolymorphismModule();
        for (Class<?> root : roots) {
            module.registerSealedInterfaceOrClass(root);
        }
        return JsonMapper.builder().addModule(module).build();
    }

    private final ObjectMapper mapper = mapperFor(Vehicle.class, Cargo.class, Beacon.class, Crate.class);

    private <T> T roundTrip(ObjectMapper m, T value, Class<T> clazz) {
        return m.readValue(m.writeValueAsString(value), clazz);
    }

    @Test
    void tagsARegisteredHierarchy() {
        assertThat(mapper.writeValueAsString(new Garage("bay", new Car("abc"))))
                .isEqualTo("{\"name\":\"bay\",\"vehicle\":{\"@type\":\"Car\",\"plate\":\"abc\"}}");
        assertThat(mapper.writeValueAsString(new Garage("bay", new Scooter())))
                .isEqualTo("{\"name\":\"bay\",\"vehicle\":{\"@type\":\"Scooter\"}}");
    }

    @Test
    void roundTripsARegisteredHierarchy() {
        assertThat(roundTrip(mapper, new Garage("bay", new Car("abc")), Garage.class))
                .isEqualTo(new Garage("bay", new Car("abc")));
        assertThat(roundTrip(mapper, new Garage("bay", new Bike(21)), Garage.class))
                .isEqualTo(new Garage("bay", new Bike(21)));
    }

    @Test
    void readsARegisteredBaseTypeAtTheTopLevel() {
        assertThat(mapper.readValue("{\"@type\":\"Car\",\"plate\":\"abc\"}", Vehicle.class))
                .isEqualTo(new Car("abc"));
    }

    /** The same types, through a module that was not told about them, must be untouched. */
    @Test
    void leavesTheSameHierarchyAloneWhenItIsNotRegistered() {
        ObjectMapper plain = JsonMapper.builder().addModule(new SealedPolymorphismModule()).build();
        assertThat(plain.writeValueAsString(new Car("abc"))).isEqualTo("{\"plate\":\"abc\"}");
        assertThat(plain.readValue("{\"plate\":\"abc\"}", Car.class)).isEqualTo(new Car("abc"));
    }

    @Test
    void appliesTheSameNamingRulesToARegisteredHierarchy() {
        assertThat(mapper.writeValueAsString(new Manifest(new Hold.Item(1))))
                .isEqualTo("{\"cargo\":{\"@type\":\"Hold$Item\",\"n\":1}}");
        assertThat(mapper.writeValueAsString(new Manifest(new Deck.Item("x"))))
                .isEqualTo("{\"cargo\":{\"@type\":\"Deck$Item\",\"s\":\"x\"}}");
        assertThat(roundTrip(mapper, new Manifest(new Deck.Item("x")), Manifest.class))
                .isEqualTo(new Manifest(new Deck.Item("x")));
    }

    /** Enums are left to Jackson in a registered hierarchy exactly as in a marked one. */
    @Test
    void leavesEnumsOfARegisteredHierarchyToJackson() {
        assertThat(mapper.writeValueAsString(new BeaconHolder(Colour.RED)))
                .isEqualTo("{\"beacon\":\"RED\"}");
        assertThat(roundTrip(mapper, new BeaconHolder(new Pulse(50)), BeaconHolder.class))
                .isEqualTo(new BeaconHolder(new Pulse(50)));
    }

    @Test
    void dispatchesAtARegisteredConcreteRoot() {
        assertThat(mapper.writeValueAsString(new Shipment(new Crate(2))))
                .isEqualTo("{\"crate\":{\"@type\":\"Crate\",\"size\":2}}");
        String json = mapper.writeValueAsString(new Shipment(new BigCrate(3, "L")));
        assertThat(json).isEqualTo("{\"crate\":{\"@type\":\"BigCrate\",\"size\":3,\"label\":\"L\"}}");
        Crate crate = mapper.readValue(json, Shipment.class).crate();
        assertThat(crate).isInstanceOf(BigCrate.class);
        assertThat(((BigCrate) crate).getLabel()).isEqualTo("L");
    }

    /** Registering a type part way down a hierarchy makes that type the root. */
    @Test
    void treatsARegisteredMidHierarchyTypeAsTheRoot() {
        ObjectMapper m = mapperFor(Tier2.class);
        assertThat(m.writeValueAsString(new TierHolder(new Tier3A(1))))
                .isEqualTo("{\"value\":{\"@type\":\"Tier3A\",\"a\":1}}");
        assertThat(roundTrip(m, new TierHolder(new Tier3A(1)), TierHolder.class))
                .isEqualTo(new TierHolder(new Tier3A(1)));
        // Tier1 sits above the registered root, so it was never opted in
        assertThat(Tier1.class.isAssignableFrom(Tier3A.class)).isTrue();
    }

    @Test
    void handlesMarkedAndRegisteredHierarchiesInTheSameMapper() {
        ObjectMapper m = mapperFor(Vehicle.class);
        assertThat(m.writeValueAsString(new Fixtures.Owner("ann", new Fixtures.Dog("rex"))))
                .isEqualTo("{\"name\":\"ann\",\"pet\":{\"@type\":\"Dog\",\"name\":\"rex\"}}");
        assertThat(m.writeValueAsString(new Garage("bay", new Car("abc"))))
                .isEqualTo("{\"name\":\"bay\",\"vehicle\":{\"@type\":\"Car\",\"plate\":\"abc\"}}");
    }

    @Test
    void refusesToRegisterATypeThatIsNotSealed() {
        assertThatThrownBy(() -> new SealedPolymorphismModule().registerSealedInterfaceOrClass(Loose.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Loose")
                .hasMessageContaining("is not sealed")
                .hasMessageContaining("registration replaces");
    }

    @Test
    void refusesToRegisterAnEnum() {
        assertThatThrownBy(() -> new SealedPolymorphismModule().registerSealedInterfaceOrClass(Colour.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is an enum")
                .hasMessageContaining("Register the sealed interface it implements");
    }

    @Test
    void refusesToRegisterNull() {
        assertThatThrownBy(() -> new SealedPolymorphismModule().registerSealedInterfaceOrClass(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    /** Registration does not relax the closed-hierarchy rule below the root. */
    @Test
    void stillRefusesARegisteredHierarchyReopenedBelowTheRoot() {
        ObjectMapper m = mapperFor(Ajar.class);
        assertThatThrownBy(() -> m.writeValueAsString(new AjarHolder(new Propped(1))))
                .satisfies(error -> {
                    Throwable cause = error;
                    while (cause.getCause() != null && cause.getCause() != cause) {
                        cause = cause.getCause();
                    }
                    assertThat(String.valueOf(cause.getMessage()))
                            .contains("Propped")
                            .contains("neither sealed nor final");
                });
    }

    @Test
    void refusesToRegisterATypeThatCarriesJsonTypeInfo() {
        assertThatThrownBy(() -> new SealedPolymorphismModule().registerSealedInterfaceOrClass(Stamped.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stamped")
                .hasMessageContaining("carries @JsonTypeInfo")
                .hasMessageContaining("two type properties at once");
    }

    @Test
    void exposesWhatWasRegistered() {
        SealedPolymorphismModule module = new SealedPolymorphismModule()
                .registerSealedInterfaceOrClass(Vehicle.class)
                .registerSealedInterfaceOrClass(Cargo.class);
        assertThat(module.registeredTypes()).containsExactly(Vehicle.class, Cargo.class);
        assertThat(new SealedPolymorphismModule().registeredTypes()).isEmpty();
    }

    @Test
    void ignoresARepeatedRegistration() {
        SealedPolymorphismModule module = new SealedPolymorphismModule()
                .registerSealedInterfaceOrClass(Vehicle.class)
                .registerSealedInterfaceOrClass(Vehicle.class);
        assertThat(module.registeredTypes()).containsExactly(Vehicle.class);
    }

    /** Registering is cumulative - each call adds a hierarchy, it does not replace the last. */
    @Test
    void handlesEveryHierarchyRegisteredOnOneModule() {
        SealedPolymorphismModule module = new SealedPolymorphismModule()
                .registerSealedInterfaceOrClass(Vehicle.class)
                .registerSealedInterfaceOrClass(Cargo.class);
        ObjectMapper m = JsonMapper.builder().addModule(module).build();
        assertThat(m.writeValueAsString(new Garage("bay", new Car("abc"))))
                .isEqualTo("{\"name\":\"bay\",\"vehicle\":{\"@type\":\"Car\",\"plate\":\"abc\"}}");
        assertThat(m.writeValueAsString(new Manifest(new Hold.Item(1))))
                .isEqualTo("{\"cargo\":{\"@type\":\"Hold$Item\",\"n\":1}}");
        // and the marker still works alongside them
        assertThat(m.writeValueAsString(new Fixtures.Owner("ann", new Fixtures.Dog("rex"))))
                .isEqualTo("{\"name\":\"ann\",\"pet\":{\"@type\":\"Dog\",\"name\":\"rex\"}}");
    }

    /** Jackson drops a module whose registration id it has already seen. */
    @Test
    void distinguishesModulesByWhatTheyRegister() {
        assertThat(new SealedPolymorphismModule().registerSealedInterfaceOrClass(Vehicle.class).getRegistrationId())
                .isNotEqualTo(new SealedPolymorphismModule()
                        .registerSealedInterfaceOrClass(Cargo.class).getRegistrationId());
        assertThat(new SealedPolymorphismModule().getRegistrationId())
                .isNotEqualTo(new SealedPolymorphismModule()
                        .registerSealedInterfaceOrClass(Vehicle.class).getRegistrationId());
    }
}
