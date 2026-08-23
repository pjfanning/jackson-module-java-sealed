package com.github.pjfanning.jackson.sealed.poly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.pjfanning.jackson.sealed.SealedPolymorphismModule;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.Ajar;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.AjarHolder;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.Beacon;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.BeaconHolder;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.BigCrate;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.Bike;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.Car;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.Cargo;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.Colour;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.Crate;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.Deck;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.Garage;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.Hold;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.Loose;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.LooseHolder;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.LooseImpl;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.Manifest;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.Propped;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.Pulse;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.Shipment;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.Signed;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.Stamped;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.StampedHolder;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.Tier2;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.Tier3A;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.TierHolder;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.Truck;
import com.github.pjfanning.jackson.sealed.poly.MixInFixtures.Vehicle;
import com.github.pjfanning.jackson.sealed.poly.mixin.VehicleMixIns;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Opting a hierarchy in with a Jackson mix-in, for classes that cannot be changed to extend the
 * marker themselves.
 *
 * <p>A mix-in does not change the type on the JVM, so the marker is not inherited by the
 * implementations the way it would be if the base extended it. Only the root is opted in directly,
 * and the module has to read that from the mapper's configuration rather than from the classes.
 */
class MixInTest {

    private static ObjectMapper mapperWith(Class<?> target, Class<?> mixIn) {
        return JsonMapper.builder()
                .addModule(new SealedPolymorphismModule())
                .addMixIn(target, mixIn)
                .build();
    }

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new SealedPolymorphismModule())
            .addMixIn(Vehicle.class, VehicleMixIns.OptIn.class)
            .addMixIn(Cargo.class, VehicleMixIns.OptIn.class)
            .addMixIn(Crate.class, VehicleMixIns.OptIn.class)
            .addMixIn(Beacon.class, VehicleMixIns.OptIn.class)
            .build();

    private <T> T roundTrip(ObjectMapper m, T value, Class<T> clazz) {
        return m.readValue(m.writeValueAsString(value), clazz);
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error.getCause();
        return (cause == null || cause == error) ? error : rootCause(cause);
    }

    @Test
    void tagsAHierarchyOptedInByMixIn() {
        assertThat(mapper.writeValueAsString(new Garage(new Car(4))))
                .isEqualTo("{\"vehicle\":{\"@type\":\"Car\",\"wheels\":4}}");
        assertThat(mapper.writeValueAsString(new Garage(new Bike())))
                .isEqualTo("{\"vehicle\":{\"@type\":\"Bike\"}}");
    }

    @Test
    void roundTripsAHierarchyOptedInByMixIn() {
        assertThat(roundTrip(mapper, new Garage(new Car(4)), Garage.class)).isEqualTo(new Garage(new Car(4)));
        assertThat(roundTrip(mapper, new Garage(new Truck(3, "sand")), Garage.class))
                .isEqualTo(new Garage(new Truck(3, "sand")));
        assertThat(roundTrip(mapper, new Garage(new Bike()), Garage.class)).isEqualTo(new Garage(new Bike()));
    }

    @Test
    void readsAMixedInBaseTypeAtTheTopLevel() {
        assertThat(mapper.readValue("{\"@type\":\"Car\",\"wheels\":4}", Vehicle.class)).isEqualTo(new Car(4));
    }

    /** The same classes through a mapper without the mix-in must be untouched. */
    @Test
    void leavesTheSameHierarchyAloneWithoutTheMixIn() {
        ObjectMapper plain = JsonMapper.builder().addModule(new SealedPolymorphismModule()).build();
        assertThat(plain.writeValueAsString(new Car(4))).isEqualTo("{\"wheels\":4}");
        assertThat(plain.readValue("{\"wheels\":4}", Car.class)).isEqualTo(new Car(4));
    }

    /** A mix-in that does not carry the marker opts nothing in. */
    @Test
    void ignoresAMixInThatDoesNotCarryTheMarker() {
        ObjectMapper m = mapperWith(Vehicle.class, VehicleMixIns.Unrelated.class);
        assertThat(m.writeValueAsString(new Car(4))).isEqualTo("{\"wheels\":4}");
    }

    @Test
    void appliesTheSameNamingRulesToAMixedInHierarchy() {
        assertThat(mapper.writeValueAsString(new Manifest(new Hold.Item(1))))
                .isEqualTo("{\"cargo\":{\"@type\":\"Hold.Item\",\"n\":1}}");
        assertThat(mapper.writeValueAsString(new Manifest(new Deck.Item("x"))))
                .isEqualTo("{\"cargo\":{\"@type\":\"Deck.Item\",\"s\":\"x\"}}");
        assertThat(roundTrip(mapper, new Manifest(new Deck.Item("x")), Manifest.class))
                .isEqualTo(new Manifest(new Deck.Item("x")));
    }

    @Test
    void dispatchesAtAMixedInConcreteRoot() {
        assertThat(mapper.writeValueAsString(new Shipment(new Crate(2))))
                .isEqualTo("{\"crate\":{\"@type\":\"Crate\",\"size\":2}}");
        String json = mapper.writeValueAsString(new Shipment(new BigCrate(3, "L")));
        assertThat(json).isEqualTo("{\"crate\":{\"@type\":\"BigCrate\",\"size\":3,\"label\":\"L\"}}");
        Crate crate = mapper.readValue(json, Shipment.class).crate();
        assertThat(crate).isInstanceOf(BigCrate.class);
        assertThat(((BigCrate) crate).getLabel()).isEqualTo("L");
    }

    @Test
    void leavesEnumsOfAMixedInHierarchyToJackson() {
        assertThat(mapper.writeValueAsString(new BeaconHolder(Colour.RED)))
                .isEqualTo("{\"beacon\":\"RED\"}");
        assertThat(roundTrip(mapper, new BeaconHolder(new Pulse(50)), BeaconHolder.class))
                .isEqualTo(new BeaconHolder(new Pulse(50)));
    }

    /** A mix-in on a type part way down a hierarchy makes that type the root. */
    @Test
    void treatsAMixedInMidHierarchyTypeAsTheRoot() {
        ObjectMapper m = mapperWith(Tier2.class, VehicleMixIns.OptIn.class);
        assertThat(m.writeValueAsString(new TierHolder(new Tier3A(1))))
                .isEqualTo("{\"value\":{\"@type\":\"Tier3A\",\"a\":1}}");
        assertThat(roundTrip(m, new TierHolder(new Tier3A(1)), TierHolder.class))
                .isEqualTo(new TierHolder(new Tier3A(1)));
    }

    @Test
    void handlesMarkedAndMixedInHierarchiesInTheSameMapper() {
        ObjectMapper m = JsonMapper.builder()
                .addModule(new SealedPolymorphismModule())
                .addMixIn(Vehicle.class, VehicleMixIns.OptIn.class)
                .build();
        assertThat(m.writeValueAsString(new Fixtures.Owner("ann", new Fixtures.Dog("rex"))))
                .isEqualTo("{\"name\":\"ann\",\"pet\":{\"@type\":\"Dog\",\"name\":\"rex\"}}");
        assertThat(m.writeValueAsString(new Garage(new Car(4))))
                .isEqualTo("{\"vehicle\":{\"@type\":\"Car\",\"wheels\":4}}");
    }

    /** Mixing in does not relax the closed-hierarchy rule. */
    @Test
    void refusesAMixedInHierarchyThatIsNotSealed() {
        ObjectMapper m = mapperWith(Loose.class, VehicleMixIns.OptIn.class);
        assertThatThrownBy(() -> m.writeValueAsString(new LooseHolder(new LooseImpl(1))))
                .satisfies(error -> assertThat(String.valueOf(rootCause(error).getMessage()))
                        .contains("Loose")
                        .contains("is not sealed"));
    }

    @Test
    void refusesAMixedInHierarchyReopenedBelowTheRoot() {
        ObjectMapper m = mapperWith(Ajar.class, VehicleMixIns.OptIn.class);
        assertThatThrownBy(() -> m.writeValueAsString(new AjarHolder(new Propped(1))))
                .satisfies(error -> assertThat(String.valueOf(rootCause(error).getMessage()))
                        .contains("Propped")
                        .contains("neither sealed nor final"));
    }

    /** A mix-in supplying @JsonTypeInfo hands the hierarchy back to Jackson, marker or not. */
    @Test
    void standsDownWhenTheMixInSuppliesJsonTypeInfo() {
        ObjectMapper m = mapperWith(Vehicle.class, VehicleMixIns.OptInThenDefer.class);
        // Jackson's own handling writes the type id it was told to; the point is that it, and not
        // this module, is in charge - so there is a "kind" and no "@type"
        assertThat(m.writeValueAsString(new Garage(new Car(4))))
                .contains("\"kind\":")
                .doesNotContain("@type");
    }

    /** A hierarchy Jackson already owns by its own annotations is untouched by a marker mix-in. */
    @Test
    void leavesAnAnnotatedHierarchyToJackson() {
        ObjectMapper m = mapperWith(Stamped.class, VehicleMixIns.OptIn.class);
        assertThat(m.writeValueAsString(new StampedHolder(new Signed(7))))
                .isEqualTo("{\"stamped\":{\"kind\":\"Signed\",\"id\":7}}");
        assertThat(roundTrip(m, new StampedHolder(new Signed(7)), StampedHolder.class))
                .isEqualTo(new StampedHolder(new Signed(7)));
    }
}
