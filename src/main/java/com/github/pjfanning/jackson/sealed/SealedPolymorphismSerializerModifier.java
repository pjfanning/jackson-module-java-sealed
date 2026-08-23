package com.github.pjfanning.jackson.sealed;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.ValueSerializerModifier;

/**
 * Tags the serializer of every concrete implementation of a marked hierarchy with its
 * {@code @type} name.
 */
final class SealedPolymorphismSerializerModifier extends ValueSerializerModifier {

    private static final long serialVersionUID = 1L;

    @Override
    public ValueSerializer<?> modifySerializer(SerializationConfig config, BeanDescription.Supplier beanDescRef,
                                               ValueSerializer<?> serializer) {
        Class<?> rawClass = beanDescRef.getBeanClass();
        if (!SealedTypes.isMarked(rawClass)) {
            return serializer;
        }
        SealedTypes.checkNoConflictingJsonTypeInfo(rawClass);
        // a base type is never written directly - only the implementation dispatched to at runtime
        if (!SealedTypes.isSupported(rawClass) || !SealedTypes.isConcrete(rawClass)) {
            return serializer;
        }
        // an enum reaches this path as well as modifyEnumSerializer, and is named per constant
        if (SealedTypes.enumClassOf(rawClass) != null) {
            return new TypeTaggedEnumSerializer(SealedTypes.hierarchyOf(rawClass));
        }
        @SuppressWarnings("unchecked")
        ValueSerializer<Object> delegate = (ValueSerializer<Object>) serializer;
        return new TypeTaggedSerializer(SealedTypes.hierarchyOf(rawClass).nameOf(rawClass), delegate);
    }

    @Override
    public ValueSerializer<?> modifyEnumSerializer(SerializationConfig config, JavaType valueType,
                                                   BeanDescription.Supplier beanDescRef,
                                                   ValueSerializer<?> serializer) {
        Class<?> rawClass = valueType.getRawClass();
        if (!SealedTypes.isMarked(rawClass)) {
            return serializer;
        }
        SealedTypes.checkNoConflictingJsonTypeInfo(rawClass);
        if (!SealedTypes.isSupported(rawClass)) {
            return serializer;
        }
        return new TypeTaggedEnumSerializer(SealedTypes.hierarchyOf(rawClass));
    }
}
