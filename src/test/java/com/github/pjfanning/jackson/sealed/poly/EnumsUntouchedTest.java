package com.github.pjfanning.jackson.sealed.poly;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.github.pjfanning.jackson.sealed.SealedPolymorphismModule;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Gauge;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.GaugeHolder;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Level;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.LevelHolder;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Mode;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.ModeHolder;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Reading;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Signal;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.SignalHolder;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.Status;
import com.github.pjfanning.jackson.sealed.poly.Fixtures.StatusHolder;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Beacon;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.BeaconHolder;
import com.github.pjfanning.jackson.sealed.poly.UnmarkedFixtures.Colour;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Enums belong to Jackson, not to this module, even inside a hierarchy it handles.
 *
 * <p>Each case writes the same value through a mapper carrying the module and through a plain one,
 * and asserts the two agree - the strongest form of "we did not interfere", since it would catch a
 * difference this module's author had not thought to predict.
 */
class EnumsUntouchedTest {

    private final ObjectMapper withModule = JsonMapper.builder()
            .addModule(new SealedPolymorphismModule().registerSealedInterfaceOrClass(Beacon.class))
            .build();
    private final ObjectMapper vanilla = JsonMapper.builder().build();

    private void agreesWithPlainJackson(Object value) {
        assertThat(withModule.writeValueAsString(value)).isEqualTo(vanilla.writeValueAsString(value));
    }

    @Test
    void writesAnEnumMemberOfAMarkedHierarchyAsPlainJacksonDoes() {
        agreesWithPlainJackson(new SignalHolder(Status.IDLE));
        agreesWithPlainJackson(new StatusHolder(Status.BUSY));
        agreesWithPlainJackson(Status.IDLE);
        assertThat(withModule.writeValueAsString(new SignalHolder(Status.IDLE))).isEqualTo("{\"signal\":\"IDLE\"}");
    }

    @Test
    void writesAnEnumMemberOfARegisteredHierarchyAsPlainJacksonDoes() {
        agreesWithPlainJackson(new BeaconHolder(Colour.RED));
        agreesWithPlainJackson(Colour.GREEN);
        assertThat(withModule.writeValueAsString(new BeaconHolder(Colour.RED))).isEqualTo("{\"beacon\":\"RED\"}");
    }

    /**
     * Constants with bodies compile to anonymous subclasses, and such an enum is implicitly sealed
     * and abstract - so it reaches every check this module makes about sealed types.
     */
    @Test
    void writesAnEnumWithConstantBodiesAsPlainJacksonDoes() {
        agreesWithPlainJackson(new GaugeHolder(Level.LOW));
        agreesWithPlainJackson(new LevelHolder(Level.HIGH));
        assertThat(withModule.writeValueAsString(new GaugeHolder(Level.LOW))).isEqualTo("{\"gauge\":\"LOW\"}");
        // the constant's own class is not the enum class, and neither is tagged
        assertThat(Level.LOW.getClass()).isNotEqualTo(Level.class);
        assertThat(Level.class.isSealed()).isTrue();
    }

    @Test
    void keepsACustomEnumRepresentation() {
        agreesWithPlainJackson(new ModeHolder(Mode.FAST));
        assertThat(withModule.writeValueAsString(new ModeHolder(Mode.FAST))).isEqualTo("{\"mode\":\"fast\"}");
        assertThat(withModule.writeValueAsString(new SignalHolder(Mode.SLOW))).isEqualTo("{\"signal\":\"slow\"}");
    }

    @Test
    void writesEnumsInCollectionsAndAsMapKeysAsPlainJacksonDoes() {
        agreesWithPlainJackson(Map.of("a", Status.IDLE));
        agreesWithPlainJackson(List.of(Status.IDLE, Status.BUSY));
        agreesWithPlainJackson(Map.of(Status.IDLE, "x"));
        assertThat(withModule.writeValueAsString(Map.of(Status.IDLE, "x"))).isEqualTo("{\"IDLE\":\"x\"}");
    }

    @Test
    void readsEnumsBackWhenDeclaredAsTheirOwnType() {
        assertThat(withModule.readValue("{\"status\":\"BUSY\"}", StatusHolder.class).status()).isSameAs(Status.BUSY);
        assertThat(withModule.readValue("{\"level\":\"HIGH\"}", LevelHolder.class).level()).isSameAs(Level.HIGH);
        assertThat(withModule.readValue("{\"mode\":\"fast\"}", ModeHolder.class).mode()).isSameAs(Mode.FAST);
        assertThat(withModule.readValue("\"IDLE\"", Status.class)).isSameAs(Status.IDLE);
    }

    /** A non-enum sibling in the same hierarchy is still tagged and still round trips. */
    @Test
    void stillTagsTheNonEnumMembersOfTheSameHierarchy() {
        assertThat(withModule.writeValueAsString(new GaugeHolder(new Reading(4))))
                .isEqualTo("{\"gauge\":{\"@type\":\"Reading\",\"v\":4}}");
        Gauge gauge = withModule.readValue("{\"gauge\":{\"@type\":\"Reading\",\"v\":4}}", GaugeHolder.class).gauge();
        assertThat(gauge).isEqualTo(new Reading(4));
        assertThat(Signal.class).isNotNull();
    }
}
