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
package org.eclipse.ditto.connectivity.service.mapping;

import static org.eclipse.ditto.placeholders.PlaceholderFilter.apply;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.Placeholders;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.MessageMappingFailedException;
import org.eclipse.ditto.connectivity.service.config.mapping.MappingConfig;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.HeadersPlaceholder;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeature;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperty;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * This mapper extracts the headers {@code creation-time} and {@code ttd} from the message and builds a
 * {@link ModifyFeature} command from it. The default featureId is {@code ConnectionStatus} but can be changed via
 * the mapping configuration. The thingId must be set in the mapping configuration. It can either be a fixed Thing ID
 * or it can be resolved from the message headers by using a placeholder e.g. {@code {{ header:device_id }}}.
 */
public class ConnectionStatusMessageMapper extends AbstractMessageMapper {

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(ConnectionStatusMessageMapper.class);

    private static final String PAYLOAD_MAPPER_ALIAS = "ConnectionStatus";

    static final String HEADER_HONO_TTD = "ttd";
    static final String HEADER_HONO_CREATION_TIME = "creation-time";
    static final String DEFAULT_FEATURE_ID = "ConnectionStatus";

    static final String MAPPING_OPTIONS_PROPERTIES_THING_ID = "thingId";
    static final String MAPPING_OPTIONS_PROPERTIES_FEATURE_ID = "featureId";

    static final String FEATURE_DEFINITION = "org.eclipse.ditto:ConnectionStatus:1.0.0";
    static final String FEATURE_PROPERTY_CATEGORY_STATUS = "status";
    static final String FEATURE_PROPERTY_READY_SINCE = "readySince";
    static final String FEATURE_PROPERTY_READY_UNTIL = "readyUntil";

    // (unix time) 253402300799 = (ISO-8601) 9999-12-31T23:59:59
    private static final Instant DISTANT_FUTURE_INSTANT = Instant.ofEpochSecond(253402300799L);

    private static final List<Adaptable> EMPTY_RESULT = Collections.emptyList();
    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();
    private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();

    private String mappingOptionThingId;
    private String mappingOptionFeatureId;

    /**
     * Constructs a new instance of ConnectionStatusMessageMapper extension.
     *
     * @param actorSystem the actor system in which to load the extension.
     * @param config the configuration for this extension.
     */
    ConnectionStatusMessageMapper(final ActorSystem actorSystem, final Config config) {
        super(actorSystem, config);
    }

    private ConnectionStatusMessageMapper(final ConnectionStatusMessageMapper copyFromMapper) {
        super(copyFromMapper);
        this.mappingOptionThingId = copyFromMapper.mappingOptionThingId;
        this.mappingOptionFeatureId = copyFromMapper.mappingOptionFeatureId;
    }

    @Override
    public String getAlias() {
        return PAYLOAD_MAPPER_ALIAS;
    }

    /**
     * "thingId" is mandatory.
     */
    @Override
    public boolean isConfigurationMandatory() {
        return true;
    }

    @Override
    public MessageMapper createNewMapperInstance() {
        // ConnectionStatusMessageMapper is stateful - so always return new instance:
        return new ConnectionStatusMessageMapper(this);
    }

    @Override
    public void doConfigure(final Connection connection, final MappingConfig mappingConfig,
            final MessageMapperConfiguration messageMapperConfiguration) {
        mappingOptionThingId = messageMapperConfiguration.findProperty(MAPPING_OPTIONS_PROPERTIES_THING_ID)
                .orElseThrow(
                        () -> MessageMapperConfigurationInvalidException.newBuilder(MAPPING_OPTIONS_PROPERTIES_THING_ID)
                                .build());
        // Check if ThingId is valid when it's not a placeholder
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

        mappingOptionFeatureId = messageMapperConfiguration.findProperty(MAPPING_OPTIONS_PROPERTIES_FEATURE_ID)
                .orElse(DEFAULT_FEATURE_ID);
    }

    @Override
    public List<Adaptable> map(final ExternalMessage externalMessage) {
        try {
            return doMap(externalMessage);
        } catch (final Exception e) {
            final DittoHeaders dittoHeaders;
            if (e instanceof WithDittoHeaders withDittoHeaders) {
                dittoHeaders = withDittoHeaders.getDittoHeaders();
            } else {
                dittoHeaders = externalMessage.getInternalHeaders();
            }
            // we don't want to throw an exception in case something went wrong during the mapping
            LOGGER.withCorrelationId(dittoHeaders)
                    .info("Error occurred during mapping: <{}>: {}", e.getClass().getSimpleName(), e.getMessage());
            return EMPTY_RESULT;
        }
    }

    @Override
    public DittoHeaders getAdditionalInboundHeaders(final ExternalMessage message) {
        return DittoHeaders.empty();
    }

