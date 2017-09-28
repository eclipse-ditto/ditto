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
package org.eclipse.ditto.model.things;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Represents data related to {@link Feature}s. Properties can be categorized, e. g. to manage the status, the
 * configuration or any fault information. Each property itself can be either a simple (scalar) value or a complex
 * object. Allowed is any JSON object.
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
