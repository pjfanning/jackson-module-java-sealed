package com.github.pjfanning.jackson.sealed.poly;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.pjfanning.jackson.sealed.SealedPolymorphismSupport;

/**
 * The hierarchies exercised by {@link SealedPolymorphismTest}, ported from the fixtures of
 * jackson-module-scala's SealedPolymorphismSupport PR.
 *
 * <p>Everything is nested in this holder so that the derived {@code @type} names line up with the
 * Scala ones: the holder is stripped as a prefix shared with each hierarchy's root, exactly as a
 * package is.
 */
public final class Fixtures {

    private Fixtures() {
    }

    // implementations declared alongside the base type
    public sealed interface Animal extends SealedPolymorphismSupport permits Dog, Bird, Unknown {
    }

    public record Dog(String name) implements Animal {
    }

    public record Bird(String name, boolean canFly) implements Animal {
    }

    /** The Java counterpart of a Scala {@code case object}: one value, carrying no state. */
    public record Unknown() implements Animal {
    }

    public record Owner(String name, Animal pet) {
    }

    public record MaybeOwner(String name, Optional<Animal> pet) {
    }

    public record Zoo(Map<String, Animal> animals) {
    }

    public record Shelter(List<Animal> animals) {
    }

    // a sealed abstract class base, with behaviour of its own - records cannot extend a class, so
    // the implementations here are ordinary final classes
    public abstract static sealed class Shape implements SealedPolymorphismSupport permits Rect, Point {
        public abstract int sides();
    }

    public static final class Rect extends Shape {
        private final double width;
        private final double height;

        public Rect(double width, double height) {
            this.width = width;
            this.height = height;
        }

        public double getWidth() {
            return width;
        }

        public double getHeight() {
            return height;
        }

        @Override
        public int sides() {
            return 4;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Rect other && other.width == width && other.height == height;
        }

        @Override
        public int hashCode() {
            return Objects.hash(width, height);
        }
    }

    public static final class Point extends Shape {
        @Override
        public int sides() {
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Point;
        }

        @Override
        public int hashCode() {
            return Point.class.hashCode();
        }
    }

    public record Drawing(Shape shape) {
    }

    // implementations declared inside the base type itself - the Java counterpart of Scala's
    // companion object
    public sealed interface Payment extends SealedPolymorphismSupport permits Payment.Card, Payment.Cash {
        record Card(String last4) implements Payment {
        }

        record Cash() implements Payment {
        }
    }

    public record Order(int total, Payment payment) {
    }

    // base type and implementations both nested inside an unrelated class
    public static final class Wrapper {
        private Wrapper() {
        }

        public sealed interface Event extends SealedPolymorphismSupport permits Created, Deleted {
        }

        public record Created(int id) implements Event {
        }

        public record Deleted() implements Event {
        }
    }

    public record Envelope(Wrapper.Event event) {
    }

    // a marked hierarchy that also carries Jackson's own annotations - they win
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    @JsonSubTypes(@JsonSubTypes.Type(value = Cheque.class, name = "Cheque"))
    public sealed interface Annotated extends SealedPolymorphismSupport permits Cheque {
    }

    public record Cheque(int number) implements Annotated {
    }

    public record Ledger(Annotated entry) {
    }

    // the annotation sits on one implementation rather than on the base - it governs that
    // implementation's own subtypes and must not switch off tagging for the hierarchy
    public sealed interface Leafy extends SealedPolymorphismSupport permits LeafA, LeafB {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    public record LeafA(int x) implements Leafy {
    }

    public record LeafB(int y) implements Leafy {
    }

    public record LeafHolder(Leafy leaf) {
    }

    // implementations grouped into classes that do not enclose the base - each keeps its enclosing
    // class in the derived name, so two of them can hold an implementation of the same name
    public sealed interface Dup extends SealedPolymorphismSupport
            permits FirstGroup.Same, FirstGroup.Only, SecondGroup.Same {
    }

    public static final class FirstGroup {
        private FirstGroup() {
        }

        public record Same(int v) implements Dup {
        }

        public record Only() implements Dup {
        }
    }

    public static final class SecondGroup {
        private SecondGroup() {
        }

        public record Same(String v) implements Dup {
        }
    }

    public record DupHolder(Dup d) {
    }

    // a concrete sealed class - a value in its own right, and a base its subclasses are read through
    public static sealed class Node implements SealedPolymorphismSupport permits Branch {
        private final int id;

        public Node(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }

    // sealed at every level, so the hierarchy stays closed all the way down
    public static sealed class Branch extends Node permits Twig {
        private final String label;

        public Branch(int id, String label) {
            super(id);
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    // concrete, and part way down the hierarchy rather than at the top
    public static final class Twig extends Branch {
        private final int length;

        public Twig(int id, String label, int length) {
            super(id, label);
            this.length = length;
        }

        public int getLength() {
            return length;
        }
    }

    public record Tree(Node node) {
    }

    public record Limb(Branch branch) {
    }

    // an enum member of a marked hierarchy - the closest Java has to a set of Scala case objects,
    // so each constant is named individually rather than the enum class as a whole
    public sealed interface Signal extends SealedPolymorphismSupport permits Data, Status, Mode {
    }

    public record Data(int value) implements Signal {
    }

    public enum Status implements Signal {
        IDLE, BUSY
    }

    public record SignalHolder(Signal signal) {
    }

    public record StatusHolder(Status status) {
    }

    // an enum whose constants have bodies, so it is implicitly sealed and abstract, and each
    // constant compiles to an anonymous subclass - the shape most likely to trip a module that
    // walks permitted subclasses
    public sealed interface Gauge extends SealedPolymorphismSupport permits Reading, Level {
    }

    public record Reading(int v) implements Gauge {
    }

    public enum Level implements Gauge {
        LOW {
            @Override
            public int weight() {
                return 1;
            }
        },
        HIGH {
            @Override
            public int weight() {
                return 9;
            }
        };

        public abstract int weight();
    }

    public record GaugeHolder(Gauge gauge) {
    }

    public record LevelHolder(Level level) {
    }

    // an enum with a custom JSON representation, to check the module does not override it
    public enum Mode implements Signal {
        FAST, SLOW;

        @com.fasterxml.jackson.annotation.JsonValue
        public String json() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record ModeHolder(Mode mode) {
    }

    // `$` is legal in a Java identifier, so a class can carry one that is not nesting and must
    // survive intact - the counterpart of Scala compiling `::` to `$colon$colon`
    public sealed interface Expr extends SealedPolymorphismSupport permits Lit, Odd$Name, Grouped.Inner {
    }

    public record Lit(int v) implements Expr {
    }

    @SuppressWarnings("checkstyle:TypeName")
    public record Odd$Name(int head, int tail) implements Expr {
    }

    public static final class Grouped {
        private Grouped() {
        }

        public record Inner(int v) implements Expr {
        }
    }

    public record ExprHolder(Expr expr) {
    }

    // a hierarchy that is not marked - it must be untouched
    public sealed interface Plain permits PlainDog {
    }

    public record PlainDog(String name) implements Plain {
    }
}
