package com.github.pjfanning.jackson.sealed.poly;

import com.github.pjfanning.jackson.sealed.SealedPolymorphismSupport;

/**
 * Hierarchies this module refuses to handle, each for a different reason. Ported from the Scala
 * module's UnreachableFixtures - where Scala can only infer that a hierarchy is not sealed, Java
 * reads it straight off the class file, so these are reported precisely.
 */
public final class InvalidFixtures {

    private InvalidFixtures() {
    }

    // marked, but not sealed at all, so an implementation may be declared anywhere on the classpath
    public interface Unsealed extends SealedPolymorphismSupport {
    }

    public record Nearby(int x) implements Unsealed {
    }

    public record UnsealedHolder(Unsealed u) {
    }

    // sealed at the root, but reopened part way down - a subclass of Reopened could never be
    // resolved back from a @type name
    public sealed interface Openable extends SealedPolymorphismSupport permits Reopened {
    }

    public static non-sealed class Reopened implements Openable {
        private final int x;

        public Reopened(int x) {
            this.x = x;
        }

        public int getX() {
            return x;
        }
    }

    public record OpenableHolder(Openable o) {
    }

    // two implementations whose derived names collide - one nested in the root, one beside it
    public sealed interface Shadow extends SealedPolymorphismSupport permits Shadow.Entry, Entry {
        record Entry(int a) implements Shadow {
        }
    }

    public record Entry(String b) implements Shadow {
    }

    public record ShadowHolder(Shadow s) {
    }

    // an enum marked directly, rather than as a member of a marked hierarchy
    public enum MarkedEnum implements SealedPolymorphismSupport {
        ONE, TWO
    }

    public record MarkedEnumHolder(MarkedEnum e) {
    }
}
