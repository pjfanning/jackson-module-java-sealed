package com.github.pjfanning.jackson.sealed;

/**
 * Marker interface that opts a {@code sealed} Java type hierarchy into automatic polymorphic
 * serialization, without the {@code @JsonTypeInfo} and {@code @JsonSubTypes} annotations that
 * Jackson normally requires.
 *
 * <p>Extend it from the base of a {@code sealed} hierarchy and every implementation gains a
 * {@code @type} property naming the implementation:
 *
 * <pre>{@code
 * public sealed interface Animal extends SealedPolymorphismSupport permits Dog, Bird, Unknown {}
 *
 * public record Dog(String name) implements Animal {}
 * public record Bird(String name, boolean canFly) implements Animal {}
 * public record Unknown() implements Animal {}
 *
 * // {"@type":"Dog","name":"rex"}
 * // {"@type":"Unknown"}
 * }</pre>
 *
 * <p>The value written to {@code @type} is a derived name, never a fully qualified class name. An
 * implementation declared beside the base, or nested inside the base itself, is named by its simple
 * name; one nested inside some other class keeps that class in its name, so two classes can each
 * hold an implementation called the same thing:
 *
 * <pre>{@code
 * public sealed interface Dup extends SealedPolymorphismSupport permits Boxed.Same, Nested.Same {}
 *
 * class Boxed  { record Same(int v)    implements Dup {} }  // {"@type":"Boxed$Same","v":1}
 * class Nested { record Same(String v) implements Dup {} }  // {"@type":"Nested$Same","v":"x"}
 * }</pre>
 *
 * <p>Unlike Scala, {@code javac} records a sealed hierarchy in the class file, so the set of
 * implementations is read back exactly from the {@code PermittedSubclasses} attribute rather than
 * guessed. A {@code @type} value is resolved against that table and nothing else: it can only ever
 * name a permitted implementation of the hierarchy it appears in, so it is never a vector for
 * loading an arbitrary class.
 *
 * <h2>The hierarchy has to be closed</h2>
 *
 * <p>Every marked type must be {@code sealed}, or {@code final} if it is a leaf. A marked type that
 * is neither - a plain interface, an abstract class that is not sealed, or a {@code non-sealed}
 * member, which reopens the hierarchy to subclasses that could never be resolved back from a name -
 * is reported as a configuration error the first time Jackson meets it, rather than being written
 * out as JSON that could not be read back. Records are closed by construction and need no modifier
 * of their own.
 *
 * <h2>Hierarchies you cannot change</h2>
 *
 * <p>Extending this interface means editing the base type. Where that is not possible - a hierarchy
 * from a library, or generated code - register a Jackson mix-in that extends it instead:
 *
 * <pre>{@code
 * public interface AnimalMixIn extends SealedPolymorphismSupport {}
 *
 * JsonMapper.builder()
 *         .addModule(new SealedPolymorphismModule())
 *         .addMixIn(Animal.class, AnimalMixIn.class)
 *         .build();
 * }</pre>
 *
 * <p>The mix-in carries the marker and nothing else. It does not - and cannot - extend the hierarchy
 * it is mixed into: a sealed type's {@code permits} clause names its subtypes, and someone who
 * cannot change those classes cannot add themselves to it. Jackson never requires a mix-in to be a
 * subtype of what it is mixed into.
 *
 * <p>Mix it into the <em>root</em>; its implementations follow from the {@code permits} clause. A
 * mix-in does not change the type on the JVM, so the marker is not inherited by the implementations
 * the way it would be if the base extended it - only the root is opted in, and the module reads that
 * from the mapper's configuration. A hierarchy opted in this way is held to exactly the same
 * requirements as one that extends the marker directly.
 *
 * <h2>Enums are left to Jackson</h2>
 *
 * <p>An enum permitted by the root is not handled here. Jackson writes an enum as a string and keeps
 * doing so, so an enum member carries no {@code @type} name - which means a value of one cannot be
 * read back through the hierarchy's base type, though it reads normally where the property is
 * declared as the enum type itself. For a stateless member that does round trip through the base,
 * use a record with no components. Putting this marker on an enum has no effect.
 *
 * <h2>Deferring to Jackson</h2>
 *
 * <p>{@code @JsonTypeInfo} on the base of a marked hierarchy switches this module off for that
 * hierarchy, leaving Jackson's own polymorphic handling in sole charge. The same annotation on an
 * implementation rather than on the base is a configuration error: Jackson would treat the
 * annotated class as a polymorphic base in its own right and demand a type id that nothing in a
 * marked hierarchy ever writes.
 *
 * @see SealedPolymorphismModule
 */
public interface SealedPolymorphismSupport {
}
