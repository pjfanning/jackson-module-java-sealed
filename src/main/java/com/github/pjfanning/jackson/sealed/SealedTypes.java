package com.github.pjfanning.jackson.sealed;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tools.jackson.databind.introspect.MixInResolver;

/**
 * Naming rules and policy behind {@link SealedPolymorphismSupport}.
 *
 * <p>Every path through the module asks {@link #isMarked} first, so a class that does not carry the
 * marker never reaches any of the reflection here, and an application's own types are untouched.
 */
final class SealedTypes {

    /** Name of the JSON property that carries the derived type name. */
    static final String TYPE_PROPERTY_NAME = "@type";

    private static final Class<SealedPolymorphismSupport> MARKER = SealedPolymorphismSupport.class;

    private SealedTypes() {
    }

    /**
     * True for a type that opts into this module - the marker itself does not count, so that the
     * marker interface can be referred to without being treated as a hierarchy.
     */
    static boolean isMarked(Class<?> clazz) {
        return isMarked(null, clazz);
    }

    /**
     * True for a type in a hierarchy this module handles.
     *
     * <p>A hierarchy opts in by extending the marker, or - where its source cannot be changed - by
     * having a mix-in that extends the marker registered for its root. A mix-in does not change the
     * type on the JVM, so the marker is not inherited by the implementations the way it would be:
     * only the root is opted in directly, and its implementations are reached from there. That is
     * why this asks the mapper's configuration rather than the class alone.
     */
    static boolean isMarked(MixInResolver mixIns, Class<?> clazz) {
        if (clazz == null || clazz == MARKER) {
            return false;
        }
        // unchanged cost for a mapper with no mix-ins, which is the usual case
        if (MARKER.isAssignableFrom(clazz)) {
            return true;
        }
        return hasMixIns(mixIns) && findRoot(mixIns, clazz) != null;
    }

    /** True where the mix-in registered for a type carries the marker. */
    private static boolean markedByMixIn(MixInResolver mixIns, Class<?> clazz) {
        if (!hasMixIns(mixIns)) {
            return false;
        }
        Class<?> mixIn = mixIns.findMixInClassFor(clazz);
        return mixIn != null && MARKER.isAssignableFrom(mixIn);
    }

    private static boolean hasMixIns(MixInResolver mixIns) {
        return mixIns != null && mixIns.hasMixIns();
    }

    /** True where the mix-in registered for a type supplies {@code @JsonTypeInfo}. */
    private static boolean jsonTypeInfoByMixIn(MixInResolver mixIns, Class<?> clazz) {
        if (!hasMixIns(mixIns)) {
            return false;
        }
        Class<?> mixIn = mixIns.findMixInClassFor(clazz);
        return mixIn != null && mixIn.getAnnotation(JsonTypeInfo.class) != null;
    }

    /**
     * True for a marked type this module should handle. A hierarchy whose root carries
     * {@code @JsonTypeInfo} is left to Jackson's own polymorphic handling rather than being tagged
     * twice.
     */
    static boolean isSupported(Class<?> clazz) {
        return isSupported(null, clazz);
    }

    static boolean isSupported(MixInResolver mixIns, Class<?> clazz) {
        // an enum is Jackson's to write, as a string; this module does not take that over
        if (!isMarked(mixIns, clazz) || enumClassOf(clazz) != null) {
            return false;
        }
        // asked before the hierarchy is built, since a hierarchy Jackson owns need not be sealed
        if (jsonTypeInfoByMixIn(mixIns, rootOf(mixIns, clazz))) {
            return false;
        }
        return !hierarchyOf(mixIns, clazz).isJacksonOwned();
    }

