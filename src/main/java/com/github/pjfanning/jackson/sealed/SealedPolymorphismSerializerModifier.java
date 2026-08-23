package com.github.pjfanning.jackson.sealed;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.ValueSerializerModifier;

/**
 * Tags the serializer of every concrete implementation of a marked hierarchy with its
 * {@code @type} name.
 */
final class SealedPolymorphismSerializerModifier extends ValueSerializerModifier {

    private static final long serialVersionUID = 1L;

    private final transient SealedTypes types;

    SealedPolymorphismSerializerModifier(SealedTypes types) {
        this.types = types;
    }

    @Override
    public ValueSerializer<?> modifySerializer(SerializationConfig config, BeanDescription.Supplier beanDescRef,
                                               ValueSerializer<?> serializer) {
        Class<?> rawClass = beanDescRef.getBeanClass();
        if (!types.isOptedIn(rawClass)) {
            return serializer;
        }
        types.checkNoConflictingJsonTypeInfo(rawClass);
        // a base type is never written directly - only the implementation dispatched to at runtime
        if (!types.isSupported(rawClass) || !SealedTypes.isConcrete(rawClass)) {
            return serializer;
        }
        @SuppressWarnings("unchecked")
        ValueSerializer<Object> delegate = (ValueSerializer<Object>) serializer;
        return new TypeTaggedSerializer(types.hierarchyOf(rawClass).nameOf(rawClass), delegate);
    }

}
