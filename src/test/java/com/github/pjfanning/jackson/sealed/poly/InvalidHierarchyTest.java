package com.github.pjfanning.jackson.sealed.poly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Consumer;

import com.github.pjfanning.jackson.sealed.SealedPolymorphismModule;
import com.github.pjfanning.jackson.sealed.poly.InvalidFixtures.Entry;
import com.github.pjfanning.jackson.sealed.poly.InvalidFixtures.MarkedEnum;
import com.github.pjfanning.jackson.sealed.poly.InvalidFixtures.MarkedEnumHolder;
import com.github.pjfanning.jackson.sealed.poly.InvalidFixtures.Nearby;
import com.github.pjfanning.jackson.sealed.poly.InvalidFixtures.Openable;
import com.github.pjfanning.jackson.sealed.poly.InvalidFixtures.OpenableHolder;
import com.github.pjfanning.jackson.sealed.poly.InvalidFixtures.Reopened;
import com.github.pjfanning.jackson.sealed.poly.InvalidFixtures.Shadow;
import com.github.pjfanning.jackson.sealed.poly.InvalidFixtures.ShadowHolder;
import com.github.pjfanning.jackson.sealed.poly.InvalidFixtures.Unsealed;
import com.github.pjfanning.jackson.sealed.poly.InvalidFixtures.UnsealedHolder;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Hierarchies that are not closed, and so cannot be read back from a {@code @type} name. Each is
 * reported the first time Jackson meets it, rather than being written out as JSON that could never
 * be read.
 *
 * <p>The Scala module can only infer that a hierarchy is not sealed - it checks, as each serializer
 * is built, whether the implementation can be found again under the name it would be written with.
 * Java reads {@code sealed} straight off the class file, so each of these is reported for what it
 * actually is.
 */
class InvalidHierarchyTest {

    private final ObjectMapper mapper = JsonMapper.builder().addModule(new SealedPolymorphismModule()).build();

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error.getCause();
        return (cause == null || cause == error) ? error : rootCause(cause);
    }

    private static Consumer<Throwable> messageContaining(String... fragments) {
        return error -> assertThat(String.valueOf(rootCause(error).getMessage())).contains(fragments);
    }

    @Test
    void refusesToWriteAnImplementationOfAHierarchyThatIsNotSealed() {
        assertThatThrownBy(() -> mapper.writeValueAsString(new UnsealedHolder(new Nearby(1))))
                .satisfies(messageContaining("Unsealed", "is not sealed", "Only sealed hierarchies are supported"));
    }

    @Test
    void refusesToReadAHierarchyThatIsNotSealed() {
        assertThatThrownBy(() -> mapper.readValue("{\"@type\":\"Nearby\",\"x\":1}", Unsealed.class))
                .satisfies(messageContaining("Unsealed", "is not sealed"));
    }

    @Test
    void refusesAHierarchyReopenedByANonSealedMember() {
        assertThatThrownBy(() -> mapper.writeValueAsString(new OpenableHolder(new Reopened(1))))
                .satisfies(messageContaining("Reopened", "neither sealed nor final", "reopens the hierarchy"));
    }

    @Test
    void refusesToReadAHierarchyReopenedByANonSealedMember() {
        assertThatThrownBy(() -> mapper.readValue("{\"@type\":\"Reopened\",\"x\":1}", Openable.class))
                .satisfies(messageContaining("Reopened", "neither sealed nor final"));
    }

    @Test
    void refusesToWriteAnImplementationWhoseDerivedNameIsAlreadyTaken() {
        assertThatThrownBy(() -> mapper.writeValueAsString(new ShadowHolder(new Entry("x"))))
                .satisfies(messageContaining("already belongs to", "Entry"));
    }

    @Test
    void refusesToReadAHierarchyWithAClashingName() {
        assertThatThrownBy(() -> mapper.readValue("{\"@type\":\"Entry\",\"a\":1}", Shadow.class))
                .satisfies(messageContaining("already belongs to"));
    }

    /**
     * An enum carrying the marker is not an error, it is simply ignored - enums are Jackson's to
     * write however they are declared, so the marker has nothing to add and nothing to complain
     * about.
     */
    @Test
    void ignoresAnEnumMarkedDirectly() {
        assertThat(mapper.writeValueAsString(new MarkedEnumHolder(MarkedEnum.ONE)))
                .isEqualTo("{\"e\":\"ONE\"}");
        assertThat(mapper.readValue("{\"e\":\"TWO\"}", MarkedEnumHolder.class).e()).isSameAs(MarkedEnum.TWO);
    }

    /**
     * A hierarchy that failed to resolve must not be remembered as anything else. {@link ClassValue}
     * caches nothing when its computation throws, so the second attempt reports the same problem
     * rather than succeeding or failing differently.
     */
    @Test
    void reportsTheSameProblemEveryTime() {
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> mapper.writeValueAsString(new UnsealedHolder(new Nearby(1))))
                    .satisfies(messageContaining("is not sealed"));
        }
    }
}
