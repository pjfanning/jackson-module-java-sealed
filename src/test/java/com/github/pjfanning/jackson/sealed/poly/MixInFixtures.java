package com.github.pjfanning.jackson.sealed.poly;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed hierarchies whose classes cannot be changed - no marker anywhere on them, and nothing here
 * referring to this module at all. They opt in through a mix-in registered on the mapper.
 */
public final class MixInFixtures {

    private MixInFixtures() {
    }

    public sealed interface Vehicle permits Car, Truck, Bike {
    }

    public record Car(int wheels) implements Vehicle {
    }

    public record Truck(int axles, String load) implements Vehicle {
    }

    public record Bike() implements Vehicle {
    }

    public record Garage(Vehicle vehicle) {
    }

    // implementations grouped into classes that do not enclose the root, to check the naming rules
    // apply to a mixed-in hierarchy exactly as to a marked one
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

    // three levels, so a mix-in can be put on a type part way down
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

    // an enum member, which stays Jackson's however the hierarchy opted in
    public sealed interface Beacon permits Pulse, Colour {
    }

    public record Pulse(int hz) implements Beacon {
    }

    public enum Colour implements Beacon {
        RED, GREEN
    }

    public record BeaconHolder(Beacon beacon) {
    }

    // not sealed at all - mixing the marker into it must be refused
    public interface Loose {
    }

    public record LooseImpl(int x) implements Loose {
    }

    public record LooseHolder(Loose loose) {
    }

    // sealed, but reopened below the root - mixing in does not relax that
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

    // Jackson's own annotations already handle this one
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    @JsonSubTypes(@JsonSubTypes.Type(value = Signed.class, name = "Signed"))
    public sealed interface Stamped permits Signed {
    }

    public record Signed(int id) implements Stamped {
    }

    public record StampedHolder(Stamped stamped) {
    }
}
