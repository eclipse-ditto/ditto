/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.base.json;

import java.util.Optional;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldMarker;

/**
 * An enumeration of types of JSON fields. Each field type constant is simultaneously a {@code Predicate} and can be
 * used as argument for {@link Jsonifiable.WithPredicate#toJson(Predicate)} for example.
 */
public enum FieldType implements JsonFieldMarker, Predicate<JsonField> {

    /**
     * Denotes a regular JSON field like for example {@code "thingId"}.
     */
    REGULAR,

    /**
     * Denotes a special JSON field like for example {@code "__schemaVersion"}.
     */
    SPECIAL,

    /**
     * Denotes a hidden JSON field - those are fields which "by default" are not visible.
     * {@link #REGULAR} and {@link #SPECIAL} fields may be additionally HIDDEN.
     *
     */
    HIDDEN;

    private final Predicate<JsonField> predicate;

    private FieldType() {
        predicate = jsonField -> {
            final Optional<JsonFieldDefinition> definition = jsonField.getDefinition();
            return !definition.isPresent() || jsonField.isMarkedAs(this);
        };
    }

    /**
     * Returns a Predicate for fields which are NOT {@link #HIDDEN}.
     *
     * @return the Predicate.
     */
    public static Predicate<JsonField> notHidden() {
        return FieldType.HIDDEN.negate();
    }

    /**
     * Returns a Predicate for fields which are either {@link #REGULAR} or {@link #SPECIAL}.
     *
     * @return the Predicate.
     */
    public static Predicate<JsonField> regularOrSpecial() {
        return FieldType.REGULAR.or(FieldType.SPECIAL);
    }

    /**
     * Returns a Predicate which returns {@code true} for all fields.
     *
     * @return the Predicate.
     */
    public static Predicate<JsonField> all() {
        return (unused -> true);
    }

    @Override
    public boolean test(final JsonField jsonField) {
        return predicate.test(jsonField);
    }

}
