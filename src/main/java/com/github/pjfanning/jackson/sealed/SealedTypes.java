package com.github.pjfanning.jackson.sealed;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Naming rules and policy behind {@link SealedPolymorphismSupport}.
 *
 * <p>A hierarchy opts in either by extending the marker or by being registered with the module, so
 * which types are handled depends on how the module was configured - that part is per instance. The
 * name table of a hierarchy is derived from the class files alone and is the same however the
 * hierarchy opted in, so that part is cached globally.
 *
 * <p>Every path through the module asks {@link #isOptedIn} first, and a class that carries neither
 * the marker nor a registration answers in constant time, so an application's own types are
 * untouched.
 */
final class SealedTypes {

    /** Name of the JSON property that carries the derived type name. */
    static final String TYPE_PROPERTY_NAME = "@type";

    private static final Class<SealedPolymorphismSupport> MARKER = SealedPolymorphismSupport.class;

    /** Keyed by root, and independent of how that root opted in. */
    private static volatile ClassValue<SealedHierarchy> hierarchiesByRoot = newHierarchyCache();

    private volatile Set<Class<?>> registeredRoots = Set.of();
    private volatile ClassValue<Optional<Class<?>>> rootsByMember = newRootCache();

    /**
     * Registers a hierarchy root, to be handled as if it carried the marker. Registering the same
     * type twice is harmless.
     *
     * <p>Roots may be added at any point, so anything already worked out about which hierarchy a
     * type belongs to is discarded - a type that resolved to nothing, or to a root further down,
     * may now resolve differently.
     */
    synchronized void register(Class<?> root) {
        checkRegistrable(root);
        if (registeredRoots.contains(root)) {
            return;
        }
        Set<Class<?>> updated = new LinkedHashSet<>(registeredRoots);
        updated.add(root);
        registeredRoots = Collections.unmodifiableSet(updated);
        rootsByMember = newRootCache();
    }

    /**
     * Checks a type may be registered as a hierarchy root. Registration is the way in for a
     * hierarchy whose source cannot be changed to extend the marker, so it accepts anything the
     * marker would - but no more: the hierarchy still has to be sealed.
     */
    private static void checkRegistrable(Class<?> root) {
        if (root == null) {
            throw new IllegalArgumentException("Cannot register a null type as a sealed hierarchy root.");
        }
        if (enumClassOf(root) != null) {
            throw new IllegalArgumentException(root.getName() + " is an enum, so cannot be registered as a "
                    + "hierarchy root. Enums are left to Jackson, which writes them as strings. Register the sealed "
                    + "interface it implements instead - the enum itself stays a string either way.");
        }
        if (!root.isSealed()) {
            throw new IllegalArgumentException(root.getName() + " cannot be registered with "
                    + SealedPolymorphismModule.class.getSimpleName() + " because it is not sealed. Only sealed "
                    + "hierarchies are supported: registration replaces the " + MARKER.getSimpleName()
                    + " marker, not the requirement that the hierarchy be closed.");
        }
        if (root.getAnnotation(JsonTypeInfo.class) != null) {
            throw new IllegalArgumentException(root.getName() + " cannot be registered with "
                    + SealedPolymorphismModule.class.getSimpleName() + " because it carries @JsonTypeInfo. That "
                    + "annotation already tells Jackson how to write and read the hierarchy, so registering it here "
                    + "would ask for two type properties at once. Remove the annotation to use "
                    + TYPE_PROPERTY_NAME + ", or leave it and do not register the type.");
        }
    }

    /** The types registered with this module, in the order they were given. */
    Set<Class<?>> registeredRoots() {
        return registeredRoots;
    }

    /**
     * True for a type in a hierarchy this module handles, whether it opted in through the marker or
     * through registration.
     */
    boolean isOptedIn(Class<?> clazz) {
        return clazz != null && rootsByMember.get(clazz).isPresent();
    }

    /**
     * True for an opted-in type this module should handle. A hierarchy whose root carries
     * {@code @JsonTypeInfo} is left to Jackson's own polymorphic handling rather than being tagged
     * twice.
     */
    boolean isSupported(Class<?> clazz) {
        // an enum is Jackson's to write, as a string; this module does not take that over
        return isOptedIn(clazz) && enumClassOf(clazz) == null && !hierarchyOf(clazz).isJacksonOwned();
    }

    /**
     * True for a type that can only be dispatched on, never instantiated - an interface or abstract
     * class. Reading one means reading whatever its {@code @type} names.
     */
    boolean isBaseType(Class<?> clazz) {
        return isSupported(clazz) && !isConcrete(clazz);
    }

    /**
     * True for a concrete type that other implementations extend - {@code sealed class Node} is both
     * a value in its own right and a base its subclasses are read through. A property declared at
     * one of these has to dispatch rather than read straight through to the bean, or a subclass
     * would silently be read back as the type the property was declared as.
     */
    boolean needsSubtypeDispatch(Class<?> clazz) {
        return isSupported(clazz) && isConcrete(clazz) && clazz.isSealed();
    }

    /**
     * {@code @JsonTypeInfo} on an implementation rather than on the root cannot be honoured
     * alongside {@code @type}: Jackson treats the annotated class as a polymorphic base in its own
     * right, so reading it demands that annotation's type id, which nothing in a handled hierarchy
     * ever writes. The combination is reported rather than left to produce JSON that cannot be read
     * back.
     */
    void checkNoConflictingJsonTypeInfo(Class<?> clazz) {
        if (isSupported(clazz) && clazz.getAnnotation(JsonTypeInfo.class) != null) {
            Class<?> root = hierarchyOf(clazz).root();
            throw new IllegalArgumentException(clazz.getName() + " carries @JsonTypeInfo but belongs to the sealed "
                    + "hierarchy rooted at " + root.getName() + ", which " + SealedPolymorphismModule.class.getSimpleName()
                    + " handles. Move the annotation to " + root.getSimpleName() + " to use Jackson's polymorphic "
                    + "handling for the whole hierarchy, or remove it to use " + TYPE_PROPERTY_NAME + ".");
        }
    }

    /**
     * The top of the hierarchy {@code clazz} belongs to. The {@code @JsonTypeInfo} opt-out is read
     * from the root rather than from {@code clazz} itself so that both halves of the module agree:
     * an annotation on one implementation governs that implementation's own subtypes, and must not
     * silently switch off tagging for it while the base is still dispatching on {@code @type}.
     */
    Class<?> rootOf(Class<?> clazz) {
        return rootsByMember.get(clazz).orElseThrow(() -> new IllegalArgumentException(clazz.getName()
                + " does not implement " + MARKER.getName() + ", and is not registered with "
                + SealedPolymorphismModule.class.getSimpleName() + "."));
    }

    /** The resolved hierarchy {@code clazz} belongs to, built once per root. */
    SealedHierarchy hierarchyOf(Class<?> clazz) {
        return hierarchiesByRoot.get(rootOf(clazz));
    }

    private ClassValue<Optional<Class<?>>> newRootCache() {
        return new ClassValue<>() {
            @Override
            protected Optional<Class<?>> computeValue(Class<?> type) {
                return findRoot(type);
            }
        };
    }

    private Optional<Class<?>> findRoot(Class<?> clazz) {
        if (clazz == MARKER) {
            return Optional.empty();
        }
        // the common case: no marker anywhere above, and nothing registered to reach either
        if (!MARKER.isAssignableFrom(clazz) && registeredRoots.isEmpty()) {
            return Optional.empty();
        }
        Set<Class<?>> optedIn = new LinkedHashSet<>();
        collectOptedIn(clazz, optedIn, new HashSet<>());
        if (optedIn.isEmpty()) {
            return Optional.empty();
        }
        Class<?> root = null;
        for (Class<?> candidate : optedIn) {
            if (root == null || candidate.isAssignableFrom(root)) {
                root = candidate;
            }
        }
        List<String> disjoint = new ArrayList<>();
        for (Class<?> candidate : optedIn) {
            if (!root.isAssignableFrom(candidate)) {
                disjoint.add(candidate.getName());
            }
        }
        if (!disjoint.isEmpty()) {
            throw new IllegalArgumentException(clazz.getName() + " belongs to more than one sealed hierarchy handled "
                    + "by " + SealedPolymorphismModule.class.getSimpleName() + " - " + root.getName() + " and "
                    + String.join(", ", disjoint) + " - so there is no single hierarchy whose names it could be "
                    + "written under. Opt only one of them in.");
        }
        return Optional.of(root);
    }

    /**
     * Collects every supertype that has opted in. Unlike the marker, a registration names one type
     * rather than everything below it, so the walk cannot stop at a supertype that has not opted in
     * - a registered root may sit above several plain classes.
     */
    private void collectOptedIn(Class<?> clazz, Set<Class<?>> into, Set<Class<?>> visited) {
        if (clazz == null || clazz == Object.class || clazz == MARKER || !visited.add(clazz)) {
            return;
        }
        if (MARKER.isAssignableFrom(clazz) || registeredRoots.contains(clazz)) {
            into.add(clazz);
        }
        collectOptedIn(clazz.getSuperclass(), into, visited);
        for (Class<?> iface : clazz.getInterfaces()) {
            collectOptedIn(iface, into, visited);
        }
    }

    /** True for a type that can hold a value of its own, so can carry a name of its own. */
    static boolean isConcrete(Class<?> clazz) {
        return !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers());
    }

    /**
     * The name written to {@code @type}: the implementation's binary name with the longest prefix
     * shared with the hierarchy root removed. Never a fully qualified class name.
     *
     * <p>An implementation declared beside the root, or nested inside the root itself, keeps its
     * simple name. One nested inside some other class keeps that class in its name -
     * {@code Boxed$Same} rather than {@code Same} - so that two classes can each hold an
     * implementation of the same name.
     */
    static String typeNameFor(Class<?> root, Class<?> clazz) {
        String name = clazz.getName();
        // prefixes run longest to shortest, so the first match is the most specific
        for (String prefix : prefixesFor(root.getName())) {
            if (name.startsWith(prefix)) {
                return name.substring(prefix.length());
            }
        }
        return name.substring(name.lastIndexOf('.') + 1);
    }

    /**
     * Where an implementation of the hierarchy could have been declared, longest prefix first:
     * nested inside the root, nested inside any class enclosing the root, or alongside the root in
     * its package.
     */
    static List<String> prefixesFor(String rootName) {
        int lastDot = rootName.lastIndexOf('.');
        String packagePrefix = lastDot == -1 ? "" : rootName.substring(0, lastDot + 1);
        Set<String> prefixes = new LinkedHashSet<>();
        prefixes.add(rootName + "$");
        String enclosing = rootName;
        int separator = enclosing.lastIndexOf('$');
        while (separator > packagePrefix.length()) {
            enclosing = enclosing.substring(0, separator);
            prefixes.add(enclosing + "$");
            separator = enclosing.lastIndexOf('$');
        }
        prefixes.add(packagePrefix);
        return List.copyOf(prefixes);
    }

    /**
     * The enum a class belongs to, or {@code null}. An enum constant with a body compiles to an
     * anonymous subclass of the enum, so the constant's own class is not the enum class.
     */
    static Class<?> enumClassOf(Class<?> clazz) {
        if (clazz.isEnum()) {
            return clazz;
        }
        Class<?> superclass = clazz.getSuperclass();
        return (superclass != null && superclass.isEnum()) ? superclass : null;
    }

    /**
     * Empties the cache of resolved hierarchies. The cache is only a memo of what is derived from
     * the class files, so clearing it affects performance but not behaviour.
     */
    static void clearCache() {
        hierarchiesByRoot = newHierarchyCache();
    }

    /**
     * Held in a {@link ClassValue} so that entries are collected along with the classes they
     * describe rather than pinning a class loader, and replaced wholesale to clear.
     */
    private static ClassValue<SealedHierarchy> newHierarchyCache() {
        return new ClassValue<>() {
            @Override
            protected SealedHierarchy computeValue(Class<?> root) {
                return SealedHierarchy.of(root);
            }
        };
    }
}
