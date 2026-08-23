package com.github.pjfanning.jackson.sealed;

import tools.jackson.core.Version;
import tools.jackson.databind.JacksonModule;

/**
 * Jackson module that gives automatic polymorphic serialization to {@code sealed} hierarchies
 * marked with {@link SealedPolymorphismSupport}.
 *
 * <pre>{@code
 * ObjectMapper mapper = JsonMapper.builder()
 *         .addModule(new SealedPolymorphismModule())
 *         .build();
 * }</pre>
 *
 * <p>The module only ever looks at types carrying the marker, so registering it has no effect on
 * anything else an application serializes.
 *
 * @see SealedPolymorphismSupport
 */
public class SealedPolymorphismModule extends JacksonModule {

    @Override
    public String getModuleName() {
        return "SealedPolymorphismModule";
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public void setupModule(SetupContext context) {
        context.addSerializerModifier(new SealedPolymorphismSerializerModifier());
        context.addDeserializers(new SealedPolymorphismDeserializers());
        context.addDeserializerModifier(new SealedPolymorphismDeserializerModifier());
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
