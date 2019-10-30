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
package org.eclipse.ditto.services.connectivity.mapping;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.common.Placeholders;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.MessageMappingFailedException;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.placeholders.HeadersPlaceholder;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.model.placeholders.PlaceholderFilter;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This mapper extracts the headers {@code creation-time} and {@code ttd} from the message and builds a
 * {@link ModifyFeature} command from it. The default featureId is {@code ConnectionStatus} but can be changed via
 * the mapping configuration. The thingId must be set in the mapping configuration. It can either be a fixed Thing ID
 * or it can be resolved from the message headers by using a placeholder e.g. {@code {{ header:device_id }}}.
 */
@PayloadMapper(
        alias = "ConnectionStatus",
        requiresMandatoryConfiguration = true // "thingId" is mandatory configuration
)
public class ConnectionStatusMessageMapper extends AbstractMessageMapper {

    static final String HEADER_HONO_TTD = "ttd";
    static final String HEADER_HONO_CREATION_TIME = "creation-time";
    static final String DEFAULT_FEATURE_ID = "ConnectionStatus";

    static final String MAPPING_OPTIONS_PROPERTIES_THING_ID = "thingId";
    static final String MAPPING_OPTIONS_PROPERTIES_FEATURE_ID = "featureId";

    private static final String FEATURE_DEFINITION = "com.bosch.iot.suite.standard:ConnectionStatus:1.0.0";
    private static final String FEATURE_PROPERTY_READY_SINCE = "readySince";
    private static final String FEATURE_PROPERTY_READY_UNTIL = "readyUntil";

    public static final Logger LOGGER = LoggerFactory.getLogger(ConnectionStatusMessageMapper.class);

    // (unix time) 253402300799 = (ISO-8601) 9999-12-31T23:59:59
    private static final String FUTURE_INSTANT = "253402300799";
    private static final List<Adaptable> EMPTY_RESULT = Collections.emptyList();
    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();
    private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();

    private String featureId;
    @Nullable private String mappingOptionThingId;

    /**
     * Constructs a new {@code ConnectionStatusMessageMapper} instance.
     * This constructor is required as the the instance is created via reflection.
     */
    public ConnectionStatusMessageMapper() {}

    @Override
    public void doConfigure(final MappingConfig mappingConfig,
            final MessageMapperConfiguration messageMapperConfiguration) {
        mappingOptionThingId = messageMapperConfiguration.findProperty(MAPPING_OPTIONS_PROPERTIES_THING_ID)
                .orElseThrow(
                        () -> MessageMapperConfigurationInvalidException.newBuilder(MAPPING_OPTIONS_PROPERTIES_THING_ID)
                                .build());
        //Check if ThingId is valid when its not a placeholder
        if (!Placeholders.containsAnyPlaceholder(mappingOptionThingId)) {
            try {
                ThingId.of(mappingOptionThingId);
            } catch (final ThingIdInvalidException e) {
                throw MessageMapperConfigurationInvalidException.newBuilder(MAPPING_OPTIONS_PROPERTIES_THING_ID)
                        .message(e.getMessage())
                        .description(e.getDescription().orElse("Make sure to use a valid Thing ID."))
                        .build();
            }
        }

        featureId = messageMapperConfiguration.findProperty(MAPPING_OPTIONS_PROPERTIES_FEATURE_ID)
                .orElse(DEFAULT_FEATURE_ID);
    }

    @Override
    public List<Adaptable> map(final ExternalMessage externalMessage) {
        try {
            return doMap(externalMessage);
        } catch (final Exception e) {
            // we don't want to throw an exception in case something went wrong during the mapping
            LOGGER.info("Error occurred during mapping: {}", e.getMessage());
            return EMPTY_RESULT;
        }
    }

