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
package org.eclipse.ditto.protocol.mappingstrategies;

import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionEvent;

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

    @Nullable
    protected static String subscriptionIdFrom(final Adaptable adaptable) {
        return getFromValue(adaptable, SubscriptionEvent.JsonFields.SUBSCRIPTION_ID).orElse(null);
    }

    static <T> Optional<T> getFromValue(final Adaptable adaptable, final JsonFieldDefinition<T> jsonFieldDefinition) {
        return adaptable.getPayload().getValue().flatMap(value -> value.asObject().getValue(jsonFieldDefinition));
    }

}
