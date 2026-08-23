package com.github.pjfanning.jackson.sealed;

import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * The implementations of one marked hierarchy, and the {@code @type} name each is written under.
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
 */
final class SealedHierarchy {

    private final Class<?> root;
    /** True when the root carries {@code @JsonTypeInfo}, so Jackson's own handling owns it. */
    private final boolean jacksonOwned;
    private final Map<String, Subtype> byName;
    private final Map<Class<?>, String> namesByClass;

    private SealedHierarchy(Class<?> root, boolean jacksonOwned,
                            Map<String, Subtype> byName, Map<Class<?>, String> namesByClass) {
        this.root = root;
        this.jacksonOwned = jacksonOwned;
        this.byName = byName;
        this.namesByClass = namesByClass;
    }

    static SealedHierarchy of(Class<?> root) {
        if (root.getAnnotation(JsonTypeInfo.class) != null) {
            return new SealedHierarchy(root, true, Map.of(), Map.of());
        }
        if (SealedTypes.enumClassOf(root) != null) {
            throw new IllegalArgumentException(root.getName() + " is an enum marked with "
                    + SealedPolymorphismSupport.class.getSimpleName() + ". An enum is not a hierarchy of its own; "
                    + "mark the sealed interface it implements instead, and its constants are named "
                    + "individually within that hierarchy.");
        }
        if (!root.isSealed()) {
            throw new IllegalArgumentException(root.getName() + " is marked with "
                    + SealedPolymorphismSupport.class.getSimpleName() + " but is not sealed. Only sealed "
                    + "hierarchies are supported: declare " + root.getSimpleName() + " as `sealed`, and each of "
                    + "its permitted subtypes as `final` or `sealed` in turn.");
        }

        Map<String, Subtype> byName = new HashMap<>();
        Map<Class<?>, String> namesByClass = new HashMap<>();
        collect(root, root, byName, namesByClass, new HashSet<>());
        return new SealedHierarchy(root, false, Map.copyOf(byName), Map.copyOf(namesByClass));
    }

    private static void collect(Class<?> root, Class<?> current, Map<String, Subtype> byName,
                                Map<Class<?>, String> namesByClass, Set<Class<?>> seen) {
        Deque<Class<?>> queue = new ArrayDeque<>();
        queue.add(current);
        while (!queue.isEmpty()) {
            Class<?> clazz = queue.poll();
            if (!seen.add(clazz)) {
                continue;
            }
            Class<?> enumClass = SealedTypes.enumClassOf(clazz);
            if (enumClass != null) {
                // an enum is closed by construction, and each constant is a value of the hierarchy in
                // its own right - the Java counterpart of the Scala module's `case object`
                String prefix = SealedTypes.typeNameFor(root, enumClass);
                for (Object constant : enumClass.getEnumConstants()) {
                    Enum<?> value = (Enum<?>) constant;
                    register(root, byName, prefix + '$' + value.name(), Subtype.ofConstant(value), value.getClass());
                }
                continue;
            }

            int modifiers = clazz.getModifiers();
            boolean sealed = clazz.isSealed();
            if (!sealed && !Modifier.isFinal(modifiers)) {
                throw new IllegalArgumentException(clazz.getName() + " belongs to the "
                        + SealedPolymorphismSupport.class.getSimpleName() + " hierarchy rooted at " + root.getName()
                        + ", but is neither sealed nor final. A `non-sealed` type reopens the hierarchy, so its "
                        + "subclasses could not be resolved back from a " + SealedTypes.TYPE_PROPERTY_NAME
                        + " name; declare " + clazz.getSimpleName() + " as `final` or `sealed`.");
            }
            // an interface or abstract class is only ever dispatched through, so carries no name of its own
            if (SealedTypes.isConcrete(clazz)) {
                String name = SealedTypes.typeNameFor(root, clazz);
                register(root, byName, name, Subtype.ofClass(clazz), clazz);
                namesByClass.put(clazz, name);
            }
            if (sealed) {
                queue.addAll(java.util.Arrays.asList(clazz.getPermittedSubclasses()));
            }
        }
    }

    private static void register(Class<?> root, Map<String, Subtype> byName, String name, Subtype subtype,
                                 Class<?> declaring) {
        Subtype existing = byName.putIfAbsent(name, subtype);
        if (existing != null && !existing.equals(subtype)) {
            throw new IllegalArgumentException(declaring.getName() + " is written as "
                    + SealedTypes.TYPE_PROPERTY_NAME + " '" + name + "', but that name already belongs to "
                    + existing.type().getName() + " in the hierarchy rooted at " + root.getName()
                    + ". Rename one of them so that the two derive different names.");
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
    Subtype resolve(Class<?> baseClass, String typeName) {
        if (typeName == null) {
            return null;
        }
        Subtype subtype = byName.get(typeName);
        return (subtype != null && baseClass.isAssignableFrom(subtype.type())) ? subtype : null;
    }

    /** The {@code @type} name for a concrete implementation. */
    String nameOf(Class<?> clazz) {
        String name = namesByClass.get(clazz);
        if (name == null) {
            throw new IllegalArgumentException(clazz.getName() + " is not a permitted implementation of the "
                    + SealedPolymorphismSupport.class.getSimpleName() + " hierarchy rooted at " + root.getName() + ".");
        }
        return name;
    }

    /** The {@code @type} name of an enum constant of this hierarchy. */
    String nameOfConstant(Enum<?> constant) {
        return SealedTypes.typeNameFor(root, constant.getDeclaringClass()) + '$' + constant.name();
    }
}
