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

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingsResponse;

public final class RetrieveThingsCommandResponseMappingStrategies
        extends AbstractThingMappingStrategies<RetrieveThingsResponse> {

    private static final RetrieveThingsCommandResponseMappingStrategies INSTANCE =
            new RetrieveThingsCommandResponseMappingStrategies();

    private RetrieveThingsCommandResponseMappingStrategies() {
        super(initMappingStrategies());
    }

    public static RetrieveThingsCommandResponseMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<RetrieveThingsResponse>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<RetrieveThingsResponse>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(RetrieveThingsResponse.TYPE,
                AdaptableToSignalMapper.of(RetrieveThingsResponse.class,
                        context -> {
                            final JsonArray thingsJsonArray = getThingsArrayOrThrow(context);
                            return RetrieveThingsResponse.newInstance(thingsJsonArray,
                                    thingsJsonArray.toString(),
                                    context.getNamespace().orElse(null),
                                    context.getHttpStatusOrThrow(),
                                    context.getDittoHeaders());
                        }));

        return mappingStrategies;
    }

    private static JsonArray getThingsArrayOrThrow(final MappingContext mappingContext) {
        final Adaptable adaptable = mappingContext.getAdaptable();
        final Payload payload = adaptable.getPayload();
        final Optional<JsonValue> payloadValueOptional = payload.getValue();
        if (payloadValueOptional.isPresent()) {
            final JsonValue jsonValue = payloadValueOptional.get();
            if (jsonValue.isArray()) {
                return jsonValue.asArray();
            } else {
                throw new IllegalAdaptableException(
                        MessageFormat.format("Payload value is not a JSON array of things but <{0}>.", jsonValue),
                        "Please ensure that the payload contains a valid JSON array of things as value.",
                        adaptable.getDittoHeaders()
                );
            }
        } else {
            throw new IllegalAdaptableException(
                    "Payload does not contain an array of things because it has no value at all.",
                    "Please ensure that the payload contains a valid JSON array of things as value.",
                    adaptable.getDittoHeaders()
            );
        }
    }

}
