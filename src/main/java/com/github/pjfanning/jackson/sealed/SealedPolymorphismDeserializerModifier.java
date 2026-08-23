package com.github.pjfanning.jackson.sealed;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.BeanDeserializerBuilder;
import tools.jackson.databind.deser.ValueDeserializerModifier;

/**
 * The dispatching deserializer strips {@code @type} before delegating, but a property may also be
 * declared as the implementation type rather than as the base type, in which case the value is read
 * straight as a bean and has to tolerate the tag.
 */
final class SealedPolymorphismDeserializerModifier extends ValueDeserializerModifier {

    private static final long serialVersionUID = 1L;

    @Override
    public BeanDeserializerBuilder updateBuilder(DeserializationConfig config, BeanDescription.Supplier beanDescRef,
                                                 BeanDeserializerBuilder builder) {
        Class<?> rawClass = beanDescRef.getBeanClass();
        if (!SealedTypes.isMarked(rawClass)) {
            return builder;
        }
        SealedTypes.checkNoConflictingJsonTypeInfo(rawClass);
        if (SealedTypes.isSupported(rawClass) && SealedTypes.isConcrete(rawClass)) {
            builder.addIgnorable(SealedTypes.TYPE_PROPERTY_NAME);
        }
        return builder;
    }

    @Override
    public ValueDeserializer<?> modifyDeserializer(DeserializationConfig config, BeanDescription.Supplier beanDescRef,
                                                   ValueDeserializer<?> deserializer) {
        Class<?> rawClass = beanDescRef.getBeanClass();
        if (!SealedTypes.needsSubtypeDispatch(rawClass)) {
            return deserializer;
        }
        @SuppressWarnings("unchecked")
        ValueDeserializer<Object> delegate = (ValueDeserializer<Object>) deserializer;
        return new TaggedBeanDeserializer(rawClass, delegate);
    }

    @Override
    public ValueDeserializer<?> modifyEnumDeserializer(DeserializationConfig config, JavaType valueType,
                                                       BeanDescription.Supplier beanDescRef,
                                                       ValueDeserializer<?> deserializer) {
        Class<?> rawClass = valueType.getRawClass();
        if (!SealedTypes.isMarked(rawClass) || !SealedTypes.isSupported(rawClass)) {
            return deserializer;
        }
        @SuppressWarnings("unchecked")
        ValueDeserializer<Object> delegate = (ValueDeserializer<Object>) deserializer;
        return new TaggedEnumDeserializer(rawClass, delegate);
    }
}
