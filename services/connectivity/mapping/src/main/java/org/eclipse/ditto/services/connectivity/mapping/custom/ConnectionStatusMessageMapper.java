/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.mapping.custom;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.placeholders.HeadersPlaceholder;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.model.placeholders.PlaceholderFilter;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperConfiguration;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionStatusMessageMapper implements MessageMapper {

    public static final String HEADER_HUB_DEVICE_ID = "device_id";
    public static final String HEADER_HUB_TTD = "ttd";
    public static final String HEADER_HUB_CREATION_TIME = "creation-time";

    public static final String MAPPING_OPTIONS_PROPERTIES_THINGID = "thingId";
    public static final String MAPPING_OPTIONS_PROPERTIES_FEATUREID = "featureId";

    public static final String DEFAULT_FEATURE_ID = "ConnectionStatus";
    public static final String FEATURE_DEFINITION = "com.bosch.iot.suite.standard:ConnectionStatus:1.0.0";
    public static final String FEATURE_PROPERTIE_READY_SINCE = "readySince";
    public static final String FEATURE_PROPERTIE_READY_UNTIL = "readyUntil";

    public static final Logger LOGGER = LoggerFactory.getLogger(ConnectionStatusMessageMapper.class);

    private boolean noErrorOccurred;
    private String featureId;
    private Optional<String> mappingOptionThingId;

    private HashMap<String, String> extractedHeader;

    /**
     * Constructs a new {@code CsmPresenceSensorMessageMapper} object.
     * This constructor is required as the the instance is created via reflection.
     */
    public ConnectionStatusMessageMapper() {
        noErrorOccurred = true;
    }

    @Override
    public String getId() {
        return "connectionStatus";
    }

    @Override
    public void configure(final MappingConfig mappingConfig,
            final MessageMapperConfiguration messageMapperConfiguration) {

        mappingOptionThingId = messageMapperConfiguration.findProperty(
                MAPPING_OPTIONS_PROPERTIES_THINGID);    //returns Optional.empty() if option is not set
        featureId = messageMapperConfiguration.findProperty(MAPPING_OPTIONS_PROPERTIES_FEATUREID)
                .orElse(DEFAULT_FEATURE_ID);
    }

    @Override
    public Optional<Adaptable> map(final ExternalMessage externalMessage) {
        //Validate
        extractedHeader =
                extractHeader(externalMessage, HEADER_HUB_TTD, HEADER_HUB_CREATION_TIME, HEADER_HUB_DEVICE_ID);

        checkIfEntriesSet(extractedHeader);

        if (!mappingOptionThingId.isPresent()) {
            noErrorOccurred = false;
            LOGGER.info("Mapping option \"{}\" is not set", MAPPING_OPTIONS_PROPERTIES_THINGID);
        }

        //Check if time is convertible
        long creationTime = 0;
        long ttd = 0;
        try {
            creationTime = Long.parseLong(extractedHeader.get(HEADER_HUB_CREATION_TIME));
            ttd = Long.parseLong(extractedHeader.get(HEADER_HUB_TTD));
        } catch (NumberFormatException e) {
            LOGGER.info("Header \"{}\" or \"{}\" is not convertible to type long", HEADER_HUB_CREATION_TIME,
                    HEADER_HUB_TTD);
            noErrorOccurred = false;
        }

        if (creationTime < 0) {
            LOGGER.info("Undefined value in \"{}\"", HEADER_HUB_CREATION_TIME);
            noErrorOccurred = false;
        }

        if (ttd < -1) {
            LOGGER.info("Undefined value in \"{}\"", HEADER_HUB_CREATION_TIME);
            noErrorOccurred = false;
        }

        //Execute
        if (noErrorOccurred) {

            //Read thingId
            final ThingId thingId;
            if (mappingOptionThingId.get().equals("{{ header:device_id }}")) {
                final HeadersPlaceholder headersPlaceholder = PlaceholderFactory.newHeadersPlaceholder();
                final ExpressionResolver expressionResolver = PlaceholderFactory.newExpressionResolver(
                        PlaceholderFactory.newPlaceholderResolver(headersPlaceholder, extractedHeader));
                thingId = ThingId.of(PlaceholderFilter.apply("{{ header:device_id }}", expressionResolver, false));
            } else {
                thingId = ThingId.of(mappingOptionThingId.get());
            }

            //Set time to ISO-8601 UTC
            String readyUntil;
            if (ttd != -1) {
                readyUntil = Instant.ofEpochSecond(creationTime + ttd).toString();
            } else {
                //(unix time) 253402300799 = (ISO-8601) 9999-12-31T23:59:59
                readyUntil = Instant.ofEpochSecond(Long.parseLong("253402300799")).toString();
            }

            String readySince = Instant.ofEpochSecond(creationTime).toString();

            //Build propertyPath of featureId
            final FeatureProperties featureProperties = FeatureProperties.newBuilder()
                    .set(FEATURE_PROPERTIE_READY_SINCE, readySince)
                    .set(FEATURE_PROPERTIE_READY_UNTIL, readyUntil)
                    .build();

            final FeatureDefinition featureDefinition = FeatureDefinition.fromIdentifier(FEATURE_DEFINITION);

            final Feature feature = Feature.newBuilder()
                    .definition(featureDefinition)
                    .properties(featureProperties)
                    .withId(featureId)
                    .build();

            final ModifyFeature modifyFeature = ModifyFeature.of(thingId, feature, DittoHeaders.empty());
            final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(modifyFeature);

            LOGGER.info("Feature \"{}\" of Thing \"{}\" is modified", featureId, thingId.toString());

            return Optional.of(adaptable);
        } else { return Optional.empty();}
    }

    private HashMap<String, String> extractHeader(final ExternalMessage externalMessage,
            final String... headerToExtract) {

        HashMap<String, String> extractedHeader = new HashMap<>();

        for (String header : headerToExtract) {
            extractedHeader.put(
                    header,
                    externalMessage.findHeader(header).orElse(""));
        }
        return extractedHeader;
    }

    private void checkIfEntriesSet(final HashMap<String, String> extractedHeader) {
        for (Map.Entry<String, String> entry : extractedHeader.entrySet()) {
            if ("".equals(entry.getValue())) {
                LOGGER.info("Header \"{}\" is not set", entry.getKey());
                noErrorOccurred = false;
            }
        }
    }

    @Override
    public Optional<ExternalMessage> map(final Adaptable adaptable) {
        return Optional.empty();
    }
}