    private List<Adaptable> doMap(final ExternalMessage externalMessage) {

        final String contentType = externalMessage.getHeaders().get(DittoHeaderDefinition.CONTENT_TYPE.getKey());

        if (mappingOptionThingId == null) {
            throw getMappingFailedException(
                    String.format("Mapping option '%s' is not set.", MAPPING_OPTIONS_PROPERTIES_THING_ID),
                    contentType);
        }

        //Read thingId
        final ThingId thingId = extractThingId(mappingOptionThingId, externalMessage.getHeaders());

        //Check if time is convertible
        final long creationTime = extractLongHeader(externalMessage.getHeaders(), HEADER_HONO_CREATION_TIME);
        final long ttd = extractLongHeader(externalMessage.getHeaders(), HEADER_HONO_TTD);

        if (creationTime < 0) {
            throw getMappingFailedException(String.format("Invalid value in header '%s': %d.",
                    HEADER_HONO_CREATION_TIME, creationTime), contentType);
        }

        if (ttd < -1) {
            throw getMappingFailedException(String.format("Invalid value in header '%s': %d.",
                    HEADER_HONO_TTD, ttd), contentType);
        }

        //Set time to ISO-8601 UTC
        final String readySince = Instant.ofEpochMilli(creationTime).toString();
        final String readyUntil;
        final Adaptable adaptable;

        if (ttd == 0) {
            readyUntil = Instant.ofEpochMilli(creationTime).toString();
            adaptable = getModifyFeatureAdaptable(thingId, readyUntil, null);
        } else if (ttd == -1) {
            readyUntil = Instant.ofEpochMilli(Long.parseLong(FUTURE_INSTANT)).toString();
            adaptable = getModifyFeatureAdaptable(thingId, readyUntil, readySince);
        } else {
            readyUntil = Instant.ofEpochMilli(creationTime + ttd * 1000).toString();
            adaptable = getModifyFeatureAdaptable(thingId, readyUntil, readySince);
        }

        return Collections.singletonList(adaptable);
    }

    @Nonnull
    private Adaptable getModifyFeatureAdaptable(final ThingId thingId, final String readyUntil,
            @Nullable final String readySince) {
        final FeatureProperties featureProperties = FeatureProperties.newBuilder()
                .set(FEATURE_PROPERTY_READY_SINCE, readySince)
                .set(FEATURE_PROPERTY_READY_UNTIL, readyUntil)
                .build();

        final FeatureDefinition featureDefinition = FeatureDefinition.fromIdentifier(FEATURE_DEFINITION);

        final Feature feature = Feature.newBuilder()
                .definition(featureDefinition)
                .properties(featureProperties)
                .withId(featureId)
                .build();

        LOGGER.debug("Feature created by mapping: {}", feature);

        final ModifyFeature modifyFeature =
                ModifyFeature.of(thingId, feature, DittoHeaders.newBuilder().responseRequired(false).build());
        return DITTO_PROTOCOL_ADAPTER.toAdaptable(modifyFeature);
    }

    private ThingId extractThingId(final String mappingOptionThingId, final Map<String, String> headers) {
        final ExpressionResolver expressionResolver = PlaceholderFactory.newExpressionResolver(
                PlaceholderFactory.newPlaceholderResolver(HEADERS_PLACEHOLDER, headers));
        return ThingId.of(PlaceholderFilter.apply(mappingOptionThingId, expressionResolver));
    }

    private MessageMappingFailedException getMappingFailedException(final String message, final String theContentType) {
        return MessageMappingFailedException.newBuilder(theContentType).message(message).build();
    }

    private Long extractLongHeader(final Map<String, String> headers, final String key) {
        final String contentType = headers.get(DittoHeaderDefinition.CONTENT_TYPE.getKey());
        try {
            return Optional.ofNullable(headers.get(key))
                    .map(Long::parseLong)
                    .orElseThrow(() -> getMappingFailedException(String.format("Header '%s' is not set.", key),
                            contentType));
        } catch (NumberFormatException e) {
            throw getMappingFailedException(String.format("Header '%s' is not convertible to type long.", key),
                    contentType);
        }
    }

    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        return Collections.emptyList();
    }
}
