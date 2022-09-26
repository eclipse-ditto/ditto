/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.WithStreamingSubscriptionId;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.TopicPath;

/**
 * Provides helper methods to map from {@link Adaptable}s to streaming subscription commands and events.
 *
 * @param <T> the type of the mapped signals
 */
abstract class AbstractStreamingSubscriptionMappingStrategies<T extends Jsonifiable.WithPredicate<JsonObject, JsonField>>
        extends AbstractMappingStrategies<T> {

    protected AbstractStreamingSubscriptionMappingStrategies(final Map<String, JsonifiableMapper<T>> mappingStrategies) {
        super(mappingStrategies);
    }

    protected static NamespacedEntityId entityIdFrom(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        return NamespacedEntityId.of(topicPath.getGroup().getEntityType(),
                topicPath.getNamespace() + ":" + topicPath.getEntityName());
    }

    protected static EntityType entityTypeFrom(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        return topicPath.getGroup().getEntityType();
    }

    protected static JsonPointer resourcePathFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getPath();
    }

    protected static String subscriptionIdFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getValue()
                .map(value -> value.asObject().getValueOrThrow(WithStreamingSubscriptionId.JsonFields.SUBSCRIPTION_ID))
                .orElseThrow(() -> JsonMissingFieldException.newBuilder()
                        .fieldName(Payload.JsonFields.VALUE.getPointer())
                        .build()
                );
    }

    static <T> Optional<T> getFromValue(final Adaptable adaptable, final JsonFieldDefinition<T> jsonFieldDefinition) {
        return adaptable.getPayload().getValue().flatMap(value -> value.asObject().getValue(jsonFieldDefinition));
    }

}
