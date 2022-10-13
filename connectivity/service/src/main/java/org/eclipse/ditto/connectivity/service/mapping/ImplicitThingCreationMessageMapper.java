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
package org.eclipse.ditto.connectivity.service.mapping;

import static org.eclipse.ditto.base.model.exceptions.DittoJsonException.wrapJsonRuntimeException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.common.Placeholders;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityIdInvalidException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatcher;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.base.model.signals.GlobalErrorRegistry;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.connectivity.service.config.mapping.MappingConfig;
import org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders;
import org.eclipse.ditto.edge.service.placeholders.RequestPlaceholder;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.HeadersPlaceholder;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.PlaceholderFilter;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * This mapper creates a {@link org.eclipse.ditto.things.model.signals.commands.modify.CreateThing} command from
 * a given thing template and may substitutes placeholders by given headers which can be {@code device_id},
 * {@code entity_id} or {@code gateway_id}.
 * The thingId must be set in the mapping configuration. It can either be a fixed Thing ID
 * or it can be resolved from the message headers by using a placeholder e.g. {@code {{ header:device_id }}}.
 * The policyId is not required to be set in the mapping configuration. If not set, the policyId will be the same as
 * the thingId.
 *
 * @since 1.3.0
 */
public class ImplicitThingCreationMessageMapper extends AbstractMessageMapper {

    private static final String PAYLOAD_MAPPER_ALIAS = "ImplicitThingCreation";

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(ImplicitThingCreationMessageMapper.class);

    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();
    private static final TimePlaceholder TIME_PLACEHOLDER = TimePlaceholder.getInstance();
    private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();
    private static final RequestPlaceholder REQUEST_PLACEHOLDER = ConnectivityPlaceholders.newRequestPlaceholder();
    private static final String THING_TEMPLATE = "thing";
    private static final String ALLOW_POLICY_LOCKOUT_OPTION = "allowPolicyLockout";
    private static final String COMMAND_HEADERS = "commandHeaders";
    private static final String THING_ID = "thingId";
    private static final String THING_ID_CONFIGURATION_PROPERTY = THING_TEMPLATE + "/" + THING_ID;
    private static final String POLICY_ID = "policyId";
    private static final String POLICY_ID_CONFIGURATION_PROPERTY = THING_TEMPLATE + "/" + POLICY_ID;
    public static final EntityTagMatcher ASTERISK = EntityTagMatcher.asterisk();

    private String thingTemplate;
    private Map<String, String> commandHeaders;
    private boolean allowPolicyLockout;

    /**
     * Constructs a new instance of ImplicitThingCreationMessageMapper extension.
     *
     * @param actorSystem the actor system in which to load the extension.
     * @param config the configuration for this extension.
     */
    public ImplicitThingCreationMessageMapper(final ActorSystem actorSystem, final Config config) {
        super(actorSystem, config);
    }

    protected ImplicitThingCreationMessageMapper(final ImplicitThingCreationMessageMapper copyFromMapper) {
        super(copyFromMapper);
        this.thingTemplate = copyFromMapper.thingTemplate;
        this.commandHeaders = copyFromMapper.commandHeaders;
        this.allowPolicyLockout = copyFromMapper.allowPolicyLockout;
    }

    @Override
    public String getAlias() {
        return PAYLOAD_MAPPER_ALIAS;
    }

    /**
     * "thing" is mandatory.
     */
    @Override
    public boolean isConfigurationMandatory() {
        return true;
    }

    @Override
    public MessageMapper createNewMapperInstance() {
        return new ImplicitThingCreationMessageMapper(this);
    }

