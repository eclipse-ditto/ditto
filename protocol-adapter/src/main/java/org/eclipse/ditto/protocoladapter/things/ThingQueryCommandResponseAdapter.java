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
package org.eclipse.ditto.protocoladapter.things;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.JsonifiableMapper;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.adaptables.AdaptableConstructor;
import org.eclipse.ditto.protocoladapter.adaptables.AdaptableConstructorFactory;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntryResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributeResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;

/**
 * Adapter for mapping a {@link ThingQueryCommandResponse} to and from an {@link Adaptable}.
 */
final class ThingQueryCommandResponseAdapter extends AbstractThingAdapter<ThingQueryCommandResponse<?>> {

    private final AdaptableConstructor<RetrieveThingsResponse>
            retrieveThingsAdaptableConstructor =
            AdaptableConstructorFactory.newRetrieveThingsResponseAdaptableConstructor();
    private final AdaptableConstructor<ThingQueryCommandResponse<?>>
            thingQueryResponseAdaptableConstructor =
            AdaptableConstructorFactory.newThingQueryResponseAdaptableConstructor();

    private ThingQueryCommandResponseAdapter(
            final Map<String, JsonifiableMapper<ThingQueryCommandResponse<?>>> mappingStrategies,
            final HeaderTranslator headerTranslator) {
        super(mappingStrategies, headerTranslator);
    }

    /**
     * Returns a new ThingQueryCommandResponseAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static ThingQueryCommandResponseAdapter of(final HeaderTranslator headerTranslator) {
        return new ThingQueryCommandResponseAdapter(mappingStrategies(), requireNonNull(headerTranslator));
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    private static Map<String, JsonifiableMapper<ThingQueryCommandResponse<?>>> mappingStrategies() {
        final Map<String, JsonifiableMapper<ThingQueryCommandResponse<?>>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(RetrieveThingResponse.TYPE,
                adaptable -> RetrieveThingResponse.of(thingIdFrom(adaptable), thingFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveThingsResponse.TYPE,
                adaptable -> RetrieveThingsResponse.of(thingsArrayFrom(adaptable),
                        namespaceFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveAclResponse.TYPE,
                adaptable -> RetrieveAclResponse.of(thingIdFrom(adaptable), aclFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveAclEntryResponse.TYPE,
                adaptable -> RetrieveAclEntryResponse.of(thingIdFrom(adaptable), aclEntryFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveAttributesResponse.TYPE,
                adaptable -> RetrieveAttributesResponse.of(thingIdFrom(adaptable), attributesFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveAttributeResponse.TYPE, adaptable -> RetrieveAttributeResponse
                .of(thingIdFrom(adaptable), attributePointerFrom(adaptable), attributeValueFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveThingDefinitionResponse.TYPE,
                adaptable -> RetrieveThingDefinitionResponse.of(thingIdFrom(adaptable), thingDefinitionFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeaturesResponse.TYPE,
                adaptable -> RetrieveFeaturesResponse.of(thingIdFrom(adaptable), featuresFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeatureResponse.TYPE, adaptable -> RetrieveFeatureResponse
                .of(thingIdFrom(adaptable), featureIdFrom(adaptable), featurePropertiesFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeatureDefinitionResponse.TYPE, adaptable -> RetrieveFeatureDefinitionResponse
                .of(thingIdFrom(adaptable), featureIdFrom(adaptable), featureDefinitionFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeaturePropertiesResponse.TYPE, adaptable -> RetrieveFeaturePropertiesResponse
                .of(thingIdFrom(adaptable), featureIdFrom(adaptable), featurePropertiesFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies
                .put(RetrieveFeaturePropertyResponse.TYPE,
                        adaptable -> RetrieveFeaturePropertyResponse.of(thingIdFrom(adaptable),
                                featureIdFrom(adaptable),
                                featurePropertyPointerFrom(adaptable), featurePropertyValueFrom(adaptable),
                                dittoHeadersFrom(adaptable)));

        return mappingStrategies;
    }


    @Override
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        if (topicPath.isWildcardTopic()) {
            return RetrieveThingsResponse.TYPE;
        } else {
            final JsonPointer path = adaptable.getPayload().getPath();
            final String commandName = getAction(topicPath) + upperCaseFirst(pathMatcher.match(path));
            return topicPath.getGroup() + ".responses:" + commandName;
        }
    }

    @Override
    public Adaptable constructAdaptable(final ThingQueryCommandResponse<?> commandResponse,
            final TopicPath.Channel channel) {
        if (commandResponse instanceof RetrieveThingsResponse) {
            return retrieveThingsAdaptableConstructor.construct((RetrieveThingsResponse) commandResponse, channel);
        } else {
            return thingQueryResponseAdaptableConstructor.construct(commandResponse, channel);
        }
    }

}
