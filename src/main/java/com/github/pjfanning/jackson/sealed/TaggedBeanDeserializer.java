package com.github.pjfanning.jackson.sealed;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Reads a value at a concrete type that others extend. A {@code sealed class Node} is both a value
 * in its own right and a base its subclasses are read through, so this wraps that type's own bean
 * deserializer: a {@code @type} naming it, or no {@code @type} at all, reads through to the
 * delegate, and anything else is dispatched. Wrapping rather than replacing is what lets the type
 * be read as itself without recursing.
 */
final class TaggedBeanDeserializer extends ValueDeserializer<Object> {

    private final Class<?> declaredClass;
    private final ValueDeserializer<Object> delegate;

    TaggedBeanDeserializer(Class<?> declaredClass, ValueDeserializer<Object> delegate) {
        this.declaredClass = declaredClass;
        this.delegate = delegate;
    }

    @Override
    public void resolve(DeserializationContext ctxt) {
        delegate.resolve(ctxt);
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        ValueDeserializer<?> contextual = delegate.createContextual(ctxt, property);
        if (contextual == delegate) {
            return this;
        }
        @SuppressWarnings("unchecked")
        ValueDeserializer<Object> typed = (ValueDeserializer<Object>) contextual;
        return new TaggedBeanDeserializer(declaredClass, typed);
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) {
        if (p.currentToken() != JsonToken.START_OBJECT) {
            return delegate.deserialize(p, ctxt);
        }
        TaggedObject tagged = TaggedObject.split(p, ctxt);
        // an untagged object is simply a value of the type it was declared as
        if (tagged.typeName() == null) {
            return delegate.deserialize(tagged.parser(), ctxt);
        }
        Class<?> subtype = SealedTypes.hierarchyOf(declaredClass).resolve(declaredClass, tagged.typeName());
        if (subtype == null) {
            return TaggedObject.unresolved(ctxt, declaredClass, tagged.typeName());
        }
        if (subtype == declaredClass) {
            return delegate.deserialize(tagged.parser(), ctxt);
        }
        return ctxt.readValue(tagged.parser(), subtype);
    }
}
