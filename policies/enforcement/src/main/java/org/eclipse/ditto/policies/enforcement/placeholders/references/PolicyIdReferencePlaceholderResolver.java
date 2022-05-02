/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement.placeholders.references;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayPlaceholderReferenceNotSupportedException;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayPlaceholderReferenceUnknownFieldException;
import org.eclipse.ditto.internal.utils.akka.logging.AutoCloseableSlf4jLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cacheloaders.AskWithRetry;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;

import akka.actor.ActorRef;
import akka.actor.Scheduler;

/**
 * Responsible for resolving a policy id of a referenced entity.
 */
@Immutable
public final class PolicyIdReferencePlaceholderResolver implements ReferencePlaceholderResolver<String> {

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(PolicyIdReferencePlaceholderResolver.class);

    private final ActorRef conciergeForwarderActor;
    private final AskWithRetryConfig askWithRetryConfig;
    private final Scheduler scheduler;
    private final Executor executor;
    private final Map<ReferencePlaceholder.ReferencedEntityType, ResolveEntityReferenceStrategy>
            supportedEntityTypesToActionMap = new EnumMap<>(ReferencePlaceholder.ReferencedEntityType.class);
    private final Set<CharSequence> supportedEntityTypeNames;

    private PolicyIdReferencePlaceholderResolver(final ActorRef conciergeForwarderActor,
            final AskWithRetryConfig askWithRetryConfig, final Scheduler scheduler, final Executor executor) {
        this.conciergeForwarderActor = conciergeForwarderActor;
        this.askWithRetryConfig = askWithRetryConfig;
        this.scheduler = scheduler;
        this.executor = executor;
        initializeSupportedEntityTypeReferences();
        this.supportedEntityTypeNames =
                this.supportedEntityTypesToActionMap.keySet().stream().map(Enum::name).collect(Collectors.toSet());
    }

    private void initializeSupportedEntityTypeReferences() {
        this.supportedEntityTypesToActionMap.put(ReferencePlaceholder.ReferencedEntityType.THINGS,
                this::handlePolicyIdReference);
    }

    /**
     * Resolves the policy id of the entity of type {@link ReferencePlaceholder#getReferencedEntityType()} with id
     * {@link ReferencePlaceholder#getReferencedEntityId()}.
     *
     * @param referencePlaceholder The placeholder holding the information about the referenced entity id.
     * @param dittoHeaders The ditto headers.
     * @return A completion stage of String that should eventually hold the policy id.
     */
    @Override
    public CompletionStage<String> resolve(final ReferencePlaceholder referencePlaceholder,
            final DittoHeaders dittoHeaders) {

        final var resolveEntityReferenceStrategy =
                supportedEntityTypesToActionMap.get(referencePlaceholder.getReferencedEntityType());

        try (final AutoCloseableSlf4jLogger logger = LOGGER.setCorrelationId(dittoHeaders)) {
            if (null == resolveEntityReferenceStrategy) {
                final String referencedEntityType = referencePlaceholder.getReferencedEntityType().name();
                logger.info("Could not find a placeholder replacement strategy for entity type <{}> in supported" +
                        " entity types: {}", referencedEntityType, supportedEntityTypeNames);
                throw notSupportedException(referencedEntityType, dittoHeaders);
            }
            logger.debug("Will resolve entity reference for placeholder: <{}>", referencePlaceholder);
        }
        return resolveEntityReferenceStrategy.handleEntityPolicyIdReference(referencePlaceholder, dittoHeaders);
    }

    private CompletionStage<String> handlePolicyIdReference(final ReferencePlaceholder referencePlaceholder,
            final DittoHeaders dittoHeaders) {

        final var thingId = ThingId.of(referencePlaceholder.getReferencedEntityId());
        final var retrieveThingCommand = RetrieveThing.getBuilder(thingId, dittoHeaders)
                .withSelectedFields(referencePlaceholder.getReferencedField().toFieldSelector())
                .build();

        return AskWithRetry.askWithRetry(conciergeForwarderActor, retrieveThingCommand, askWithRetryConfig, scheduler,
                executor,
                response -> handleRetrieveThingResponse(response, referencePlaceholder, dittoHeaders)
        );
    }

