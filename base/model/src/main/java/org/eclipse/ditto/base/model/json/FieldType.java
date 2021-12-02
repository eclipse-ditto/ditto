/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.base.model.json;

import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldMarker;

/**
 * An enumeration of types of JSON fields. Each field type constant is simultaneously a {@code Predicate} and can be
 * used as argument for {@link Jsonifiable.WithPredicate#toJson(java.util.function.Predicate)} for example.
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
     */
    HIDDEN;

    private final Predicate<JsonField> predicate;

    FieldType() {
        predicate = jsonField -> !jsonField.getDefinition().isPresent() || jsonField.isMarkedAs(this);
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
     * Returns a Predicate for fields which are either {@link #SPECIAL} or {@link #HIDDEN}.
     *
     * @return the Predicate.
     * @since  2.3.0.
     */
    public static Predicate<JsonField> specialOrHidden() {
        return FieldType.SPECIAL.or(FieldType.HIDDEN);
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
