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

package org.eclipse.ditto.services.connectivity.mapping;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.model.base.common.Placeholders;
import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.placeholders.HeadersPlaceholder;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.model.placeholders.PlaceholderFilter;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;

/**
 * This mapper extracts the headers {@code device_id} and {@code entity_id} from the message and builds a
 * {@link org.eclipse.ditto.signals.commands.things.modify.CreateThing} command from it.
 * The policyId must not be set in the mapping configuration. If not set the policyId will be the same as the thingId.
 * The thingId must be set in the mapping configuration. It can either be a fixed Thing ID
 * or it can be resolved from the message headers by using a placeholder e.g. {@code {{ header:device_id }}}.
 *
 * @since TODO
 */
@PayloadMapper(
        alias = "ImplicitThingCreation",
        requiresMandatoryConfiguration = true // "thingId" is mandatory configuration
)
public class ImplicitThingCreationMessageMapper extends AbstractMessageMapper {

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(ConnectionStatusMessageMapper.class);

    private static final List<Adaptable> EMPTY_RESULT = Collections.emptyList();
    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();

    static final String HONO_DEVICE_ID = "device_id";
    static final String OPTIONAL_ENTITY_ID = "entity_id";

    static final String MAPPING_OPTIONS_PROPERTIES_THING_ID = "thingId";

    private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();

    private String mappingOptionThingId;
    private String mappingOptionPolicyId;

    @Override
    protected void doConfigure(final MappingConfig mappingConfig, final MessageMapperConfiguration configuration) {

        mappingOptionThingId = configuration.findProperty(HONO_DEVICE_ID)
                .orElseThrow(
                        () -> MessageMapperConfigurationInvalidException.newBuilder(HONO_DEVICE_ID)
                                .build());

        // Check if ThingId is valid when its not a placeholder
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

        mappingOptionPolicyId = configuration.findProperty(OPTIONAL_ENTITY_ID)
                .orElse(mappingOptionThingId);

    }

    @Override
    public List<Adaptable> map(final ExternalMessage message) {
        try {
            return doMap(message);
        } catch (final Exception e) {
            // we don't want to throw an exception in case something went wrong during the mapping
            LOGGER.withCorrelationId(message.getInternalHeaders())
                    .info("Error occurred during mapping: <{}>: {}", e.getClass().getSimpleName(), e.getMessage());
            return EMPTY_RESULT;
        }
    }

    private List<Adaptable> doMap(final ExternalMessage message) {

        final Map<String, String> externalHeaders = message.getHeaders();

        final ExpressionResolver expressionResolver = getExpressionResolver(externalHeaders);

        final ThingId thingId = ThingId.of(applyPlaceholderReplacement(mappingOptionThingId, expressionResolver));
        final PolicyId policyId = PolicyId.of(applyPlaceholderReplacement(mappingOptionPolicyId, expressionResolver));

        Thing thing = ThingsModelFactory.newThingBuilder()
                .setEmptyAttributes()
                .setEmptyFeatures()
                .setId(thingId)
                .setPolicyId(policyId)
                .build();

        final Adaptable adaptable;

        adaptable = DITTO_PROTOCOL_ADAPTER.toAdaptable(CreateThing.of(thing, null, message.getInternalHeaders()));

        return Collections.singletonList(adaptable);
    }


    private String applyPlaceholderReplacement(final String template, final ExpressionResolver expressionResolver) {
        return PlaceholderFilter.apply(template, expressionResolver);
    }

    private static ExpressionResolver getExpressionResolver(final Map<String, String> headers) {
        return PlaceholderFactory.newExpressionResolver(
                PlaceholderFactory.newPlaceholderResolver(HEADERS_PLACEHOLDER, headers));
    }

    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        return Collections.emptyList();
    }

}
