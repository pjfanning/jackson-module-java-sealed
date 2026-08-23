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
    private final SealedHierarchy hierarchy;

    SealedPolymorphicDeserializer(Class<?> baseClass, SealedHierarchy hierarchy) {
        super(baseClass);
        this.baseClass = baseClass;
        this.hierarchy = hierarchy;
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) {
        if (p.currentToken() != JsonToken.START_OBJECT) {
            String hint = hierarchy.enumMemberHint();
            return ctxt.reportInputMismatch(baseClass, "Expected a JSON object with a %s property to create %s.%s",
                    SealedTypes.TYPE_PROPERTY_NAME, baseClass.getName(), hint == null ? "" : hint);
        }
        TaggedObject tagged = TaggedObject.split(p, ctxt);
        Class<?> subtype = hierarchy.resolve(baseClass, tagged.typeName());
        if (subtype == null) {
            return TaggedObject.unresolved(ctxt, baseClass, tagged.typeName(), hierarchy);
        }
        return ctxt.readValue(tagged.parser(), subtype);
    }
}
