package com.github.pjfanning.jackson.sealed;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Writes an enum constant of a marked hierarchy as a tagged, otherwise empty object -
 * {@code {"@type":"Status$IDLE"}}.
 *
 * <p>An enum constant is the Java counterpart of the Scala module's {@code case object}: one value,
 * carrying no state, that has to be told apart from its siblings by name alone. The enum class
 * cannot be the unit of naming, because it holds several such values, so each constant is named
 * within it.
 *
 * <p>Only value serializers are replaced. An enum used as a map key keeps Jackson's ordinary key
 * handling, since a tagged object cannot be a JSON property name.
 */
final class TypeTaggedEnumSerializer extends ValueSerializer<Object> {

    private final SealedHierarchy hierarchy;

    TypeTaggedEnumSerializer(SealedHierarchy hierarchy) {
        this.hierarchy = hierarchy;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeStartObject(value);
        gen.writeStringProperty(SealedTypes.TYPE_PROPERTY_NAME, hierarchy.nameOfConstant((Enum<?>) value));
        gen.writeEndObject();
    }
}
