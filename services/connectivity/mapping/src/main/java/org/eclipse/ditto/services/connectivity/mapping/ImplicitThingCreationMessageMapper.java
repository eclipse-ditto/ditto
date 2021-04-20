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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.Placeholders;
import org.eclipse.ditto.model.base.entity.id.NamespacedEntityIdInvalidException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTagMatcher;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.services.models.placeholders.ExpressionResolver;
import org.eclipse.ditto.services.models.placeholders.HeadersPlaceholder;
import org.eclipse.ditto.services.models.placeholders.PlaceholderFactory;
import org.eclipse.ditto.services.models.placeholders.PlaceholderFilter;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.services.connectivity.config.mapping.MappingConfig;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.base.GlobalErrorRegistry;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;

/**
 * This mapper creates a {@link org.eclipse.ditto.signals.commands.things.modify.CreateThing} command from
 * a given thing template and may substitutes placeholders by given headers which can be {@code device_id},
 * {@code entity_id} or {@code gateway_id}.
 * The thingId must be set in the mapping configuration. It can either be a fixed Thing ID
 * or it can be resolved from the message headers by using a placeholder e.g. {@code {{ header:device_id }}}.
 * The policyId is not required to be set in the mapping configuration. If not set, the policyId will be the same as
 * the thingId.
 *
 * @since 1.3.0
 */
@PayloadMapper(
        alias = "ImplicitThingCreation",
        requiresMandatoryConfiguration = true // "thing" is mandatory configuration
)
public final class ImplicitThingCreationMessageMapper extends AbstractMessageMapper {

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(ImplicitThingCreationMessageMapper.class);

    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();
    private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();
    private static final String THING_TEMPLATE = "thing";
    private static final String ALLOW_POLICY_LOCKOUT_OPTION = "allowPolicyLockout";
    private static final String COMMAND_HEADERS = "commandHeaders";
    private static final String THING_ID = "thingId";
    private static final String THING_ID_CONFIGURATION_PROPERTY = THING_TEMPLATE + "/" + THING_ID;
    private static final String POLICY_ID = "policyId";
    private static final String POLICY_ID_CONFIGURATION_PROPERTY = THING_TEMPLATE + "/" + POLICY_ID;

    private String thingTemplate;
    private Map<String, String> commandHeaders;
    private boolean allowPolicyLockout;

    @Override
    protected void doConfigure(final MappingConfig mappingConfig, final MessageMapperConfiguration configuration) {
        thingTemplate = configuration.findProperty(THING_TEMPLATE).orElseThrow(
                () -> MessageMapperConfigurationInvalidException.newBuilder(THING_TEMPLATE).build());

        commandHeaders = configuration.findProperty(COMMAND_HEADERS, JsonValue::isObject, JsonValue::asObject)
                .filter(configuredHeaders -> !configuredHeaders.isEmpty())
                .map(configuredHeaders -> {
                    final Map<String, String> newCommandHeaders = new HashMap<>();
                    for (final JsonField field : configuredHeaders) {
                        newCommandHeaders.put(field.getKeyName(), field.getValue().formatAsString());
                    }
                    return Collections.unmodifiableMap(newCommandHeaders);
                })
                .orElse(Map.of());

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

        LOGGER.debug("Configured with Thing template: {}", thingTemplate);

        allowPolicyLockout = configuration.findProperty(ALLOW_POLICY_LOCKOUT_OPTION).map(Boolean::valueOf).orElse(true);
    }

    private static void validateThingEntityId(final String thingId) {
        try {
            if (!Placeholders.containsAnyPlaceholder(thingId)) {
                ThingId.of(thingId);
            }
        } catch (final NamespacedEntityIdInvalidException e) {
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
        } catch (final NamespacedEntityIdInvalidException e) {
            throw MessageMapperConfigurationInvalidException.newBuilder(POLICY_ID_CONFIGURATION_PROPERTY)
                    .message(e.getMessage())
                    .description(e.getDescription().orElse("Make sure to use a valid Policy ID."))
                    .build();
        }
    }