    @Override
    protected void doConfigure(final Connection connection, final MappingConfig mappingConfig,
            final MessageMapperConfiguration configuration) {
        thingTemplate = configuration.findProperty(THING_TEMPLATE).orElseThrow(
                () -> MessageMapperConfigurationInvalidException.newBuilder(THING_TEMPLATE).build());

        commandHeaders = configuration.findProperty(COMMAND_HEADERS, JsonValue::isObject, JsonValue::asObject)
                .filter(configuredHeaders -> !configuredHeaders.isEmpty())
                .map(configuredHeaders -> {
                    final Map<String, String> newCommandHeaders = new LinkedHashMap<>();
                    newCommandHeaders.put(DittoHeaderDefinition.IF_NONE_MATCH.getKey(), ASTERISK.toString());
                    for (final JsonField field : configuredHeaders) {
                        newCommandHeaders.put(field.getKeyName(), field.getValue().formatAsString());
                    }
                    return Collections.unmodifiableMap(newCommandHeaders);
                })
                .orElseGet(() -> DittoHeaders.newBuilder()
                        .ifNoneMatch(EntityTagMatchers.fromList(Collections.singletonList(ASTERISK)))
                        .build());

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
        final ExpressionResolver expressionResolver = createExpressionResolver(
                externalHeaders,
                message.getAuthorizationContext().orElse(null)
        );

        final String resolvedTemplate;
        if (Placeholders.containsAnyPlaceholder(thingTemplate)) {
            resolvedTemplate = PlaceholderFilter.apply(thingTemplate, expressionResolver);
        } else {
            resolvedTemplate = thingTemplate;
        }

        commandHeaders = resolveCommandHeaders(expressionResolver, commandHeaders);

        final Signal<CreateThing> createThing = getCreateThingSignal(message, resolvedTemplate);
        final Adaptable adaptable = DITTO_PROTOCOL_ADAPTER.toAdaptable(createThing);

        // we cannot set the header on CreateThing directly because it is filtered when mapped to an adaptable
        final DittoHeaders modifiedHeaders = DittoHeaders.of(commandHeaders).toBuilder()
                .allowPolicyLockout(allowPolicyLockout)
                .build();
        final Adaptable adaptableWithModifiedHeaders = adaptable.setDittoHeaders(modifiedHeaders);

        LOGGER.withCorrelationId(message.getInternalHeaders())
                .debug("Mapped ExternalMessage to Adaptable: {}", adaptableWithModifiedHeaders);

        return Collections.singletonList(adaptableWithModifiedHeaders);
    }

    @Override
    public DittoHeaders getAdditionalInboundHeaders(final ExternalMessage message) {
        return DittoHeaders.empty();
    }

    /**
     * Creates a new {@code ExpressionResolver} based on the passed in {@code headers} and {@code authorizationContext}.
     * May be overwritten in subclasses which add additional expressions to resolve.
     *
     * @param headers the headers to use for resolving via headers placeholder expressions.
     * @param authorizationContext the authorization context to resolve via request placeholder expressions.
     * @return the created ExpressionResolver.
     */
    protected ExpressionResolver createExpressionResolver(final Map<String, String> headers,
            @Nullable final AuthorizationContext authorizationContext) {
        return PlaceholderFactory.newExpressionResolver(
                PlaceholderFactory.newPlaceholderResolver(TIME_PLACEHOLDER, new Object()),
                PlaceholderFactory.newPlaceholderResolver(HEADERS_PLACEHOLDER, headers),
                PlaceholderFactory.newPlaceholderResolver(REQUEST_PLACEHOLDER, authorizationContext)
        );
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

    private static Map<String, String> resolveCommandHeaders(final ExpressionResolver resolver,
            final Map<String, String> unresolvedHeaders) {
        final Map<String, String> resolvedHeaders = new LinkedHashMap<>();
        unresolvedHeaders.forEach((key, value) ->
                resolver.resolve(value).findFirst().ifPresent(resolvedHeaderValue ->
                        resolvedHeaders.put(key, resolvedHeaderValue)
                )
        );
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
        if (isErrorByTopicPath(adaptable)) {
            final var adaptablePayload = adaptable.getPayload();
            adaptablePayload.getValue()
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .ifPresentOrElse(jsonObject -> {
                        final var globalErrorRegistry = GlobalErrorRegistry.getInstance();
                        throw globalErrorRegistry.parse(jsonObject, adaptable.getDittoHeaders());
                    }, () -> LOGGER.withCorrelationId(adaptable.getDittoHeaders())
                            .warn("Unexpected error adaptable. Expected value of type JsonObject in payload but got " +
                                    "<{}>.", adaptablePayload));
        }
        return Collections.emptyList();
    }

    private static boolean isErrorByTopicPath(final Adaptable adaptable) {
        final var topicPath = adaptable.getTopicPath();
        return topicPath.isCriterion(TopicPath.Criterion.ERRORS);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", thingTemplate=" + thingTemplate +
                ", commandHeaders=" + commandHeaders +
                ", allowPolicyLockout=" + allowPolicyLockout +
                "]";
    }
}
