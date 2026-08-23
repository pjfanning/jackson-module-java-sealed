# jackson-module-java-sealed

Polymorphic serialization for Java 17+ `sealed` hierarchies, opted into with a marker interface
instead of an annotation.

Jackson can already do this — see [below](#jackson-can-already-do-this) — so this is an alternative
style rather than a missing capability. Extend `SealedPolymorphismSupport` from the base of a sealed
hierarchy and every implementation gains a `@type` property, with no Jackson annotations on your
types at all.

> **Status: early.** Covered by 90 tests, so the examples below are verified output. Snapshots are
> published, but there is no release yet and the API may still change.

## Jackson can already do this

`@JsonTypeInfo` on a sealed base is enough on its own. Jackson reads the permitted subclasses from
the class file, so there is no `@JsonSubTypes` to write and no list to keep in step:

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
public sealed interface Shape permits Circle, Square {}

// {"type":"Circle","r":2.0}
```

If that suits you, use it — it is one annotation, it ships with Jackson, and it is more configurable
than this module. What follows is what you get by using this module instead.

| | `@JsonTypeInfo` | this module |
| --- | --- | --- |
| Opting in | annotation on the base | marker interface on the base |
| Property name | yours, via `property=` | always `@type` |
| Type id | `Id.SIMPLE_NAME` gives `Circle`; `Id.NAME` qualifies a nested class; `Id.CLASS` is fully qualified | the binary name with the root's shared prefix removed — never fully qualified |
| Configurability | inclusion style, visibility, defaults, custom resolvers | none of it |
| Hierarchies you cannot edit | a mix-in carrying `@JsonTypeInfo` | a mix-in carrying the marker |
| Two nested implementations sharing a simple name | see below | reported as a configuration error |

That last row is the one substantive difference. Given `Boxed.Same` and `Nested.Same` in one
hierarchy, `Id.SIMPLE_NAME` writes `{"type":"Same"}` for both — and reads both back as
`Nested.Same`, so a `Boxed.Same` silently becomes something else. This module derives `Boxed$Same`
and `Nested$Same`, and refuses at startup if two implementations would still collide.

## Requirements

- Java 17 or later (sealed classes are [JEP 409](https://openjdk.org/jeps/409))
- Jackson 3 (`tools.jackson`) — built against 3.1.6

## Installing

Snapshots are published to the Sonatype Central snapshot repository on every commit to `main`.
There is no release yet.

```groovy
repositories {
    mavenCentral()
    maven { url = uri('https://central.sonatype.com/repository/maven-snapshots/') }
}

dependencies {
    implementation 'com.github.pjfanning:jackson-module-java-sealed:0.1.0-SNAPSHOT'
}
```

Or build and install locally:

```
./gradlew publishToMavenLocal
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

The module only ever looks at types that have opted in — through the marker, or through a
[mix-in](#hierarchies-you-cannot-change) — so adding it has no effect on anything else your
application serializes.

## Hierarchies you cannot change

Extending the marker means editing the base type. Where that is not possible — a hierarchy from a
library, or generated code — register a Jackson **mix-in** that extends the marker instead:

```java
// yours, in your own package
public interface AnimalMixIn extends SealedPolymorphismSupport {}

ObjectMapper mapper = JsonMapper.builder()
        .addModule(new SealedPolymorphismModule())
        .addMixIn(Animal.class, AnimalMixIn.class)
        .build();
```

The mix-in carries the marker and nothing else. It does not — and cannot — extend the hierarchy it
is mixed into: a sealed type's `permits` clause names its subtypes, and someone who cannot change
those classes cannot add themselves to it. Jackson never requires a mix-in to be a subtype of what
it is mixed into; it only harvests annotations and supertypes from it.

Mix it into the **root**; its implementations follow from the `permits` clause. A mix-in does not
change the type on the JVM, so the marker is not inherited by the implementations the way it would
be if the base extended it — only the root is opted in, and the module reads that from the mapper's
configuration rather than from the classes.

A mixed-in hierarchy is handled exactly as a marked one, and held to the same requirements: it must
be sealed, a `non-sealed` member is still reported, and enums are still left to Jackson. Mixing into
a type part way down a hierarchy is allowed and makes that type the root.

Two mix-ins that are not opt-ins:

| Mix-in | Result |
| --- | --- |
| does not extend `SealedPolymorphismSupport` | opts nothing in — the hierarchy stays untouched |
| carries `@JsonTypeInfo` as well as the marker | the module stands down, as it does for the annotation on the class |

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

## Enums are left to Jackson

This module does not touch enums, even ones permitted by a hierarchy it handles. Jackson writes an
enum as a string, and it keeps doing so — in every position, including as a `Map` key, and including
an enum with a custom `@JsonValue` representation or with constant bodies.

```java
public sealed interface Signal extends SealedPolymorphismSupport permits Data, Status {}

public record Data(int value) implements Signal {}
public enum Status implements Signal { IDLE, BUSY }

// {"signal":{"@type":"Data","value":1}}   the record is tagged
// {"signal":"IDLE"}                       the enum is not
```

An enum therefore has no `@type` name, which has one consequence worth knowing: **a value of an
enum member cannot be read back through the hierarchy's base type.** A string is not something the
base type can dispatch on, so reading `{"signal":"IDLE"}` as a `Signal` fails, and says why. Writing
works, and reading works wherever the property is declared as the enum type itself.

If you need a hierarchy member that round-trips through the base type and carries no state, use a
record with no components — `record Unknown() implements Animal {}` writes as `{"@type":"Unknown"}`
and reads straight back.

Putting the marker on an enum is not an error, it just has no effect: the enum is written as a
string either way.

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

Every type this module handles must be `sealed`, or `final` if it is a leaf. Anything else is
reported as a configuration error the first time Jackson meets the type, rather than being written
out as JSON that could not be read back:

| Declaration | Result |
| --- | --- |
| `sealed interface X extends SealedPolymorphismSupport` | supported |
| `sealed abstract class X implements SealedPolymorphismSupport` | supported |
| `sealed class X implements SealedPolymorphismSupport` | supported — a value and a base |
| `interface X extends SealedPolymorphismSupport` | error: not sealed |
| `non-sealed class X implements Base` | error: reopens the hierarchy |

Records are closed by construction and need no modifier of their own.

Enums sit outside this rule entirely, because the module does not handle them. An enum permitted by
the root is skipped rather than checked — whether it is `final`, or implicitly `sealed` because its
constants have bodies — and `enum X implements SealedPolymorphismSupport` is ignored rather than
rejected.

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

## Tests

90 tests, in `src/test/java/com/github/pjfanning/jackson/sealed/`:

| Test | Covers |
| --- | --- |
| `poly/SealedPolymorphismTest` | Tagging, reading, and the naming rules |
| `poly/EnumsUntouchedTest` | That enums serialize identically with and without this module |
| `poly/MixInTest` | Hierarchies opted in by a Jackson mix-in rather than by the marker |
| `poly/NestedPolymorphismTest` | A polymorphic value holding a polymorphic value |
| `poly/InvalidHierarchyTest` | The three ways a hierarchy can fail to be closed — not sealed, reopened by a `non-sealed` member, clashing derived names — on both the read and the write path, plus that a marked enum is ignored |
| `SealedTypesTest` | The name derivation itself, and resolution |

## Not done yet

- A release. Only snapshots are published; releases need signing and the Central Portal release
  endpoint, which are deliberately not wired up yet.
- A Jackson 2.x build. All Jackson API contact is confined to the serializer and deserializer
  classes, so the reflection core would port unchanged.
- Polymorphic values as `Map` keys. A tagged object cannot be a JSON property name, so a handled
  hierarchy is not usable as a key type. Enum keys are unaffected, being Jackson's to write.
- Reading an enum member back through the hierarchy's base type — see above.

## Keeping in step with jackson-module-scala

The design follows the `SealedPolymorphismSupport` added to jackson-module-scala in
[FasterXML/jackson-module-scala#835](https://github.com/FasterXML/jackson-module-scala/pull/835):
the same marker-interface opt-in, the same `@type` property, and the same rule for deriving a name
from the hierarchy root. The aim is to keep the two in step, so that a sealed hierarchy modelled in
either language is written and read the same way, and a change to one is worth mirroring in the
other.

The constructs line up more closely than the two languages might suggest. A Scala `case class` and a
Java record are both written `{"@type":"Name", ...}`; a Scala `case object` and a Java record with no
components are both written `{"@type":"Name"}`.

Enums are not a counterpart pair, and do not need to be. A Scala 3 `enum` is nearer a sealed
hierarchy than a Java enum: its cases can carry data, so jackson-module-scala has separate support
that tags those parameterized cases with the same `@type` property, writing the simple cases as a
bare name. A Java enum constant carries no data of its own, so Jackson writes it as a name and this
module leaves it alone — which is the same outcome, reached without needing to be told.

## License

Apache License 2.0
