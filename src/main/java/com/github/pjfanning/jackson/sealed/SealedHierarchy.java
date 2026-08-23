package com.github.pjfanning.jackson.sealed;

import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * The implementations of one hierarchy, and the {@code @type} name each is written under.
 *
 * <p>Built once per hierarchy root by walking the {@code PermittedSubclasses} attribute that
 * {@code javac} records for every {@code sealed} type. The result is an exact, closed table: a name
 * either belongs to this hierarchy or it does not resolve at all. This is what a Java port can do
 * and the Scala module cannot - scalac leaves no trace of {@code sealed} on the JVM, so
 * jackson-module-scala has to rebuild candidate class names from where the base is declared and
 * filter them by subtype relationship.
 *
 * <p>Building the table is also where the hierarchy is checked to be closed, so a type that could
 * not be read back is reported before anything is written.
 *
 * <p>Enum members are left out. Jackson writes an enum as a string, and this module does not take
 * that over, so an enum permitted by the root carries no {@code @type} name of its own.
 */
final class SealedHierarchy {

    private final Class<?> root;
    /** True when the root carries {@code @JsonTypeInfo}, so Jackson's own handling owns it. */
    private final boolean jacksonOwned;
    private final Map<String, Class<?>> byName;
    private final Map<Class<?>, String> namesByClass;
    /** Permitted enums, kept only so that a value of one can be explained when it cannot be read. */
    private final Set<Class<?>> enumMembers;

    private SealedHierarchy(Class<?> root, boolean jacksonOwned, Map<String, Class<?>> byName,
                            Map<Class<?>, String> namesByClass, Set<Class<?>> enumMembers) {
        this.root = root;
        this.jacksonOwned = jacksonOwned;
        this.byName = byName;
        this.namesByClass = namesByClass;
        this.enumMembers = enumMembers;
    }

    static SealedHierarchy of(Class<?> root) {
        if (root.getAnnotation(JsonTypeInfo.class) != null) {
            return new SealedHierarchy(root, true, Map.of(), Map.of(), Set.of());
        }
        if (!root.isSealed()) {
            throw new IllegalArgumentException(root.getName() + " is marked with "
                    + SealedPolymorphismSupport.class.getSimpleName() + " but is not sealed. Only sealed "
                    + "hierarchies are supported: declare " + root.getSimpleName() + " as `sealed`, and each of "
                    + "its permitted subtypes as `final` or `sealed` in turn.");
        }

        Map<String, Class<?>> byName = new HashMap<>();
        Map<Class<?>, String> namesByClass = new HashMap<>();
        Set<Class<?>> enumMembers = new LinkedHashSet<>();
        collect(root, byName, namesByClass, enumMembers);
        // Set.copyOf does not keep insertion order, and this set is rendered into an error message
        return new SealedHierarchy(root, false, Map.copyOf(byName), Map.copyOf(namesByClass),
                Collections.unmodifiableSet(enumMembers));
    }

    private static void collect(Class<?> root, Map<String, Class<?>> byName, Map<Class<?>, String> namesByClass,
                                Set<Class<?>> enumMembers) {
        Set<Class<?>> seen = new HashSet<>();
        Deque<Class<?>> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Class<?> clazz = queue.poll();
            if (!seen.add(clazz)) {
                continue;
            }
            // an enum is Jackson's to write, as a string - it takes no name here, and its constant
            // bodies are not part of this hierarchy either
            Class<?> enumClass = SealedTypes.enumClassOf(clazz);
            if (enumClass != null) {
                enumMembers.add(enumClass);
                continue;
            }

            boolean sealed = clazz.isSealed();
            if (!sealed && !Modifier.isFinal(clazz.getModifiers())) {
                throw new IllegalArgumentException(clazz.getName() + " belongs to the sealed hierarchy rooted at "
                        + root.getName() + ", but is neither sealed nor final. A `non-sealed` type reopens the "
                        + "hierarchy, so its subclasses could not be resolved back from a "
                        + SealedTypes.TYPE_PROPERTY_NAME + " name; declare " + clazz.getSimpleName()
                        + " as `final` or `sealed`.");
            }
            // an interface or abstract class is only ever dispatched through, so carries no name of its own
            if (SealedTypes.isConcrete(clazz)) {
                String name = SealedTypes.typeNameFor(root, clazz);
                Class<?> existing = byName.putIfAbsent(name, clazz);
                if (existing != null && existing != clazz) {
                    throw new IllegalArgumentException(clazz.getName() + " is written as "
                            + SealedTypes.TYPE_PROPERTY_NAME + " '" + name + "', but that name already belongs to "
                            + existing.getName() + " in the hierarchy rooted at " + root.getName()
                            + ". Rename one of them so that the two derive different names.");
                }
                namesByClass.put(clazz, name);
            }
            if (sealed) {
                queue.addAll(Arrays.asList(clazz.getPermittedSubclasses()));
            }
        }
    }

    Class<?> root() {
        return root;
    }

    boolean isJacksonOwned() {
        return jacksonOwned;
    }

    /**
     * Resolves a {@code @type} name to an implementation assignable to {@code baseClass}, or
     * {@code null} if this hierarchy has no such name.
     *
     * <p>Names are held for the hierarchy as a whole, so that a property declared at an intermediate
     * type still reads the names written for the hierarchy it belongs to. The result is narrowed to
     * {@code baseClass} here, so a name from a sibling branch does not resolve into a property that
     * could not hold it.
     */
    Class<?> resolve(Class<?> baseClass, String typeName) {
        if (typeName == null) {
            return null;
        }
        Class<?> subtype = byName.get(typeName);
        return (subtype != null && baseClass.isAssignableFrom(subtype)) ? subtype : null;
    }

    /**
     * Explains that a value read at this hierarchy's base was not an object, when the reason is that
     * the hierarchy permits an enum - which Jackson writes as a string, and which therefore cannot
     * be dispatched on. Returns {@code null} when that is not the explanation.
     */
    String enumMemberHint() {
        if (enumMembers.isEmpty()) {
            return null;
        }
        return " The hierarchy permits " + enumMembers.stream().map(Class::getSimpleName)
                .collect(Collectors.joining(", ")) + ", which Jackson writes as a string rather than a tagged "
                + "object, so a value of that type cannot be read back through " + root.getSimpleName()
                + ". Declare the property as the enum type itself, or replace the enum with final classes.";
    }

    /** The {@code @type} name for a concrete implementation. */
    String nameOf(Class<?> clazz) {
        String name = namesByClass.get(clazz);
        if (name == null) {
            throw new IllegalArgumentException(clazz.getName() + " is not a permitted implementation of the sealed "
                    + "hierarchy rooted at " + root.getName() + ".");
        }
        return name;
    }
}
