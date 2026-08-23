package com.github.pjfanning.jackson.sealed;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Reads an enum constant of a marked hierarchy from the tagged object form its serializer writes.
 * Needed where a property is declared as the enum type itself rather than as the hierarchy's base,
 * so the value is read straight rather than dispatched.
 *
 * <p>Anything that is not an object - a plain string, as Jackson writes an untagged enum - is left
 * to the enum's own deserializer, so enums read from JSON this module did not write still work.
 */
final class TaggedEnumDeserializer extends ValueDeserializer<Object> {

    private final Class<?> enumClass;
    private final ValueDeserializer<Object> delegate;

    TaggedEnumDeserializer(Class<?> enumClass, ValueDeserializer<Object> delegate) {
        this.enumClass = enumClass;
        this.delegate = delegate;
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) {
        if (p.currentToken() != JsonToken.START_OBJECT) {
            return delegate.deserialize(p, ctxt);
        }
        TaggedObject tagged = TaggedObject.split(p, ctxt);
        Subtype subtype = SealedTypes.hierarchyOf(enumClass).resolve(enumClass, tagged.typeName());
        if (subtype == null || subtype.singleton() == null) {
            return TaggedObject.unresolved(ctxt, enumClass, tagged.typeName());
        }
        return subtype.singleton();
    }
}
