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
package org.eclipse.ditto.protocoladapter;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;

/**
 * Adapter for mapping a {@link RetrieveThingsResponse} to and from an {@link Adaptable}.
 */
final class ThingSearchResponseAdapter extends AbstractAdapter<RetrieveThingsResponse> {

    private ThingSearchResponseAdapter(final Map<String, JsonifiableMapper<RetrieveThingsResponse>> mappingStrategies) {
        super(mappingStrategies);
    }

    /**
     * Returns a new ThingSearchResponseAdapter.
     *
     * @return the adapter.
     */
    public static ThingSearchResponseAdapter newInstance() {
        return new ThingSearchResponseAdapter(mappingStrategies());
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    private static Map<String, JsonifiableMapper<RetrieveThingsResponse>> mappingStrategies() {
        final Map<String, JsonifiableMapper<RetrieveThingsResponse>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(RetrieveThingsResponse.TYPE,
                adaptable -> RetrieveThingsResponse.of(idsArrayFrom(adaptable), dittoHeadersFrom(adaptable)));

        return mappingStrategies;
    }

    private static JsonArray idsArrayFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getValue().filter(JsonValue::isArray).map(JsonValue::asArray).orElseThrow(() ->
                new JsonParseException("Could not map payload to expected JsonArray"));
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        return RetrieveThingsResponse.TYPE;
    }

    @Override
    public Adaptable toAdaptable(final RetrieveThingsResponse commandResponse, final TopicPath.Channel channel) {
        final String responseName = commandResponse.getClass().getSimpleName().toLowerCase();
        if (!responseName.endsWith("response")) {
            throw UnknownCommandResponseException.newBuilder(responseName).build();
        }

        final Payload payload = Payload.newBuilder(commandResponse.getResourcePath()) //
                .withStatus(commandResponse.getStatusCode()) //
                .withValue(commandResponse.getEntity(commandResponse.getImplementedSchemaVersion())) //
                .build();

        return Adaptable.newBuilder(DittoProtocolAdapter.emptyTopicPath()) //
                .withPayload(payload) //
                .withHeaders(DittoProtocolAdapter.newHeaders(commandResponse.getDittoHeaders())) //
                .build();
    }

}
