/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatcher;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.mapping.MappingConfig;
import org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders;
import org.eclipse.ditto.edge.service.placeholders.RequestPlaceholder;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.HeadersPlaceholder;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableAdaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * This mapper creates a {@link MergeThing} command when a {@link ThingQueryCommandResponse} was received via the
 * {@link TopicPath.Channel#LIVE live channel} patching exactly the retrieved "live" data into the twin.
 * It might be configured with a {@code dittoHeadersForMerge} JsonObject containing {@link DittoHeaders} to apply for
 * the created {@code MergeThing} command, e.g. adding a condition for the merge update.
 */
public final class UpdateTwinWithLiveResponseMessageMapper extends AbstractMessageMapper {

    private static final String PAYLOAD_MAPPER_ALIAS = "UpdateTwinWithLiveResponse";

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(
            UpdateTwinWithLiveResponseMessageMapper.class);

    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER =
            DittoProtocolAdapter.of(HeaderTranslator.empty());

    private static final TimePlaceholder TIME_PLACEHOLDER = TimePlaceholder.getInstance();
    private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();
    private static final RequestPlaceholder REQUEST_PLACEHOLDER = ConnectivityPlaceholders.newRequestPlaceholder();

    static final String DITTO_HEADERS_FOR_MERGE = "dittoHeadersForMerge";

    private static final DittoHeaders DEFAULT_DITTO_HEADERS_FOR_MERGE = DittoHeaders.newBuilder()
            .responseRequired(false)
            .ifMatch(EntityTagMatchers.fromList(List.of(EntityTagMatcher.asterisk())))
            .build();

    private static final JsonObject DEFAULT_CONFIG = DittoMessageMapper.DEFAULT_OPTIONS.toBuilder()
            .set(DITTO_HEADERS_FOR_MERGE, DEFAULT_DITTO_HEADERS_FOR_MERGE.toJson())
            .build();

    static final String CORRELATION_ID_SUFFIX = "-merge-into-twin";

    private DittoHeaders dittoHeadersForMerge = DEFAULT_DITTO_HEADERS_FOR_MERGE;

    /**
     * Constructs a new instance of UpdateTwinWithLiveResponseMessageMapper extension.
     *
     * @param actorSystem the actor system in which to load the extension.
     * @param config the configuration for this extension.
     */
    UpdateTwinWithLiveResponseMessageMapper(final ActorSystem actorSystem, final Config config) {
        super(actorSystem, config);
    }

    private UpdateTwinWithLiveResponseMessageMapper(final UpdateTwinWithLiveResponseMessageMapper copyFromMapper) {
        super(copyFromMapper);
        this.dittoHeadersForMerge = copyFromMapper.dittoHeadersForMerge;
    }

    @Override
    public String getAlias() {
        return PAYLOAD_MAPPER_ALIAS;
    }

    @Override
    public boolean isConfigurationMandatory() {
        return false;
    }

    @Override
    public MessageMapper createNewMapperInstance() {
        return new UpdateTwinWithLiveResponseMessageMapper(this);
    }

    @Override
    public void doConfigure(final Connection connection, final MappingConfig mappingConfig, final MessageMapperConfiguration configuration) {
        configuration.findProperty(DITTO_HEADERS_FOR_MERGE, JsonValue::isObject, JsonValue::asObject)
                .ifPresent(
                        configuredHeaders -> dittoHeadersForMerge = DittoHeaders.newBuilder(configuredHeaders).build());
    }

    @Override
    public JsonObject getDefaultOptions() {
        return DEFAULT_CONFIG;
    }

    @Override
    public List<Adaptable> map(final ExternalMessage message) {

        final JsonifiableAdaptable adaptable;
        try {
            final String payload = extractPayloadAsString(message);
            adaptable = DittoJsonException.wrapJsonRuntimeException(() ->
                    ProtocolFactory.jsonifiableAdaptableFromJson(JsonFactory.newObject(payload))
            );
        } catch (final DittoRuntimeException e) {
            LOGGER.withCorrelationId(message.getInternalHeaders())
                    .debug("Got DittoRuntimeException while trying to created adaptable from ExternalMessage: {}",
                            e.toString());
            return Collections.emptyList();
        }

        if (shouldUpdateTwinWithLiveThingQueryCommandResponse(adaptable)) {
            LOGGER.withCorrelationId(message.getInternalHeaders())
                    .debug("Received ExternalMessage containing a live ThingQueryCommandResponse adaptable: {}",
                            adaptable);
            final Signal<?> liveResponseSignal = DITTO_PROTOCOL_ADAPTER.fromAdaptable(adaptable);
            return extractMergeThingSignal(liveResponseSignal, message.getHeaders(),
                    message.getAuthorizationContext().orElse(null))
                    .map(mergeThing -> {
                        LOGGER.withCorrelationId(mergeThing)
                                .info("Mapped MergeThing from received live <{}>", liveResponseSignal.getType());
                        LOGGER.withCorrelationId(mergeThing)
                                .debug("Mapped MergeThing from received live <{}>: <{}>", liveResponseSignal.getType(),
                                        mergeThing);
                        return DITTO_PROTOCOL_ADAPTER.toAdaptable(mergeThing);
                    })
                    .map(List::of)
                    .orElseGet(Collections::emptyList);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public DittoHeaders getAdditionalInboundHeaders(final ExternalMessage message) {
        return DittoHeaders.empty();
    }

    private static boolean shouldUpdateTwinWithLiveThingQueryCommandResponse(final Adaptable adaptable) {
        return isResponse(adaptable) &&
                isLiveSignal(adaptable) &&
                isRetrieveAction(adaptable) &&
                isThingGroup(adaptable);
    }

    private static boolean isRetrieveAction(final Adaptable adaptable) {
        return adaptable.getTopicPath().getAction()
                .filter(action -> action.equals(TopicPath.Action.RETRIEVE))
                .isPresent();
    }

    private static boolean isThingGroup(final Adaptable adaptable) {
        return adaptable.getTopicPath().isGroup(TopicPath.Group.THINGS);
    }

    private Optional<Signal<MergeThing>> extractMergeThingSignal(final Signal<?> liveResponseSignal,
            final Map<String, String> externalMessageHeaders,
            @Nullable final AuthorizationContext authorizationContext) {

        final DittoHeaders dittoHeaders = liveResponseSignal.getDittoHeaders();
        final JsonPointer resourcePath = liveResponseSignal.getResourcePath();
        if (liveResponseSignal instanceof ThingQueryCommandResponse<?> queryCommandResponse) {
            final ThingId thingId = queryCommandResponse.getEntityId();
            final JsonValue entity = queryCommandResponse.getEntity();
            final String correlationId = dittoHeaders.getCorrelationId().orElseGet(() -> UUID.randomUUID().toString());
            final DittoHeadersBuilder<?, ?> headersBuilder =
                    DittoHeaders.newBuilder(resolvePlaceholders(externalMessageHeaders, authorizationContext));
            return Optional.of(MergeThing.of(thingId, resourcePath, entity, headersBuilder
                    .correlationId(correlationId + CORRELATION_ID_SUFFIX)
                    .build()));
        }
        return Optional.empty();
    }

    private Map<String, String> resolvePlaceholders(final Map<String, String> externalMessageHeaders,
            @Nullable final AuthorizationContext authorizationContext) {
        final ExpressionResolver expressionResolver = createExpressionResolver(externalMessageHeaders,
                authorizationContext);
        return dittoHeadersForMerge.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry ->
                        potentiallySubstitutePlaceholders(entry.getValue(), expressionResolver)));
    }

    private static ExpressionResolver createExpressionResolver(final Map<String, String> headers,
            @Nullable final AuthorizationContext authorizationContext) {
        return PlaceholderFactory.newExpressionResolver(List.of(
                PlaceholderFactory.newPlaceholderResolver(TIME_PLACEHOLDER, new Object()),
                PlaceholderFactory.newPlaceholderResolver(HEADERS_PLACEHOLDER, headers),
                PlaceholderFactory.newPlaceholderResolver(REQUEST_PLACEHOLDER, authorizationContext)
        ));
    }

    private static String potentiallySubstitutePlaceholders(final String stringValue,
            final ExpressionResolver expressionResolver) {
        return expressionResolver.resolvePartiallyAsPipelineElement(stringValue, Set.of()).findFirst().orElse(stringValue);
    }

    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", dittoHeadersForMerge=" + dittoHeadersForMerge +
                "]";
    }
}
