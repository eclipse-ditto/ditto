/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.concierge.enforcement.placeholders.references;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.id.ThingId;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.akka.controlflow.AbstractGraphActor;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayPlaceholderReferenceNotSupportedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayPlaceholderReferenceUnknownFieldException;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.pattern.Patterns;

/**
 * Responsible for resolving a policy id of a referenced entity.
 */
@Immutable
public final class PolicyIdReferencePlaceholderResolver implements ReferencePlaceholderResolver<String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyIdReferencePlaceholderResolver.class);

    private final Duration retrieveEntityTimeoutDuration;
    private final ActorRef conciergeForwarderActor;
    private final Map<ReferencePlaceholder.ReferencedEntityType, ResolveEntityReferenceStrategy>
            supportedEntityTypesToActionMap = new EnumMap<>(ReferencePlaceholder.ReferencedEntityType.class);
    private final Set<CharSequence> supportedEntityTypeNames;

    private PolicyIdReferencePlaceholderResolver(final ActorRef conciergeForwarderActor,
            final Duration retrieveEntityTimeoutDuration) {
        this.conciergeForwarderActor = conciergeForwarderActor;
        this.retrieveEntityTimeoutDuration = retrieveEntityTimeoutDuration;
        initializeSupportedEntityTypeReferences();
        this.supportedEntityTypeNames =
                this.supportedEntityTypesToActionMap.keySet().stream().map(Enum::name).collect(Collectors.toSet());
    }

    private void initializeSupportedEntityTypeReferences() {
        this.supportedEntityTypesToActionMap.put(ReferencePlaceholder.ReferencedEntityType.THINGS,
                this::handleThingPolicyIdReference);
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

        final ResolveEntityReferenceStrategy resolveEntityReferenceStrategy =
                this.supportedEntityTypesToActionMap.get(referencePlaceholder.getReferencedEntityType());

        if (resolveEntityReferenceStrategy == null) {
            final String referencedEntityType = referencePlaceholder.getReferencedEntityType().name();
            LogUtil.logWithCorrelationId(LOGGER, dittoHeaders, log -> log.info(
                    "Could not find a placeholder replacement strategy for entity type <{}> in supported entity types: {}",
                    referencedEntityType, supportedEntityTypeNames));
            throw notSupportedException(referencedEntityType, dittoHeaders);
        }

        LogUtil.logWithCorrelationId(LOGGER, dittoHeaders,
                log -> log.debug("Will resolve entity reference for placeholder: <{}>", referencePlaceholder));
        return resolveEntityReferenceStrategy.handleEntityPolicyIdReference(referencePlaceholder, dittoHeaders);
    }

    private CompletionStage<String> handleThingPolicyIdReference(final ReferencePlaceholder referencePlaceholder,
            final DittoHeaders dittoHeaders) {

        final HashMap<String, String> enhancedMap = new HashMap<>(dittoHeaders);
        enhancedMap.put(AbstractGraphActor.DITTO_INTERNAL_SPECIAL_ENFORCEMENT_LANE, "true");
        final DittoHeaders adjustedHeaders = DittoHeaders.of(enhancedMap);
        final ThingId thingId = ThingId.of(referencePlaceholder.getReferencedEntityId());
        final RetrieveThing retrieveThingCommand = RetrieveThing.getBuilder(thingId, adjustedHeaders)
                .withSelectedFields(referencePlaceholder.getReferencedField().toFieldSelector())
                .build();

        return Patterns.ask(conciergeForwarderActor, retrieveThingCommand, retrieveEntityTimeoutDuration)
                .thenApply(response -> this.handleRetrieveThingResponse(response, referencePlaceholder, dittoHeaders));
    }

    private String handleRetrieveThingResponse(final Object response,
            final ReferencePlaceholder referencePlaceholder,
            final DittoHeaders dittoHeaders) {

        if (response instanceof RetrieveThingResponse) {

            final JsonValue entity = ((RetrieveThingResponse) response).getEntity();
            if (!entity.isObject()) {
                LogUtil.logWithCorrelationId(LOGGER, dittoHeaders, log ->
                        log.error("Expected RetrieveThingResponse to contain a JsonObject as Entity but was: {}",
                                entity));
                throw GatewayInternalErrorException.newBuilder().dittoHeaders(dittoHeaders).build();
            }
            return entity.asObject()
                    .getValue(JsonFieldDefinition.ofString(referencePlaceholder.getReferencedField()))
                    .orElseThrow(() -> unknownFieldException(referencePlaceholder, dittoHeaders));

        } else if (response instanceof ThingErrorResponse) {
            LogUtil.logWithCorrelationId(LOGGER, dittoHeaders, log -> log.info(
                    "Got ThingErrorResponse when waiting on RetrieveThingResponse when resolving policy id placeholder reference <{}>: {}",
                    referencePlaceholder,
                    response));
            throw ((ThingErrorResponse) response).getDittoRuntimeException();
        } else if (response instanceof DittoRuntimeException) {
            // ignore warning that second argument isn't used. Runtime exceptions will have their stacktrace printed
            // in the logs according to https://www.slf4j.org/faq.html#paramException
            LogUtil.logWithCorrelationId(LOGGER, dittoHeaders, log -> log.info(
                    "Got Exception when waiting on RetrieveThingResponse when resolving policy id placeholder reference <{}> - {}: {}",
                    referencePlaceholder,
                    response.getClass().getSimpleName(), ((DittoRuntimeException) response).getMessage()));
            throw (DittoRuntimeException) response;
        } else {
            LogUtil.logWithCorrelationId(LOGGER, dittoHeaders, log -> log.error(
                    "Did not retrieve expected RetrieveThingResponse when resolving policy id placeholder reference <{}>: {}",
                    referencePlaceholder, response));
            throw GatewayInternalErrorException.newBuilder().dittoHeaders(dittoHeaders).build();
        }
    }

    private GatewayPlaceholderReferenceUnknownFieldException unknownFieldException(
            final ReferencePlaceholder placeholder, final DittoHeaders headers) {
        return GatewayPlaceholderReferenceUnknownFieldException.fromUnknownFieldAndEntityId(
                placeholder.getReferencedField().toString(),
                placeholder.getReferencedEntityId())
                .dittoHeaders(headers)
                .build();
    }

    private GatewayPlaceholderReferenceNotSupportedException notSupportedException(final String referencedEntityType,
            final DittoHeaders headers) {
        return GatewayPlaceholderReferenceNotSupportedException.fromUnsupportedEntityType(
                referencedEntityType, supportedEntityTypeNames)
                .dittoHeaders(headers).build();
    }

    public static PolicyIdReferencePlaceholderResolver of(final ActorRef conciergeForwarderActor,
            final Duration retrieveEntityTimeoutDuration) {

        return new PolicyIdReferencePlaceholderResolver(conciergeForwarderActor, retrieveEntityTimeoutDuration);
    }

    interface ResolveEntityReferenceStrategy {

        CompletionStage<String> handleEntityPolicyIdReference(final ReferencePlaceholder referencePlaceholder,
                final DittoHeaders dittoHeaders);

    }

}
