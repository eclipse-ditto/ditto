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

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.Placeholders;
import org.eclipse.ditto.model.base.entity.id.NamespacedEntityIdInvalidException;
import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.placeholders.HeadersPlaceholder;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.model.placeholders.PlaceholderFilter;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;

/**
 * This mapper creates a {@link org.eclipse.ditto.signals.commands.things.modify.CreateThing} command from
 * a given thing template and may substitutes placeholders by given headers which can be {@code device_id},
 * {@code entity_id} or {@code gateway_id}.
 * The policyId must not be set in the mapping configuration. If not set, the policyId will be the same as the thingId.
 * The thingId must be set in the mapping configuration. It can either be a fixed Thing ID
 * or it can be resolved from the message headers by using a placeholder e.g. {@code {{ header:device_id }}}.
 *
 * @since 1.2.0
 */
@PayloadMapper(
        alias = "ImplicitThingCreation",
        requiresMandatoryConfiguration = true // "thing" is mandatory configuration
)
public class ImplicitThingCreationMessageMapper extends AbstractMessageMapper {

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(ConnectionStatusMessageMapper.class);

    private static final List<Adaptable> EMPTY_RESULT = Collections.emptyList();
    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();

    static final String THING_TEMPLATE = "thing";
    static final String THING_ID = "thingId";
    static final String POLICY_ID = "policyId";

    private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();

    private String mappingOptionThingTemplate;

    @Override
    protected void doConfigure(final MappingConfig mappingConfig, final MessageMapperConfiguration configuration) {

        mappingOptionThingTemplate = configuration.findProperty(THING_TEMPLATE)
                .orElseThrow(
                        () -> MessageMapperConfigurationInvalidException.newBuilder(THING_TEMPLATE).build()
                );

        // Check if thing and policy ID are present and valid if they are not placeholders.
        final JsonObject thingTemplate = JsonObject.of(mappingOptionThingTemplate);

        final JsonValue thingId = thingTemplate.getField(THING_ID)
                .orElseThrow(() -> MessageMapperConfigurationInvalidException.newBuilder(THING_ID).build())
                .getValue();

        // Do not throw an exception because policyId is not required in mapping config. But still needs to be valid if
        // given.
        final JsonValue policyId =
                thingTemplate.getField(POLICY_ID).isPresent() ?
                        thingTemplate.getField(POLICY_ID).get().getValue() : thingId;

        try {

            if (!Placeholders.containsAnyPlaceholder(thingId.asString())) {
                ThingId.of(thingId.asString());
            }

            if (!Placeholders.containsAnyPlaceholder(policyId.asString())) {
                PolicyId.of(policyId.asString());
            }

        } catch (final NamespacedEntityIdInvalidException e) {
            throw MessageMapperConfigurationInvalidException.newBuilder("device/entity_id")
                    .message(e.getMessage())
                    .description(e.getDescription().orElse("Make sure to use valid thing and/or policy ID."))
                    .build();
        }

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

    private List<Adaptable> doMap(final ExternalMessage externalMessage) {

        final Map<String, String> externalHeaders = externalMessage.getHeaders();

        final ExpressionResolver expressionResolver = getExpressionResolver(externalHeaders);

        if (Placeholders.containsAnyPlaceholder(mappingOptionThingTemplate)) {
            mappingOptionThingTemplate = applyPlaceholderReplacement(mappingOptionThingTemplate, expressionResolver);
        }

        final Thing newThing = ThingsModelFactory.newThing(mappingOptionThingTemplate);

        final CreateThing createThing = CreateThing.of(newThing, null, externalMessage.getInternalHeaders());

        final Adaptable adaptable = DITTO_PROTOCOL_ADAPTER.toAdaptable(createThing);

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
