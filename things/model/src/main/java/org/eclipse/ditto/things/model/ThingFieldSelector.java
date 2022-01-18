/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonPointer;

/**
 * A Json field selector which validates that only valid fields of a thing can be selected.
 * Commas in keys are not supported by this selector.
 *
 * @since 2.0.0
 */
public final class ThingFieldSelector implements JsonFieldSelector {


    private static final JsonParseOptions JSON_PARSE_OPTIONS = JsonParseOptions.newBuilder()
            .withoutUrlDecoding()
            .build();
    static final List<String> SELECTABLE_FIELDS = Arrays.asList("thingId", "policyId", "definition",
            "_namespace", "_revision", "_created", "_modified", "_metadata", "_policy",
            "features(/[^,]+)?", "attributes(/[^,]+)?");
    private static final String KNOWN_FIELDS_REGEX = "/?(" + String.join("|", SELECTABLE_FIELDS) + ")";
    private static final String FIELD_SELECTION_REGEX = "^" + KNOWN_FIELDS_REGEX + "(," + KNOWN_FIELDS_REGEX + ")*$";
    private static final Pattern FIELD_SELECTION_PATTERN = Pattern.compile(FIELD_SELECTION_REGEX);

    private final JsonFieldSelector jsonFieldSelector;


    private ThingFieldSelector(final JsonFieldSelector jsonFieldSelector) {
        this.jsonFieldSelector = jsonFieldSelector;
    }

    /**
     * Creates a thing field selector based on the more generic json field selector. Selected fields are validated.
     * If the given json field selector is already an instance of {@link ThingFieldSelector} the same
     * instance will be returned.
     *
     * @param jsonFieldSelector the generic json field selector.
     * @return the ThingFieldSelector.
     * @throws InvalidThingFieldSelectionException when the given json field selector is {@code null} or contains
     * invalid fields.
     */
    public static ThingFieldSelector fromJsonFieldSelector(final JsonFieldSelector jsonFieldSelector) {
        if (jsonFieldSelector instanceof ThingFieldSelector) {
            return (ThingFieldSelector) jsonFieldSelector;
        } else if (null == jsonFieldSelector) {
            throw InvalidThingFieldSelectionException.forExtraFieldSelectionString(null);
        } else if (FIELD_SELECTION_PATTERN.matcher(jsonFieldSelector.toString()).matches()) {
            return new ThingFieldSelector(jsonFieldSelector);
        } else {
            throw InvalidThingFieldSelectionException.forExtraFieldSelectionString(jsonFieldSelector.toString());
        }
    }

    /**
     * Creates a thing field selector based on the given string representation of a json field selector.
     * Selected fields are validated.
     *
     * @param selectionString the string representation of a json field selector.
     * @return the ThingFieldSelector.
     * @throws InvalidThingFieldSelectionException when the given string is {@code null} or contains invalid fields.
     */
    public static ThingFieldSelector fromString(final String selectionString) {
        if (null == selectionString) {
            throw InvalidThingFieldSelectionException.forExtraFieldSelectionString(null);
        } else if (FIELD_SELECTION_PATTERN.matcher(selectionString).matches()) {
            return new ThingFieldSelector(JsonFactory.newFieldSelector(selectionString, JSON_PARSE_OPTIONS));
        } else {
            throw InvalidThingFieldSelectionException.forExtraFieldSelectionString(selectionString);
        }
    }

    /**
     * Returns the underlying JsonFieldSelector.
     *
     * @return the underlying JsonFieldSelector.
     */
    public JsonFieldSelector getJsonFieldSelector() {
        return jsonFieldSelector;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ThingFieldSelector that = (ThingFieldSelector) o;
        return Objects.equals(jsonFieldSelector, that.jsonFieldSelector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jsonFieldSelector);
    }

    @Override
    public Set<JsonPointer> getPointers() {
        return jsonFieldSelector.getPointers();
    }

    @Override
    public int getSize() {
        return jsonFieldSelector.getSize();
    }

    @Override
    public boolean isEmpty() {
        return jsonFieldSelector.isEmpty();
    }

    @Override
    public String toString() {
        return jsonFieldSelector.toString();
    }

    @Override
    public Iterator<JsonPointer> iterator() {
        return jsonFieldSelector.iterator();
    }

}
