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

package org.eclipse.ditto.things.service.persistence.actors;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.cacheloaders.AskWithRetry;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.enforcement.config.EnforcementConfig;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Functionality used in {@link ThingSupervisorActor} for retrieving an inline {@code Policy} together with a
 * {@link RetrieveThing} command.
 */
final class SupervisorInlinePolicyEnrichment {

    private static final Duration DEFAULT_LOCAL_ASK_TIMEOUT = Duration.ofSeconds(5);

    private final ActorSystem actorSystem;
    private final ThreadSafeDittoLoggingAdapter log;
    private final ThingId thingId;
    private final ActorSelection thingPersistenceActor;
    private final ActorRef policiesShardRegion;
    private final EnforcementConfig enforcementConfig;

    SupervisorInlinePolicyEnrichment(final ActorSystem actorSystem,
            final ThreadSafeDittoLoggingAdapter log,
            final ThingId thingId,
            final ActorSelection thingPersistenceActor,
            final ActorRef policiesShardRegion,
            final EnforcementConfig enforcementConfig) {

        this.actorSystem = actorSystem;
        this.log = log;
        this.thingId = thingId;
        this.thingPersistenceActor = thingPersistenceActor;
        this.policiesShardRegion = policiesShardRegion;
        this.enforcementConfig = enforcementConfig;
    }

    /**
     * Check if inlined policy should be retrieved together with the thing.
     *
     * @param retrieveThing the RetrieveThing command.
     * @return whether it is necessary to retrieve the thing's policy.
     */
    static boolean shouldRetrievePolicyWithThing(final RetrieveThing retrieveThing) {

        return retrieveThing.getSelectedFields()
                .filter(selector -> selector.getPointers()
                        .stream()
                        .anyMatch(jsonPointer -> jsonPointer.getRoot()
                                .filter(jsonKey -> Policy.INLINED_FIELD_NAME.equals(jsonKey.toString()))
                                .isPresent()))
                .isPresent();
    }

    /**
     * Based on the need to retrieve a policy together with a thing determined by
     * {@link #shouldRetrievePolicyWithThing(RetrieveThing)} this method creates a {@code RetrieveThingResponse}
     * containing the inlined policy as {@code "_policy"} field in the Thing JSON.
     *
     * @param retrieveThing the RetrieveThing command which contains a field selector with {@code "_policy"} field
     * @param retrieveThingResponse the response from the thing persistence actor containing the Thing JSON to enrich
     * with the policy.
     * @return a Source of a single RetrieveThingResponse combining Thing JSON and inlined {@code "_policy"}
     */
    Source<RetrieveThingResponse, NotUsed> enrichPolicy(final RetrieveThing retrieveThing,
            final RetrieveThingResponse retrieveThingResponse) {

        return retrievePolicyIdViaSudoRetrieveThing()
                .map(SudoRetrieveThingResponse::getThing)
                .map(Thing::getPolicyId)
                .map(optionalPolicyId -> optionalPolicyId.orElseThrow(() -> {
                    log.withCorrelationId(retrieveThing)
                            .warning("Found thing without policy ID. This should never be possible. " +
                                    "This is most likely a bug and should be fixed.");
                    return ThingNotAccessibleException.newBuilder(thingId)
                            .dittoHeaders(retrieveThing.getDittoHeaders())
                            .build();
                }))
                .map(policyId -> {
                    final var dittoHeadersWithoutPreconditionHeaders = retrieveThing.getDittoHeaders()
                            .toBuilder()
                            .removePreconditionHeaders()
                            .build();
                    return RetrievePolicy.of(policyId, dittoHeadersWithoutPreconditionHeaders);
                })
                .map(this::retrieveInlinedPolicyForThing)
                .flatMapConcat(Source::completionStage)
                .map(policyResponse -> {
                    if (policyResponse.isPresent()) {
                        final JsonObject inlinedPolicy = policyResponse.get()
                                .getPolicy()
                                .toInlinedJson(retrieveThing.getImplementedSchemaVersion(),
                                        FieldType.notHidden());

                        final JsonObject thingWithInlinedPolicy = retrieveThingResponse.getEntity()
                                .asObject()
                                .toBuilder()
                                .setAll(inlinedPolicy)
                                .build();
                        return retrieveThingResponse.setEntity(thingWithInlinedPolicy);
                    } else {
                        return retrieveThingResponse;
                    }
                });
    }

    private Source<SudoRetrieveThingResponse, NotUsed> retrievePolicyIdViaSudoRetrieveThing() {

        final CompletionStage<Object> askForThing =
                Patterns.ask(thingPersistenceActor, SudoRetrieveThing.of(thingId,
                                JsonFieldSelector.newInstance("policyId"),
                                DittoHeaders.newBuilder()
                                        .correlationId("retrievePolicyIdViaSudoRetrieveThing-" + UUID.randomUUID())
                                        .build()
                        ), DEFAULT_LOCAL_ASK_TIMEOUT
                );
        return Source.completionStage(askForThing)
                .map(response -> {
                    if (response instanceof DittoRuntimeException dre) {
                        throw dre;
                    }
                    return response;
                })
                .divertTo(Sink.foreach(unexpectedResponseType ->
                                log.warning("Unexpected response type. Expected <{}>, but got <{}>.",
                                        SudoRetrieveThingResponse.class, unexpectedResponseType.getClass())),
                        response -> !(response instanceof SudoRetrieveThingResponse))
                .map(SudoRetrieveThingResponse.class::cast);
    }



    /**
     * Retrieve inlined policy after retrieving a thing. Do not report errors.
     *
     * @param retrievePolicy the command to retrieve the thing's policy.
     * @return future response from policies-shard-region.
     */
    private CompletionStage<Optional<RetrievePolicyResponse>> retrieveInlinedPolicyForThing(
            final RetrievePolicy retrievePolicy) {

        return AskWithRetry.askWithRetry(policiesShardRegion, retrievePolicy,
                enforcementConfig.getAskWithRetryConfig(),
                actorSystem,
                response -> {
                    if (response instanceof RetrievePolicyResponse retrievePolicyResponse) {
                        return Optional.of(retrievePolicyResponse);
                    } else {
                        log.withCorrelationId(getCorrelationIdOrNull(response, retrievePolicy))
                                .info("No authorized response when retrieving inlined policy <{}> for thing <{}>: {}",
                                        retrievePolicy.getEntityId(), thingId, response);
                        return Optional.<RetrievePolicyResponse>empty();
                    }
                }
        ).exceptionally(error -> {
            log.withCorrelationId(getCorrelationIdOrNull(error, retrievePolicy))
                    .error(error, "Retrieving inlined policy after RetrieveThing");
            return Optional.empty();
        });
    }

    @Nullable
    private static CharSequence getCorrelationIdOrNull(final Object signal, final WithDittoHeaders fallBackSignal) {

        final WithDittoHeaders withDittoHeaders;
        if (isWithDittoHeaders(signal)) {
            withDittoHeaders = (WithDittoHeaders) signal;
        } else {
            withDittoHeaders = fallBackSignal;
        }
        final var dittoHeaders = withDittoHeaders.getDittoHeaders();
        return dittoHeaders.getCorrelationId().orElse(null);
    }

    private static boolean isWithDittoHeaders(final Object o) {
        return o instanceof WithDittoHeaders;
    }
}
