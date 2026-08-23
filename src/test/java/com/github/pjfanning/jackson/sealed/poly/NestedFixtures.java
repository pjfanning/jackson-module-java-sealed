package com.github.pjfanning.jackson.sealed.poly;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.pjfanning.jackson.sealed.SealedPolymorphismSupport;

/** A polymorphic value holding a polymorphic value, in every combination of who owns which level. */
public final class NestedFixtures {

    private NestedFixtures() {
    }

    // the inner hierarchy, marked
    public sealed interface Inner extends SealedPolymorphismSupport permits InnerA, InnerB {
    }

    public record InnerA(int a) implements Inner {
    }

    public record InnerB() implements Inner {
    }

    // both hierarchies marked - a polymorphic value holding a polymorphic value
    public sealed interface Outer extends SealedPolymorphismSupport
            permits OuterA, OuterB, OuterC, OuterNest {
    }

    public record OuterA(Inner inner) implements Outer {
    }

    public record OuterB() implements Outer {
    }

    public record OuterC(List<Inner> inners) implements Outer {
    }

    // the hierarchy nests inside itself
    public record OuterNest(Outer next) implements Outer {
    }

    public record NestHolder(Outer outer) {
    }

    // the outer hierarchy is annotated as well as marked, so Jackson owns it - the inner one is
    // still ours
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = AnnOuterA.class, name = "AnnOuterA"),
            @JsonSubTypes.Type(value = AnnOuterB.class, name = "AnnOuterB")
    })
    public sealed interface AnnOuter extends SealedPolymorphismSupport permits AnnOuterA, AnnOuterB {
    }

    public record AnnOuterA(Inner inner) implements AnnOuter {
    }

    public record AnnOuterB(String label) implements AnnOuter {
    }

    public record AnnOuterHolder(AnnOuter outer) {
    }

    // and the other way round - Jackson owns the inner hierarchy, we own the outer one
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = AnnInnerA.class, name = "AnnInnerA"),
            @JsonSubTypes.Type(value = AnnInnerB.class, name = "AnnInnerB")
    })
    public sealed interface AnnInner extends SealedPolymorphismSupport permits AnnInnerA, AnnInnerB {
    }

    public record AnnInnerA(int a) implements AnnInner {
    }

    public record AnnInnerB(String b) implements AnnInner {
    }

    public sealed interface PlainOuter extends SealedPolymorphismSupport permits PlainOuterA, PlainOuterB {
    }

    public record PlainOuterA(AnnInner inner) implements PlainOuter {
    }

    public record PlainOuterB() implements PlainOuter {
    }

    public record PlainOuterHolder(PlainOuter outer) {
    }
}
