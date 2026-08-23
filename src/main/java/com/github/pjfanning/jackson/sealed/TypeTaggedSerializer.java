package com.github.pjfanning.jackson.sealed;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.util.NameTransformer;

/**
 * Wraps the bean serializer of a concrete implementation so that its {@code @type} name is written
 * as the first property of the object, making the value round-trippable without
 * {@code @JsonTypeInfo} annotations.
 *
 * <p>The delegate is asked for an unwrapping view of itself, which writes the properties without the
 * enclosing braces, so the tag can be emitted ahead of them.
 */
final class TypeTaggedSerializer extends ValueSerializer<Object> {

    private final String typeName;
    private final ValueSerializer<Object> delegate;
    /** Resolved lazily - the delegate has to be resolved before it can hand out an unwrapping view. */
    private volatile ValueSerializer<Object> unwrapped;

    TypeTaggedSerializer(String typeName, ValueSerializer<Object> delegate) {
        this.typeName = typeName;
        this.delegate = delegate;
    }

    @Override
    public void resolve(SerializationContext ctxt) {
        delegate.resolve(ctxt);
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) {
        ValueSerializer<?> contextual = delegate.createContextual(ctxt, property);
        if (contextual == delegate) {
            return this;
        }
        @SuppressWarnings("unchecked")
        ValueSerializer<Object> typed = (ValueSerializer<Object>) contextual;
        return new TypeTaggedSerializer(typeName, typed);
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeStartObject(value);
        gen.writeStringProperty(SealedTypes.TYPE_PROPERTY_NAME, typeName);
        unwrapped().serialize(value, gen, ctxt);
        gen.writeEndObject();
    }

    private ValueSerializer<Object> unwrapped() {
        ValueSerializer<Object> resolved = unwrapped;
        if (resolved == null) {
            // idempotent, so an unsynchronized race simply recomputes the same view
            resolved = delegate.unwrappingSerializer(NameTransformer.NOP);
            unwrapped = resolved;
        }
        return resolved;
    }
}
