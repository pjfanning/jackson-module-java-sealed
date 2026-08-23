package com.github.pjfanning.jackson.sealed;

/**
 * One implementation of a marked hierarchy, as reached from its {@code @type} name.
 *
 * @param type      the class the name resolves to
 * @param singleton the one value of that name, for a type that has exactly one - an enum constant -
 *                  or {@code null} for a type whose values have to be read from the JSON
 */
record Subtype(Class<?> type, Object singleton) {

    static Subtype ofClass(Class<?> type) {
        return new Subtype(type, null);
    }

    static Subtype ofConstant(Enum<?> constant) {
        return new Subtype(constant.getDeclaringClass(), constant);
    }
}
