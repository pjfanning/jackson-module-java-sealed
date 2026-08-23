package com.github.pjfanning.jackson.sealed;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.Deserializers;

/** Binds the dispatching deserializer to every base type of a marked hierarchy. */
final class SealedPolymorphismDeserializers extends Deserializers.Base {

    private final SealedTypes types;

    SealedPolymorphismDeserializers(SealedTypes types) {
        this.types = types;
    }

    @Override
    public ValueDeserializer<?> findBeanDeserializer(JavaType type, DeserializationConfig config,
                                                     BeanDescription.Supplier beanDescRef) {
        Class<?> rawClass = type.getRawClass();
        return hasDeserializerFor(config, rawClass)
                ? new SealedPolymorphicDeserializer(rawClass, types.hierarchyOf(rawClass))
                : null;
    }

    @Override
    public boolean hasDeserializerFor(DeserializationConfig config, Class<?> valueType) {
        return types.isBaseType(valueType);
    }
}
