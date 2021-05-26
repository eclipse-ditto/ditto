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
package org.eclipse.ditto.protocol.mappingstrategies;


import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Map;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.protocol.TopicPath;

/**
 * Provides helper methods to map from {@link Adaptable}s to connectivity commands.
 *
 * @param <T> the type of the mapped signals
 * @since 2.1.0
 */
abstract class AbstractConnectivityMappingStrategies<T extends Jsonifiable.WithPredicate<JsonObject, JsonField>>
        extends AbstractMappingStrategies<T> {

    protected AbstractConnectivityMappingStrategies(final Map<String, JsonifiableMapper<T>> mappingStrategies) {
        super(mappingStrategies);
    }

    /**
     * Connection id from topic path connection id.
     *
     * @param topicPath the topic path
     * @return the connection id
     */
    protected static ConnectionId connectionIdFromTopicPath(final TopicPath topicPath) {
        checkNotNull(topicPath, "topicPath");
        return ConnectionId.of(topicPath.getEntityName());
    }

    /**
     * @throws NullPointerException if the value is null.
     */
    protected static JsonObject getValueFromPayload(final Adaptable adaptable) {
        final JsonValue value = adaptable.getPayload().getValue()
                .filter(JsonValue::isObject)
                .orElseThrow(() -> new NullPointerException("Payload value must be a non-null object."));
        return value.asObject();
    }

}
