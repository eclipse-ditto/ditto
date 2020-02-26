/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter.adaptables;

import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableMapper;

/**
 * Provides helper methods to map from {@link Adaptable}s to search commands and events.
 *
 * @param <T> the type of the mapped signals
 */
abstract class AbstractSearchMappingStrategies<T extends Jsonifiable.WithPredicate<JsonObject, JsonField>>
        extends AbstractMappingStrategies<T> {


    protected AbstractSearchMappingStrategies(final Map<String, JsonifiableMapper<T>> mappingStrategies) {
        super(mappingStrategies);
    }

    @Nullable
    protected static JsonFieldSelector selectedFieldsFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getFields().orElse(null);
    }

    protected static @Nullable String subscriptionIdFrom(final Adaptable adaptable) {

        if (adaptable.getPayload().getValue().isPresent()) {
            final JsonObject value = JsonObject.of(
                    adaptable
                            .getPayload()
                            .getValue()
                            .map(JsonValue::formatAsString)
                            .orElseThrow(() -> JsonParseException.newBuilder().build()));

            return value.getValue("subscriptionId").map(JsonValue::asString).orElse(null);
        }
        return null;
    }

}