    @Override
    public List<Adaptable> map(final ExternalMessage message) {
        LOGGER.withCorrelationId(message.getInternalHeaders()).debug("Received ExternalMessage: {}", message);

        final Map<String, String> externalHeaders = message.getHeaders();
        final ExpressionResolver expressionResolver = getExpressionResolver(externalHeaders);

        final String resolvedTemplate;
        if (Placeholders.containsAnyPlaceholder(thingTemplate)) {
            resolvedTemplate = applyPlaceholderReplacement(thingTemplate, expressionResolver);
        } else {
            resolvedTemplate = thingTemplate;
        }

        if (Placeholders.containsAnyPlaceholder(thingTemplate)) {
            commandHeaders = resolveCommandHeaders(message, commandHeaders);
        }

        final Signal<CreateThing> createThing = getCreateThingSignal(message, resolvedTemplate);
        final Adaptable adaptable = DITTO_PROTOCOL_ADAPTER.toAdaptable(createThing);

        // we cannot set the header on CreateThing directly because it is filtered when mapped to an adaptable
        final DittoHeaders modifiedHeaders = adaptable.getDittoHeaders().toBuilder()
                .allowPolicyLockout(allowPolicyLockout)
                .ifNoneMatch(EntityTagMatchers.fromList(Collections.singletonList(EntityTagMatcher.asterisk())))
                .build();
        final Adaptable adaptableWithModifiedHeaders = adaptable.setDittoHeaders(modifiedHeaders);

        LOGGER.withCorrelationId(message.getInternalHeaders())
                .debug("Mapped ExternalMessage to Adaptable: {}", adaptableWithModifiedHeaders);

        return Collections.singletonList(adaptableWithModifiedHeaders);
    }

    private static ExpressionResolver getExpressionResolver(final Map<String, String> headers) {
        return PlaceholderFactory.newExpressionResolver(
                PlaceholderFactory.newPlaceholderResolver(HEADERS_PLACEHOLDER, headers));
    }

    private static String applyPlaceholderReplacement(final String template, final ExpressionResolver resolver) {
        return PlaceholderFilter.apply(template, resolver);
    }

    private Signal<CreateThing> getCreateThingSignal(final ExternalMessage message, final String template) {
        final JsonObject thingJson = wrapJsonRuntimeException(() -> JsonFactory.newObject(template));
        final Thing newThing = ThingsModelFactory.newThing(thingJson);
        final JsonObject inlinePolicyJson = createInlinePolicyJson(thingJson);
        final String copyPolicyFrom = getCopyPolicyFrom(thingJson);
        final DittoHeaders dittoHeaders = message.getInternalHeaders().toBuilder()
                .putHeaders(commandHeaders)
                .build();
        return CreateThing.of(newThing, inlinePolicyJson, copyPolicyFrom, dittoHeaders);
    }

    private static Map<String, String> resolveCommandHeaders(final ExternalMessage externalMessage,
            final Map<String, String> errorResponseHeaders) {
        final ExpressionResolver resolver = getExpressionResolver(externalMessage.getHeaders());
        final Map<String, String> resolvedHeaders = new HashMap<>();
        errorResponseHeaders.forEach((key, value) ->
                resolver.resolve(value).toOptional().ifPresent(resolvedHeaderValue ->
                        resolvedHeaders.put(key, resolvedHeaderValue)
                )
        );
        // throws IllegalArgumentException or SubjectInvalidException
        // if resolved headers are missing mandatory headers or the resolved subject is invalid.
        return resolvedHeaders;
    }

    @Nullable
    private static JsonObject createInlinePolicyJson(final JsonObject thingJson) {
        return thingJson.getValue(Policy.INLINED_FIELD_NAME)
                .map(jsonValue -> wrapJsonRuntimeException(jsonValue::asObject))
                .orElse(null);
    }

    @Nullable
    private static String getCopyPolicyFrom(final JsonObject thingJson) {
        return thingJson.getValue(CreateThing.JSON_COPY_POLICY_FROM)
                .orElse(null);
    }

    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        if (TopicPath.Criterion.ERRORS.equals(adaptable.getTopicPath().getCriterion())) {
            adaptable.getPayload().getValue()
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .ifPresentOrElse(jsonObject -> {
                        throw GlobalErrorRegistry.getInstance().parse(jsonObject,
                                adaptable.getDittoHeaders());
                    }, () -> LOGGER.withCorrelationId(adaptable.getDittoHeaders())
                            .warn("Unexpected error adaptable. Expected value of type JsonObject in payload but got " +
                                    "<{}>.", adaptable.getPayload()));
        }
        return Collections.emptyList();
    }

}
