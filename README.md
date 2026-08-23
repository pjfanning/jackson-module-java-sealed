# jackson-module-java-sealed

Automatic polymorphic serialization for Java 17+ `sealed` hierarchies, without the
`@JsonTypeInfo` and `@JsonSubTypes` annotations Jackson normally requires.

A Java port of the `SealedPolymorphismSupport` added to jackson-module-scala in
[FasterXML/jackson-module-scala#835](https://github.com/FasterXML/jackson-module-scala/pull/835),
using the same `@type` property and the same name-derivation rules, so a value written by one is
readable by the other.

> **Status: prototype.** The code compiles and the fixtures are in place, but the round-trip tests
> have not been written yet, so nothing here has been through an `ObjectMapper`. Treat the examples
> below as the intended behaviour rather than as verified output.

## Requirements

- Java 17 or later (sealed classes are [JEP 409](https://openjdk.org/jeps/409))
- Jackson 3 (`tools.jackson`) — built against 3.1.6

## Installing

Not published to Maven Central yet. To build and install locally:

```
./gradlew publishToMavenLocal
```

```groovy
dependencies {
    implementation 'com.github.pjfanning:jackson-module-java-sealed:0.1.0-SNAPSHOT'
}
```

## Using it

Register the module, and extend the `SealedPolymorphismSupport` marker from the base of a `sealed`
hierarchy:

```java
import com.github.pjfanning.jackson.sealed.SealedPolymorphismModule;
import com.github.pjfanning.jackson.sealed.SealedPolymorphismSupport;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public sealed interface Animal extends SealedPolymorphismSupport permits Dog, Bird, Unknown {}

public record Dog(String name) implements Animal {}
public record Bird(String name, boolean canFly) implements Animal {}
public record Unknown() implements Animal {}

public record Owner(String name, Animal pet) {}
```

```java
ObjectMapper mapper = JsonMapper.builder()
        .addModule(new SealedPolymorphismModule())
        .build();

mapper.writeValueAsString(new Owner("ann", new Dog("rex")));
// {"name":"ann","pet":{"@type":"Dog","name":"rex"}}

mapper.writeValueAsString(new Owner("ann", new Unknown()));
// {"name":"ann","pet":{"@type":"Unknown"}}

mapper.readValue("{\"@type\":\"Dog\",\"name\":\"rex\"}", Animal.class);
// Dog[name=rex]
```

The module only ever looks at types carrying the marker, so registering it has no effect on
anything else your application serializes.

## How names are derived

The value written to `@type` is never a fully qualified class name. It is the implementation's
binary name with the longest prefix it shares with the hierarchy root removed.

An implementation declared beside the root, or nested inside the root itself, keeps its simple
name:

```java
public sealed interface Payment extends SealedPolymorphismSupport permits Payment.Card, Payment.Cash {
    record Card(String last4) implements Payment {}   // {"@type":"Card","last4":"4242"}
    record Cash() implements Payment {}               // {"@type":"Cash"}
}
```

One nested inside some *other* class keeps that class in its name, so two classes can each hold an
implementation called the same thing:

```java
public sealed interface Dup extends SealedPolymorphismSupport permits Boxed.Same, Nested.Same {}

class Boxed  { record Same(int v)    implements Dup {} }   // {"@type":"Boxed$Same","v":1}
class Nested { record Same(String v) implements Dup {} }   // {"@type":"Nested$Same","v":"x"}
```

If two implementations would derive the same name, that is reported as a configuration error rather
than producing JSON that could not be read back unambiguously.

## Reading is exact, not a class-name lookup

`javac` records a sealed hierarchy in the class file, so the implementations are read straight off
the `PermittedSubclasses` attribute. That gives a closed name-to-class table per hierarchy: a
`@type` value either names a permitted implementation of the hierarchy being read, or it does not
resolve at all. It is never fed to `Class.forName`, so it cannot be used to load an arbitrary class,
and a name from a sibling branch will not resolve into a property that could not hold it.

This is the main thing the Java version does better than the Scala one. scalac leaves no trace of
`sealed` on the JVM, so jackson-module-scala has to rebuild candidate class names from where the
base is declared and filter them by subtype relationship.

## Enum members

An enum in a sealed hierarchy is the closest Java has to a set of Scala `case object`s. Because the
enum class holds several values, each *constant* is named individually:

```java
public sealed interface Signal extends SealedPolymorphismSupport permits Data, Status {}

public record Data(int value) implements Signal {}
public enum Status implements Signal { IDLE, BUSY }

// {"@type":"Data","value":1}
// {"@type":"Status$IDLE"}
```

Only value serializers are replaced — an enum used as a `Map` key keeps Jackson's ordinary key
handling, since a tagged object cannot be a JSON property name.

## Concrete sealed roots

A `sealed class` can be instantiable and still be a base its subclasses are read through. Such a
type carries a name of its own, and a property declared at it dispatches rather than reading
straight through:

```java
public sealed class Node implements SealedPolymorphismSupport permits Branch { /* int id */ }
public sealed class Branch extends Node permits Twig { /* String label */ }
public final class Twig extends Branch { /* int length */ }

// {"@type":"Node","id":1}
// {"@type":"Twig","id":3,"label":"t","length":9}
```

An object with no `@type` at such a property is read as the type the property was declared as.

## The hierarchy has to be closed

Every marked type must be `sealed`, or `final` if it is a leaf. Anything else is reported as a
configuration error the first time Jackson meets the type, rather than being written out as JSON
that could not be read back:

| Declaration | Result |
| --- | --- |
| `sealed interface X extends SealedPolymorphismSupport` | supported |
| `sealed abstract class X implements SealedPolymorphismSupport` | supported |
| `sealed class X implements SealedPolymorphismSupport` | supported — a value and a base |
| `interface X extends SealedPolymorphismSupport` | error: not sealed |
| `non-sealed class X implements Base` | error: reopens the hierarchy |
| `enum X implements SealedPolymorphismSupport` | error: mark the sealed interface it implements |

Records and enum constants are closed by construction and need no modifier of their own.

## Working alongside `@JsonTypeInfo`

`@JsonTypeInfo` on the base of a marked hierarchy switches this module off for that hierarchy,
leaving Jackson's own polymorphic handling in sole charge. This lets a marked hierarchy hold, or be
held by, an annotated one.

The same annotation on an *implementation* rather than on the base is a configuration error: Jackson
would treat the annotated class as a polymorphic base in its own right and demand a type id that
nothing in a marked hierarchy ever writes.

## Caching

Resolved hierarchies are memoized in a `ClassValue`, so entries are collected along with the classes
they describe rather than pinning a class loader. `SealedPolymorphismModule.clearCache()` empties
it; since the cache is only a memo of what is derived from the class files, clearing it affects
performance but not behaviour.

## Building

```
./gradlew build
```

## Not done yet

- The round-trip tests — JUnit ports of the Scala PR's `SealedPolymorphismSpec` and
  `NestedPolymorphismSpec`. The fixtures they run against are already in
  `src/test/java/com/github/pjfanning/jackson/sealed/poly/`.
- Publishing to Maven Central.
- A Jackson 2.x build. All Jackson API contact is confined to the serializer and deserializer
  classes, so the reflection core would port unchanged.

## License

Apache License 2.0