    private static String handleRetrieveThingResponse(final Object response,
            final ReferencePlaceholder referencePlaceholder, final DittoHeaders dittoHeaders) {

        if (response instanceof RetrieveThingResponse) {
            final JsonValue entity = ((RetrieveThingResponse) response).getEntity();
            if (!entity.isObject()) {
                LOGGER.withCorrelationId(dittoHeaders)
                        .error("Expected RetrieveThingResponse to contain a JsonObject as Entity but was: {}", entity);
                throw DittoInternalErrorException.newBuilder().dittoHeaders(dittoHeaders).build();
            }
            return entity.asObject()
                    .getValue(JsonFieldDefinition.ofString(referencePlaceholder.getReferencedField()))
                    .orElseThrow(() -> unknownFieldException(referencePlaceholder, dittoHeaders));

        } else if (response instanceof ThingErrorResponse) {
            LOGGER.withCorrelationId(dittoHeaders)
                    .info("Got ThingErrorResponse when waiting on RetrieveThingResponse when resolving policy ID" +
                                    " placeholder reference <{}>: {}", referencePlaceholder, response);
            throw ((ThingErrorResponse) response).getDittoRuntimeException();
        } else if (response instanceof DittoRuntimeException) {
            // ignore warning that second argument isn't used. Runtime exceptions will have their stacktrace printed
            // in the logs according to https://www.slf4j.org/faq.html#paramException
            LOGGER.withCorrelationId(dittoHeaders)
                    .info("Got Exception when waiting on RetrieveThingResponse when resolving policy ID placeholder reference <{}> - {}: {}",
                            referencePlaceholder, response.getClass().getSimpleName(),
                            ((Throwable) response).getMessage());
            throw (DittoRuntimeException) response;
        } else {
            LOGGER.withCorrelationId(dittoHeaders)
                    .error("Did not retrieve expected RetrieveThingResponse when resolving policy ID placeholder reference <{}>: {}",
                            referencePlaceholder, response);
            throw DittoInternalErrorException.newBuilder().dittoHeaders(dittoHeaders).build();
        }
    }

    private static GatewayPlaceholderReferenceUnknownFieldException unknownFieldException(
            final ReferencePlaceholder placeholder, final DittoHeaders headers) {

        return GatewayPlaceholderReferenceUnknownFieldException.fromUnknownFieldAndEntityId(
                placeholder.getReferencedField().toString(),
                placeholder.getReferencedEntityId())
                .dittoHeaders(headers)
                .build();
    }

    private GatewayPlaceholderReferenceNotSupportedException notSupportedException(
            final CharSequence referencedEntityType, final DittoHeaders headers) {

        return GatewayPlaceholderReferenceNotSupportedException.fromUnsupportedEntityType(referencedEntityType,
                supportedEntityTypeNames)
                .dittoHeaders(headers)
                .build();
    }

    /**
     * Creates a new {@link PolicyIdReferencePlaceholderResolver} responsible for resolving a policy id of a referenced
     * entity.
     *
     * @param conciergeForwarderActor the ActorRef of the {@code ConciergeForwarderActor} which to ask for "retrieve"
     * commands.
     * @param askWithRetryConfig the configuration for the "ask with retry" pattern applied when asking for retrieves.
     * @param scheduler the scheduler to use for the "ask with retry" for retries.
     * @param executor the executor to use for the "ask with retry" for retries.
     * @return the created PolicyIdReferencePlaceholderResolver instance.
     */
    public static PolicyIdReferencePlaceholderResolver of(final ActorRef conciergeForwarderActor,
            final AskWithRetryConfig askWithRetryConfig, final Scheduler scheduler, final Executor executor) {

        return new PolicyIdReferencePlaceholderResolver(conciergeForwarderActor, askWithRetryConfig, scheduler,
                executor);
    }

    interface ResolveEntityReferenceStrategy {

        CompletionStage<String> handleEntityPolicyIdReference(ReferencePlaceholder referencePlaceholder,
                DittoHeaders dittoHeaders);

    }

}
