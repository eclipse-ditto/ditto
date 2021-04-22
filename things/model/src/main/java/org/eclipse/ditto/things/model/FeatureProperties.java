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
 * Represents data related to {@link Feature}s. Properties can be categorized, e. g. to manage the status, the
 * configuration or any fault information. Each property itself can be either a simple (scalar) value or a complex
 * object. Allowed is any JSON object. The FeatureProperties can either represent the desired properties or
 * the actual properties of a {@link Feature}.
 */
@Immutable
public interface FeatureProperties extends JsonObject, Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a new empty builder for an immutable {@code FeatureProperties}.
     *
     * @return the builder.
     */
    static FeaturePropertiesBuilder newBuilder() {
        return ThingsModelFactory.newFeaturePropertiesBuilder();
    }

    /**
     * Returns a new builder for an immutable {@code FeatureProperties} which is initialised with the values of the this
     * FeatureProperties object.
     *
     * @return the new builder.
     */
    @Override
    default FeaturePropertiesBuilder toBuilder() {
        return ThingsModelFactory.newFeaturePropertiesBuilder(this);
    }

    @Override
    FeatureProperties setValue(CharSequence key, int value);

    @Override
    FeatureProperties setValue(CharSequence key, long value);

    @Override
    FeatureProperties setValue(CharSequence key, double value);

    @Override
    FeatureProperties setValue(CharSequence key, boolean value);

    @Override
    FeatureProperties setValue(CharSequence key, String value);

    @Override
    FeatureProperties setValue(CharSequence key, JsonValue value);

    @Override
    FeatureProperties set(JsonField field);

    @Override
    FeatureProperties setAll(Iterable<JsonField> jsonFields);

    @Override
    FeatureProperties remove(CharSequence key);

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return get(fieldSelector);
    }

}
