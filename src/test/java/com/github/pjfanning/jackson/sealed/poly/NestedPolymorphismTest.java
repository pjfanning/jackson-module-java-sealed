package com.github.pjfanning.jackson.sealed.poly;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.github.pjfanning.jackson.sealed.SealedPolymorphismModule;
import com.github.pjfanning.jackson.sealed.poly.NestedFixtures.AnnInnerA;
import com.github.pjfanning.jackson.sealed.poly.NestedFixtures.AnnInnerB;
import com.github.pjfanning.jackson.sealed.poly.NestedFixtures.AnnOuterA;
import com.github.pjfanning.jackson.sealed.poly.NestedFixtures.AnnOuterB;
import com.github.pjfanning.jackson.sealed.poly.NestedFixtures.AnnOuterHolder;
import com.github.pjfanning.jackson.sealed.poly.NestedFixtures.InnerA;
import com.github.pjfanning.jackson.sealed.poly.NestedFixtures.InnerB;
import com.github.pjfanning.jackson.sealed.poly.NestedFixtures.NestHolder;
import com.github.pjfanning.jackson.sealed.poly.NestedFixtures.OuterA;
import com.github.pjfanning.jackson.sealed.poly.NestedFixtures.OuterB;
import com.github.pjfanning.jackson.sealed.poly.NestedFixtures.OuterC;
import com.github.pjfanning.jackson.sealed.poly.NestedFixtures.OuterNest;
import com.github.pjfanning.jackson.sealed.poly.NestedFixtures.PlainOuterA;
import com.github.pjfanning.jackson.sealed.poly.NestedFixtures.PlainOuterB;
import com.github.pjfanning.jackson.sealed.poly.NestedFixtures.PlainOuterHolder;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * A polymorphic value holding a polymorphic value, with each of the two hierarchies handled by this
 * module or by Jackson's own annotations. Ported from jackson-module-scala's
 * {@code NestedPolymorphismSpec}.
 */
class NestedPolymorphismTest {

    private final ObjectMapper mapper = JsonMapper.builder().addModule(new SealedPolymorphismModule()).build();

    private <T> T roundTrip(T value, Class<T> clazz) {
        return mapper.readValue(mapper.writeValueAsString(value), clazz);
    }

    @Test
    void tagsBothLevelsWhenBothHierarchiesAreMarked() {
        assertThat(mapper.writeValueAsString(new NestHolder(new OuterA(new InnerA(1)))))
                .isEqualTo("{\"outer\":{\"@type\":\"OuterA\",\"inner\":{\"@type\":\"InnerA\",\"a\":1}}}");
    }

    @Test
    void tagsBothLevelsWhenTheInnerValueCarriesNoState() {
        assertThat(mapper.writeValueAsString(new NestHolder(new OuterA(new InnerB()))))
                .isEqualTo("{\"outer\":{\"@type\":\"OuterA\",\"inner\":{\"@type\":\"InnerB\"}}}");
    }

    @Test
    void roundTripsBothHierarchiesWhenBothAreMarked() {
        assertThat(roundTrip(new NestHolder(new OuterA(new InnerA(1))), NestHolder.class))
                .isEqualTo(new NestHolder(new OuterA(new InnerA(1))));
        assertThat(roundTrip(new NestHolder(new OuterA(new InnerB())), NestHolder.class).outer())
                .isEqualTo(new OuterA(new InnerB()));
        assertThat(roundTrip(new NestHolder(new OuterB()), NestHolder.class).outer())
                .isEqualTo(new OuterB());
    }

    @Test
    void tagsEveryElementOfACollectionOfTheInnerHierarchy() {
        NestHolder value = new NestHolder(new OuterC(List.of(new InnerA(1), new InnerB())));
        assertThat(mapper.writeValueAsString(value)).isEqualTo(
                "{\"outer\":{\"@type\":\"OuterC\",\"inners\":[{\"@type\":\"InnerA\",\"a\":1},{\"@type\":\"InnerB\"}]}}");
        assertThat(roundTrip(value, NestHolder.class)).isEqualTo(value);
    }

    @Test
    void tagsAHierarchyNestedInsideItself() {
        NestHolder value = new NestHolder(new OuterNest(new OuterNest(new OuterA(new InnerA(1)))));
        assertThat(mapper.writeValueAsString(value)).isEqualTo("{\"outer\":{\"@type\":\"OuterNest\",\"next\":"
                + "{\"@type\":\"OuterNest\",\"next\":{\"@type\":\"OuterA\",\"inner\":{\"@type\":\"InnerA\",\"a\":1}}}}}");
        assertThat(roundTrip(value, NestHolder.class)).isEqualTo(value);
    }

    @Test
    void letsJacksonOwnTheOuterHierarchyAndKeepsTheInnerOne() {
        assertThat(mapper.writeValueAsString(new AnnOuterHolder(new AnnOuterA(new InnerA(1)))))
                .isEqualTo("{\"outer\":{\"kind\":\"AnnOuterA\",\"inner\":{\"@type\":\"InnerA\",\"a\":1}}}");
    }

    @Test
    void roundTripsWhenJacksonOwnsOnlyTheOuterHierarchy() {
        assertThat(roundTrip(new AnnOuterHolder(new AnnOuterA(new InnerA(1))), AnnOuterHolder.class))
                .isEqualTo(new AnnOuterHolder(new AnnOuterA(new InnerA(1))));
        assertThat(roundTrip(new AnnOuterHolder(new AnnOuterA(new InnerB())), AnnOuterHolder.class))
                .isEqualTo(new AnnOuterHolder(new AnnOuterA(new InnerB())));
        assertThat(roundTrip(new AnnOuterHolder(new AnnOuterB("x")), AnnOuterHolder.class))
                .isEqualTo(new AnnOuterHolder(new AnnOuterB("x")));
    }

    @Test
    void letsJacksonOwnTheInnerHierarchyAndKeepsTheOuterOne() {
        assertThat(mapper.writeValueAsString(new PlainOuterHolder(new PlainOuterA(new AnnInnerA(1)))))
                .isEqualTo("{\"outer\":{\"@type\":\"PlainOuterA\",\"inner\":{\"kind\":\"AnnInnerA\",\"a\":1}}}");
    }

    @Test
    void roundTripsWhenJacksonOwnsOnlyTheInnerHierarchy() {
        assertThat(roundTrip(new PlainOuterHolder(new PlainOuterA(new AnnInnerA(1))), PlainOuterHolder.class))
                .isEqualTo(new PlainOuterHolder(new PlainOuterA(new AnnInnerA(1))));
        assertThat(roundTrip(new PlainOuterHolder(new PlainOuterA(new AnnInnerB("b"))), PlainOuterHolder.class))
                .isEqualTo(new PlainOuterHolder(new PlainOuterA(new AnnInnerB("b"))));
        assertThat(roundTrip(new PlainOuterHolder(new PlainOuterB()), PlainOuterHolder.class).outer())
                .isEqualTo(new PlainOuterB());
    }
}
