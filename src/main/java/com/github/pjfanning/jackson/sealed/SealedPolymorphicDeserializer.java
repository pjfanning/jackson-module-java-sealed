package com.github.pjfanning.jackson.sealed;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Reads a value of a marked hierarchy by dispatching on its {@code @type} property. Bound to an
 * interface or abstract class, which never holds a value of its own.
 */
final class SealedPolymorphicDeserializer extends StdDeserializer<Object> {

    private static final long serialVersionUID = 1L;

    private final Class<?> baseClass;

    SealedPolymorphicDeserializer(Class<?> baseClass) {
        super(baseClass);
        this.baseClass = baseClass;
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) {
        if (p.currentToken() != JsonToken.START_OBJECT) {
            String hint = SealedTypes.hierarchyOf(baseClass).enumMemberHint();
            return ctxt.reportInputMismatch(baseClass, "Expected a JSON object with a %s property to create %s.%s",
                    SealedTypes.TYPE_PROPERTY_NAME, baseClass.getName(), hint == null ? "" : hint);
        }
        TaggedObject tagged = TaggedObject.split(p, ctxt);
        Class<?> subtype = SealedTypes.hierarchyOf(baseClass).resolve(baseClass, tagged.typeName());
        if (subtype == null) {
            return TaggedObject.unresolved(ctxt, baseClass, tagged.typeName());
        }
        return ctxt.readValue(tagged.parser(), subtype);
    }
}