    /** True for a type that can hold a value of its own, so can carry a name of its own. */
    static boolean isConcrete(Class<?> clazz) {
        return !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers());
    }

    /**
     * True for a type that can only be dispatched on, never instantiated - an interface or abstract
     * class. Reading one means reading whatever its {@code @type} names.
     */
    static boolean isBaseType(Class<?> clazz) {
        return isBaseType(null, clazz);
    }

    static boolean isBaseType(MixInResolver mixIns, Class<?> clazz) {
        return isSupported(mixIns, clazz) && !isConcrete(clazz);
    }

    /**
     * True for a concrete type that other implementations extend - {@code sealed class Node} is both
     * a value in its own right and a base its subclasses are read through. A property declared at
     * one of these has to dispatch rather than read straight through to the bean, or a subclass
     * would silently be read back as the type the property was declared as.
     */
    static boolean needsSubtypeDispatch(Class<?> clazz) {
        return needsSubtypeDispatch(null, clazz);
    }

    static boolean needsSubtypeDispatch(MixInResolver mixIns, Class<?> clazz) {
        return isSupported(mixIns, clazz) && isConcrete(clazz) && clazz.isSealed();
    }

    /**
     * {@code @JsonTypeInfo} on an implementation rather than on the root cannot be honoured
     * alongside {@code @type}: Jackson treats the annotated class as a polymorphic base in its own
     * right, so reading it demands that annotation's type id, which nothing in a marked hierarchy
     * ever writes. The combination is reported rather than left to produce JSON that cannot be read
     * back.
     */
    static void checkNoConflictingJsonTypeInfo(Class<?> clazz) {
        checkNoConflictingJsonTypeInfo(null, clazz);
    }

    static void checkNoConflictingJsonTypeInfo(MixInResolver mixIns, Class<?> clazz) {
        if (isSupported(mixIns, clazz)
                && (clazz.getAnnotation(JsonTypeInfo.class) != null || jsonTypeInfoByMixIn(mixIns, clazz))) {
            Class<?> root = hierarchyOf(mixIns, clazz).root();
            throw new IllegalArgumentException(clazz.getName() + " carries @JsonTypeInfo but belongs to the "
                    + MARKER.getSimpleName() + " hierarchy rooted at " + root.getName() + ". Move the annotation to "
                    + root.getSimpleName() + " to use Jackson's polymorphic handling for the whole hierarchy, or "
                    + "remove it to use " + TYPE_PROPERTY_NAME + ".");
        }
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
     * The top of the marked hierarchy {@code clazz} belongs to. The {@code @JsonTypeInfo} opt-out is
     * read from the root rather than from {@code clazz} itself so that both halves of the module
     * agree: an annotation on one implementation governs that implementation's own subtypes, and
     * must not silently switch off tagging for it while the base is still dispatching on
     * {@code @type}.
     */
    static Class<?> rootOf(Class<?> clazz) {
        return rootOf(null, clazz);
    }

    static Class<?> rootOf(MixInResolver mixIns, Class<?> clazz) {
        Class<?> root = findRoot(mixIns, clazz);
        if (root == null) {
            throw new IllegalArgumentException(clazz.getName() + " does not implement " + MARKER.getName()
                    + ", and no mix-in carrying it is registered for anything above it.");
        }
        return root;
    }

    /** The top of the opted-in hierarchy {@code clazz} belongs to, or {@code null} if there is none. */
    private static Class<?> findRoot(MixInResolver mixIns, Class<?> clazz) {
        Set<Class<?>> optedIn = new LinkedHashSet<>();
        collectOptedIn(mixIns, clazz, optedIn, new HashSet<>());
        if (optedIn.isEmpty()) {
            return null;
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
            throw new IllegalArgumentException(clazz.getName() + " belongs to more than one "
                    + MARKER.getSimpleName() + " hierarchy - " + root.getName() + " and " + String.join(", ", disjoint)
                    + " - so there is no single hierarchy whose names it could be written under. Opt only one of "
                    + "them in, and let the other be a plain interface.");
        }
        return root;
    }

    /**
     * Collects every supertype that has opted in. A mix-in marks one type rather than everything
     * below it, so unlike the marker the walk cannot stop at a supertype that has not opted in - a
     * mix-in marked root may sit above several plain classes.
     */
    private static void collectOptedIn(MixInResolver mixIns, Class<?> clazz, Set<Class<?>> into,
                                       Set<Class<?>> visited) {
        if (clazz == null || clazz == Object.class || clazz == MARKER || !visited.add(clazz)) {
            return;
        }
        if (MARKER.isAssignableFrom(clazz) || markedByMixIn(mixIns, clazz)) {
            into.add(clazz);
        }
        collectOptedIn(mixIns, clazz.getSuperclass(), into, visited);
        for (Class<?> iface : clazz.getInterfaces()) {
            collectOptedIn(mixIns, iface, into, visited);
        }
    }


    /** The resolved hierarchy {@code clazz} belongs to, built once per root. */
    static SealedHierarchy hierarchyOf(Class<?> clazz) {
        return hierarchyOf(null, clazz);
    }

    static SealedHierarchy hierarchyOf(MixInResolver mixIns, Class<?> clazz) {
        return cache.byRoot.get(rootOf(mixIns, clazz));
    }

    /**
     * Empties the cache of resolved hierarchies. The cache is only a memo of what is derived from
     * the class files, so clearing it affects performance but not behaviour.
     */
    static void clearCache() {
        cache = new Cache();
    }

    /**
     * Held in a {@link ClassValue} so that entries are collected along with the classes they
     * describe rather than pinning a class loader, and replaced wholesale to clear.
     *
     * <p>Keyed by root only. Which root a type belongs to can depend on the mapper's mix-ins, and so
     * differ between mappers, but a root's name table is derived from the class files alone and is
     * the same however that root opted in - so only the second half is memoized here.
     */
    private static final class Cache {
        final ClassValue<SealedHierarchy> byRoot = new ClassValue<>() {
            @Override
            protected SealedHierarchy computeValue(Class<?> root) {
                return SealedHierarchy.of(root);
            }
        };
    }

    private static volatile Cache cache = new Cache();
}
