package com.github.pjfanning.jackson.sealed.poly;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed hierarchies that do <em>not</em> extend {@code SealedPolymorphismSupport}, standing in for
 * types whose source cannot be changed - from a library, or generated. They opt in by being
 * registered with the module instead.
 *
 * <p>Nothing here imports the module, which is the point: these are ordinary sealed types.
 */
public final class UnmarkedFixtures {

    private UnmarkedFixtures() {
    }

    public sealed interface Vehicle permits Car, Bike, Scooter {
    }

    public record Car(String plate) implements Vehicle {
    }

    public record Bike(int gears) implements Vehicle {
    }

    public record Scooter() implements Vehicle {
    }

    public record Garage(String name, Vehicle vehicle) {
    }

    // implementations grouped into classes that do not enclose the root, to check the naming rules
    // apply to a registered hierarchy exactly as to a marked one
    public sealed interface Cargo permits Hold.Item, Deck.Item {
    }

    public static final class Hold {
        private Hold() {
        }

        public record Item(int n) implements Cargo {
        }
    }

    public static final class Deck {
        private Deck() {
        }

        public record Item(String s) implements Cargo {
        }
    }

    public record Manifest(Cargo cargo) {
    }

    // three levels, so a type part way down can be registered as the root
    public sealed interface Tier1 permits Tier2 {
    }

    public sealed interface Tier2 extends Tier1 permits Tier3A, Tier3B {
    }

    public record Tier3A(int a) implements Tier2 {
    }

    public record Tier3B(String b) implements Tier2 {
    }

    public record TierHolder(Tier2 value) {
    }

    // an enum member of an unmarked hierarchy
    public sealed interface Beacon permits Pulse, Colour {
    }

    public record Pulse(int hz) implements Beacon {
    }

    public enum Colour implements Beacon {
        RED, GREEN
    }

    public record BeaconHolder(Beacon beacon) {
    }

    // a concrete sealed root, which is both a value and a base
    public static sealed class Crate permits BigCrate {
        private final int size;

        public Crate(int size) {
            this.size = size;
        }

        public int getSize() {
            return size;
        }
    }

    public static final class BigCrate extends Crate {
        private final String label;

        public BigCrate(int size, String label) {
            super(size);
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public record Shipment(Crate crate) {
    }

    // sealed, but Jackson's own annotations already handle it - registering it must be refused
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    @JsonSubTypes(@JsonSubTypes.Type(value = Signed.class, name = "Signed"))
    public sealed interface Stamped permits Signed {
    }

    public record Signed(int id) implements Stamped {
    }

    // not sealed at all - registering it must be refused
    public interface Loose {
    }

    public record LooseImpl(int x) implements Loose {
    }

    // sealed, but reopened below - registering is fine, using it is not
    public sealed interface Ajar permits Propped {
    }

    public static non-sealed class Propped implements Ajar {
        private final int x;

        public Propped(int x) {
            this.x = x;
        }

        public int getX() {
            return x;
        }
    }

    public record AjarHolder(Ajar ajar) {
    }
}