    private List<Adaptable> doMap(final ExternalMessage externalMessage) {

        final Map<String, String> externalHeaders = externalMessage.getHeaders();

        final ExpressionResolver expressionResolver = getExpressionResolver(externalHeaders);
        final ThingId thingId = ThingId.of(apply(mappingOptionThingId, expressionResolver));
        final String featureId = apply(mappingOptionFeatureId, expressionResolver);

        //Check if time is convertible
        final long creationTime = extractLongHeader(externalHeaders, HEADER_HONO_CREATION_TIME,
                externalMessage.getInternalHeaders());
        final long ttd = extractLongHeader(externalHeaders, HEADER_HONO_TTD,
                externalMessage.getInternalHeaders());

        if (creationTime < 0) {
            throw getMappingFailedException(String.format("Invalid value in header <%s>: %d.",
                    HEADER_HONO_CREATION_TIME, creationTime),
                    "The creation-time in milliseconds since Epoch has to be " +
                            "a positive number.", externalMessage.getInternalHeaders());
        }
        if (ttd < -1) {
            throw getMappingFailedException(String.format("Invalid value in header <%s>: %d.",
                    HEADER_HONO_TTD, ttd), "The time until disconnect in milliseconds has to be -1, 0 or greater 0.",
                    externalMessage.getInternalHeaders());
        }

        final Instant readySince = Instant.ofEpochMilli(creationTime);
        final Adaptable adaptable;
        if (ttd == 0) {
            final Instant readyUntil = Instant.ofEpochMilli(creationTime);
            adaptable = getModifyFeaturePropertyAdaptable(thingId, featureId, readyUntil,
                    externalMessage.getInternalHeaders());
        } else if (ttd == -1) {
            adaptable = getModifyFeatureAdaptable(thingId, featureId, DISTANT_FUTURE_INSTANT, readySince,
                    externalMessage.getInternalHeaders());
        } else {
            final Instant readyUntil = Instant.ofEpochMilli(creationTime + ttd * 1000);
            adaptable = getModifyFeatureAdaptable(thingId, featureId, readyUntil, readySince,
                    externalMessage.getInternalHeaders());
        }

        return Collections.singletonList(adaptable);
    }

    private Adaptable getModifyFeaturePropertyAdaptable(final ThingId thingId, final String featureId,
            final Instant readyUntil, final DittoHeaders dittoHeaders) {
        LOGGER.debug("Property of feature {} for thing {} adjusted by mapping", featureId, thingId);

        final JsonPointer propertyJsonPointer = JsonFactory.newPointer(
                FEATURE_PROPERTY_CATEGORY_STATUS + "/" + FEATURE_PROPERTY_READY_UNTIL);

        final DittoHeaders newDittoHeaders = dittoHeaders.toBuilder()
                .responseRequired(false) // we never expect a response when updating the ConnectionState
                .acknowledgementRequests(Collections.emptyList())
                .build();
        final ModifyFeatureProperty modifyFeatureProperty =
                ModifyFeatureProperty.of(thingId, featureId, propertyJsonPointer, JsonValue.of(readyUntil.toString()),
                        newDittoHeaders);

        LOGGER.withCorrelationId(newDittoHeaders)
                .debug("ModifyFeatureProperty for ConnectionStatus created by mapper: {}", modifyFeatureProperty);
        return DITTO_PROTOCOL_ADAPTER.toAdaptable(modifyFeatureProperty);
    }

    private Adaptable getModifyFeatureAdaptable(final ThingId thingId, final String featureId, final Instant readyUntil,
            @Nullable final Instant readySince, final DittoHeaders dittoHeaders) {

        final FeatureProperties featureProperties = FeatureProperties.newBuilder()
                .set(FEATURE_PROPERTY_CATEGORY_STATUS, JsonFactory.newObjectBuilder()
                        .set(FEATURE_PROPERTY_READY_SINCE, readySince != null ? readySince.toString() : null)
                        .set(FEATURE_PROPERTY_READY_UNTIL, readyUntil.toString())
                        .build()
                )
                .build();
        final FeatureDefinition featureDefinition = FeatureDefinition.fromIdentifier(FEATURE_DEFINITION);
        final Feature feature = Feature.newBuilder()
                .definition(featureDefinition)
                .properties(featureProperties)
                .withId(featureId)
                .build();

        final DittoHeaders newDittoHeaders = dittoHeaders.toBuilder()
                .responseRequired(false) // we never expect a response when updating the ConnectionState
                .acknowledgementRequests(Collections.emptyList())
                .build();
        final ModifyFeature modifyFeature = ModifyFeature.of(thingId, feature, newDittoHeaders);

        LOGGER.withCorrelationId(newDittoHeaders)
                .debug("ModifyFeature for ConnectionStatus created by mapper: {}", modifyFeature);
        return DITTO_PROTOCOL_ADAPTER.toAdaptable(modifyFeature);
    }

    private static ExpressionResolver getExpressionResolver(final Map<String, String> headers) {
        return PlaceholderFactory.newExpressionResolver(
                PlaceholderFactory.newPlaceholderResolver(HEADERS_PLACEHOLDER, headers));
    }

    private MessageMappingFailedException getMappingFailedException(final String message,
            final String description, final DittoHeaders dittoHeaders) {
        return MessageMappingFailedException.newBuilder(null)
                .message(message)
                .description(description)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    private Long extractLongHeader(final Map<String, String> headers, final String key,
            final DittoHeaders dittoHeaders) {
        try {
            return Optional.ofNullable(headers.get(key))
                    .map(Long::parseLong)
                    .orElseThrow(() -> getMappingFailedException(String.format("Header <%s> is not set.", key),
                            "Make sure to include the header or only activate the mapping for messages containing " +
                                    "the header.", dittoHeaders));
        } catch (final NumberFormatException e) {
            throw getMappingFailedException(String.format("Header <%s> is not convertible to type long.", key),
                    "Make sure that the header is in the right format.", dittoHeaders);
        }
    }

    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", mappingOptionThingId=" + mappingOptionThingId +
                ", mappingOptionFeatureId=" + mappingOptionFeatureId +
                "]";
    }
}
