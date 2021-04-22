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
package org.eclipse.ditto.things.model;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * {@code Attributes} describe other entities (e. g. a {@code Thing}) in more detail and can be of any type. Attributes
 * can also be used to find entities. Attributes are typically used to model rather static properties of an entity.
 * Static means that the values do not change as frequently as property values of Features for example.
 */
@Immutable
public interface Attributes extends JsonObject, Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a new empty builder for a {@code Attributes}.
     *
     * @return the builder.
     */
    static AttributesBuilder newBuilder() {
        return AttributesModelFactory.newAttributesBuilder();
    }

    /**
     * Returns a mutable builder with a fluent API for immutable {@code Attributes}. The builder is initialised with the
     * entries of this instance.
     *
     * @return the new builder.
     */
    @Override
    default AttributesBuilder toBuilder() {
        return AttributesModelFactory.newAttributesBuilder(this);
    }

    @Override
    Attributes setValue(CharSequence key, int value);

    @Override
    Attributes setValue(CharSequence key, long value);

    @Override
    Attributes setValue(CharSequence key, double value);

    @Override
    Attributes setValue(CharSequence key, boolean value);

    @Override
    Attributes setValue(CharSequence key, String value);

    @Override
    Attributes setValue(CharSequence key, JsonValue value);

    @Override
    Attributes set(JsonField field);

    @Override
    Attributes setAll(Iterable<JsonField> jsonFields);

    @Override
    Attributes remove(CharSequence key);

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return get(fieldSelector);
    }

}
