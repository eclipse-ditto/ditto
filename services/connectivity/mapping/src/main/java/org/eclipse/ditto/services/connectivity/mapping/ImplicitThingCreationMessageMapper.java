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

import static org.eclipse.ditto.model.base.exceptions.DittoJsonException.wrapJsonRuntimeException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.Placeholders;
import org.eclipse.ditto.model.base.entity.id.NamespacedEntityIdInvalidException;
import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.placeholders.HeadersPlaceholder;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.model.placeholders.PlaceholderFilter;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;

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

    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();
    private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();
    private static final String THING_TEMPLATE = "thing";
    private static final String THING_ID = "thingId";
    private static final String THING_ID_CONFIGURATION_PROPERTY = THING_TEMPLATE + "/" + THING_ID;
    private static final String POLICY_ID = "policyId";
    private static final String POLICY_ID_CONFIGURATION_PROPERTY = THING_TEMPLATE + "/" + POLICY_ID;

    private String thingTemplate;

    @Override
    protected void doConfigure(final MappingConfig mappingConfig, final MessageMapperConfiguration configuration) {
        thingTemplate = configuration.findProperty(THING_TEMPLATE).orElseThrow(
                () -> MessageMapperConfigurationInvalidException.newBuilder(THING_TEMPLATE).build());

        final JsonObject thingJson = JsonObject.of(thingTemplate);

        thingJson.getValue(THING_ID)
                .map(JsonValue::asString)
                .ifPresentOrElse(ImplicitThingCreationMessageMapper::validateThingEntityId, () -> {
                    throw MessageMapperConfigurationInvalidException.newBuilder(THING_ID_CONFIGURATION_PROPERTY)
                            .build();
                });

        // PolicyId is not required in mapping config. Still needs to be valid if present.
        thingJson.getValue(POLICY_ID)
                .map(JsonValue::asString)
                .ifPresent(ImplicitThingCreationMessageMapper::validatePolicyEntityId);

    }

    private static void validateThingEntityId(final String thingId) {
        try {
            if (!Placeholders.containsAnyPlaceholder(thingId)) {
                ThingId.of(thingId);
            }
        } catch (NamespacedEntityIdInvalidException e) {
            throw MessageMapperConfigurationInvalidException.newBuilder(THING_ID_CONFIGURATION_PROPERTY)
                    .message(e.getMessage())
                    .description(e.getDescription().orElse("Make sure to use a valid Thing ID."))
                    .build();
        }
    }

    private static void validatePolicyEntityId(final String policyId) {
        try {
            if (!Placeholders.containsAnyPlaceholder(policyId)) {
                PolicyId.of(policyId);
            }
        } catch (NamespacedEntityIdInvalidException e) {
            throw MessageMapperConfigurationInvalidException.newBuilder(POLICY_ID_CONFIGURATION_PROPERTY)
                    .message(e.getMessage())
                    .description(e.getDescription().orElse("Make sure to use a valid Policy ID."))
                    .build();
        }
    }

    @Override
    public List<Adaptable> map(final ExternalMessage message) {
        final Map<String, String> externalHeaders = message.getHeaders();
        final ExpressionResolver expressionResolver = getExpressionResolver(externalHeaders);

        if (Placeholders.containsAnyPlaceholder(thingTemplate)) {
            thingTemplate = applyPlaceholderReplacement(thingTemplate, expressionResolver);
        }

        final Signal<CreateThing> createThing = getCreateThingSignal(message);
        final Adaptable adaptable = DITTO_PROTOCOL_ADAPTER.toAdaptable(createThing);

        return Collections.singletonList(adaptable);
    }

    private static ExpressionResolver getExpressionResolver(final Map<String, String> headers) {
        return PlaceholderFactory.newExpressionResolver(
                PlaceholderFactory.newPlaceholderResolver(HEADERS_PLACEHOLDER, headers));
    }

    private static String applyPlaceholderReplacement(final String template, final ExpressionResolver resolver) {
        return PlaceholderFilter.apply(template, resolver);
    }

    private Signal<CreateThing> getCreateThingSignal(final ExternalMessage message) {
        final JsonObject thingJson = wrapJsonRuntimeException(() -> JsonFactory.newObject(thingTemplate));
        final Thing newThing = ThingsModelFactory.newThing(thingJson);
        final JsonObject inlinePolicyJson = createInlinePolicyJson(thingJson);
        final String copyPolicyFrom = getCopyPolicyFrom(thingJson);
        return CreateThing.of(newThing, inlinePolicyJson, copyPolicyFrom, message.getInternalHeaders());
    }

    @Nullable
    private static JsonObject createInlinePolicyJson(final JsonObject thingJson) {
        return thingJson.getValue(Policy.INLINED_FIELD_NAME)
                .map(jsonValue -> wrapJsonRuntimeException(jsonValue::asObject))
                .orElse(null);
    }

    @Nullable
    private static String getCopyPolicyFrom(final JsonObject thingJson) {
        return thingJson.getValue(ModifyThing.JSON_COPY_POLICY_FROM)
                .orElse(null);
    }

    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        return Collections.emptyList();
    }

}
