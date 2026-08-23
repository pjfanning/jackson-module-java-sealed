package com.github.pjfanning.jackson.sealed;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.util.TokenBuffer;

/**
 * Buffers a tagged object, pulling the {@code @type} property out of the tokens handed on, so that
 * the implementation's own deserializer never sees a property it does not know about.
 *
 * @param typeName the value of the {@code @type} property, or {@code null} if the object had none
 * @param parser   the object's remaining properties, positioned on the opening brace
 */
record TaggedObject(String typeName, JsonParser parser) {

    static TaggedObject split(JsonParser p, DeserializationContext ctxt) {
        TokenBuffer buffer = ctxt.bufferForInputBuffering(p);
        buffer.writeStartObject();
        String typeName = null;
        while (p.nextToken() == JsonToken.PROPERTY_NAME) {
            String name = p.currentName();
            p.nextToken();
            if (typeName == null && SealedTypes.TYPE_PROPERTY_NAME.equals(name)) {
                typeName = p.getValueAsString();
            } else {
                buffer.writeName(name);
                buffer.copyCurrentStructure(p);
            }
        }
        buffer.writeEndObject();
        return new TaggedObject(typeName, buffer.asParserOnFirstToken(ctxt));
    }

    /** Reports a {@code @type} that names nothing in the hierarchy being read. */
    static <T> T unresolved(DeserializationContext ctxt, Class<?> baseClass, String typeName) {
        if (typeName == null) {
            return ctxt.reportInputMismatch(baseClass, "Expected a %s property naming an implementation of %s",
                    SealedTypes.TYPE_PROPERTY_NAME, baseClass.getName());
        }
        return ctxt.reportInputMismatch(baseClass, "'%s' is not an implementation of %s: no permitted subtype of "
                        + "the sealed hierarchy rooted at %s is written under that %s name",
                typeName, baseClass.getName(), SealedTypes.hierarchyOf(baseClass).root().getName(),
                SealedTypes.TYPE_PROPERTY_NAME);
    }
}
