package com.github.pjfanning.jackson.sealed;

import java.util.Set;

import tools.jackson.core.Version;
import tools.jackson.databind.JacksonModule;

/**
 * Jackson module that gives automatic polymorphic serialization to {@code sealed} hierarchies.
 *
 * <p>A hierarchy opts in either by extending {@link SealedPolymorphismSupport}:
 *
 * <pre>{@code
 * ObjectMapper mapper = JsonMapper.builder()
 *         .addModule(new SealedPolymorphismModule())
 *         .build();
 * }</pre>
 *
 * <p>or by being registered here, for a hierarchy whose source cannot be changed to extend the
 * marker - one from a library, or generated code. A module handles both at once: registering a type
 * adds to the hierarchies it already picks up from the marker, it does not replace them.
 *
 * <pre>{@code
 * SealedPolymorphismModule module = new SealedPolymorphismModule()
 *         .registerSealedInterfaceOrClass(Animal.class)
 *         .registerSealedInterfaceOrClass(Shape.class);
 *
 * ObjectMapper mapper = JsonMapper.builder().addModule(module).build();
 * }</pre>
 *
 * <p>A registered type is handled exactly as if it carried the marker, and is held to the same
 * requirement: it must be {@code sealed}, and so must every type below it that is not {@code final}.
 * Anything that cannot be handled is rejected by
 * {@link #registerSealedInterfaceOrClass(Class) registerSealedInterfaceOrClass} itself, rather than
 * later when Jackson first meets the type.
 *
 * <p>Register the <em>root</em> of a hierarchy; its implementations follow from the root's
 * {@code permits} clause and do not need registering themselves. Registering a type part way down a
 * hierarchy is allowed, and makes that type the root - names are then derived relative to it, and
 * its siblings are not handled.
 *
 * <p>The module only ever looks at types that have opted in one of these two ways, so registering
 * it has no effect on anything else an application serializes.
 *
 * @see SealedPolymorphismSupport
 */
public class SealedPolymorphismModule extends JacksonModule {

    private final SealedTypes types = new SealedTypes();

    /**
     * A module handling every hierarchy that extends {@link SealedPolymorphismSupport}. Add
     * hierarchies that do not with
     * {@link #registerSealedInterfaceOrClass(Class) registerSealedInterfaceOrClass}.
     */
    public SealedPolymorphismModule() {
    }

    /**
     * Handles the given sealed interface or class as if it carried
     * {@link SealedPolymorphismSupport}, on top of everything that does carry it.
     *
     * <p>Call it once per hierarchy root, as often as needed. Register the root: its
     * implementations follow from the root's {@code permits} clause and do not need registering
     * themselves. Registering the same type twice is harmless.
     *
     * <p>Registering a type part way down a hierarchy is allowed, and makes that type the root -
     * names are then derived relative to it, and its siblings are not handled.
     *
     * @param sealedType root of a sealed hierarchy to handle
     * @return this module, so calls can be chained
     * @throws IllegalArgumentException if the type is null, is not {@code sealed}, is an enum, or
     *                                  carries {@code @JsonTypeInfo} - which already tells Jackson
     *                                  how to write and read the hierarchy
     */
    public SealedPolymorphismModule registerSealedInterfaceOrClass(Class<?> sealedType) {
        types.register(sealedType);
        return this;
    }

    /** The hierarchy roots registered with this module, beyond those carrying the marker. */
    public Set<Class<?>> registeredTypes() {
        return types.registeredRoots();
    }

    @Override
    public String getModuleName() {
        return "SealedPolymorphismModule";
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    /**
     * Two modules registering different types are different modules. Jackson drops a module whose
     * registration id it has already seen, so without this a second module carrying extra
     * registrations would be silently ignored.
     */
    @Override
    public Object getRegistrationId() {
        return getClass().getName() + types.registeredRoots();
    }

    @Override
    public void setupModule(SetupContext context) {
        context.addSerializerModifier(new SealedPolymorphismSerializerModifier(types));
        context.addDeserializers(new SealedPolymorphismDeserializers(types));
        context.addDeserializerModifier(new SealedPolymorphismDeserializerModifier(types));
    }

    /**
     * Empties the cache of resolved hierarchies. The cache is only a memo of what is derived from
     * the class files, so clearing it affects performance but not behaviour. Chiefly useful where
     * classes are reloaded, or in tests.
     */
    public static void clearCache() {
        SealedTypes.clearCache();
    }
}
