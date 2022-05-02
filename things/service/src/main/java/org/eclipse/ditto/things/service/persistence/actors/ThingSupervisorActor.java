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
package org.eclipse.ditto.things.service.persistence.actors;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.service.actors.ShutdownBehaviour;
import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.internal.utils.cacheloaders.AskWithRetry;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceSupervisor;
import org.eclipse.ditto.internal.utils.pubsub.DistributedAcks;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsub.LiveSignalPub;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.enforcement.PreEnforcer;
import org.eclipse.ditto.policies.enforcement.config.DefaultEnforcementConfig;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingUnavailableException;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.service.common.config.DittoThingsConfig;
import org.eclipse.ditto.things.service.enforcement.ThingCommandEnforcement;

import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;

/**
 * Supervisor for {@link ThingPersistenceActor} which means it will create, start and watch it as child actor.
 * <p>
 * If the child terminates, it will wait for the calculated exponential back-off time and restart it afterwards.
 * Between the termination of the child and the restart, this actor answers to all requests with a
 * {@link ThingUnavailableException} as fail fast strategy.
 * </p>
 */
public final class ThingSupervisorActor extends AbstractPersistenceSupervisor<ThingId> {

    private final ActorRef pubSubMediator;
    private final ActorRef policiesShardRegion;
    private final DistributedPub<ThingEvent<?>> distributedPub;
    private final ThingPersistenceActorPropsFactory thingPersistenceActorPropsFactory;
    private final PreEnforcer preEnforcer;
    private final DefaultEnforcementConfig enforcementConfig;

    @SuppressWarnings("unused")
    private ThingSupervisorActor(final ActorRef pubSubMediator,
            final ActorRef policiesShardRegion,
            final DistributedPub<ThingEvent<?>> distributedPub,
            final ThingPersistenceActorPropsFactory thingPersistenceActorPropsFactory,
            final PreEnforcer preEnforcer) {

        this.pubSubMediator = pubSubMediator;
        this.policiesShardRegion = policiesShardRegion;
        this.distributedPub = distributedPub;
        this.thingPersistenceActorPropsFactory = thingPersistenceActorPropsFactory;
        this.preEnforcer = preEnforcer;
        enforcementConfig = DefaultEnforcementConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );
    }

    /**
     * Props for creating a {@code ThingSupervisorActor}.
     * <p>
     * Exceptions in the child are handled with a supervision strategy that stops the child
     * for {@link ActorKilledException}'s and escalates all others.
     * </p>
     *
     * @param pubSubMediator
     * @param policiesShardRegion TODO TJ doc
     * @param distributedPub distributed-pub access for publishing thing events.
     * @param propsFactory factory for creating Props to be used for creating
     * @param preEnforcer TODO TJ doc
     * @return the {@link Props} to create this actor.
     */
    public static Props props(final ActorRef pubSubMediator,
            final ActorRef policiesShardRegion,
            final DistributedPub<ThingEvent<?>> distributedPub,
            final ThingPersistenceActorPropsFactory propsFactory,
            final PreEnforcer preEnforcer) {

        return Props.create(ThingSupervisorActor.class, pubSubMediator, policiesShardRegion, distributedPub,
                propsFactory, preEnforcer);
    }

    @Override
    protected CompletionStage<Object> modifyPersistenceActorCommandResponse(final Command<?> enforcedCommand,
            final Object persistenceCommandResponse) {

        if (enforcedCommand instanceof RetrieveThing retrieveThing &&
                shouldRetrievePolicyWithThing(retrieveThing) &&
                persistenceCommandResponse instanceof RetrieveThingResponse retrieveThingResponse) {

            assert entityId != null;
            return Patterns.ask(persistenceActorChild, SudoRetrieveThing.of(entityId,
                            JsonFieldSelector.newInstance("policyId"),
                            DittoHeaders.newBuilder()
                                    .correlationId("sudoRetrieveThingFromThingSupervisorActor-" + UUID.randomUUID())
                                    .build()
                    ), DEFAULT_LOCAL_ASK_TIMEOUT
            ).thenApply(response ->
                    ThingEnforcerActor.extractPolicyIdFromSudoRetrieveThingResponse(response).orElse(null)
            ).thenCompose(policyId -> {
                final var dittoHeadersWithoutPreconditionHeaders = retrieveThing.getDittoHeaders()
                        .toBuilder()
                        .removePreconditionHeaders()
                        .build();
                final var retrievePolicy = RetrievePolicy.of(policyId, dittoHeadersWithoutPreconditionHeaders);

                return retrieveInlinedPolicyForThing(retrieveThing, retrievePolicy)
                        .thenApply(policyResponse -> {
                            if (policyResponse.isPresent()) {
                                final JsonObject inlinedPolicy = policyResponse.get()
                                        .getPolicy()
                                        .toInlinedJson(retrieveThing.getImplementedSchemaVersion(), FieldType.notHidden());

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
            });
        } else {
            return CompletableFuture.completedFuture(persistenceCommandResponse);
        }
    }

    /**
     * Check if inlined policy should be retrieved together with the thing.
     *
     * @param retrieveThing the RetrieveThing command.
     * @return whether it is necessary to retrieve the thing's policy.
     */
    private static boolean shouldRetrievePolicyWithThing(final RetrieveThing retrieveThing) {
        return retrieveThing.getSelectedFields()
                .filter(selector -> selector.getPointers()
                        .stream()
                        .anyMatch(jsonPointer -> jsonPointer.getRoot()
                                .filter(jsonKey -> Policy.INLINED_FIELD_NAME.equals(jsonKey.toString()))
                                .isPresent()))
                .isPresent();
    }

    /**
     * Retrieve inlined policy after retrieving a thing. Do not report errors.
     *
     * @param retrieveThing the original command.
     * @param retrievePolicy the command to retrieve the thing's policy.
     * @return future response from policies-shard-region.
     */
    private CompletionStage<Optional<RetrievePolicyResponse>> retrieveInlinedPolicyForThing(
            final RetrieveThing retrieveThing, final RetrievePolicy retrievePolicy) {

        return preEnforcer.apply(retrievePolicy)
                .thenCompose(msg -> AskWithRetry.askWithRetry(policiesShardRegion, msg,
                        enforcementConfig.getAskWithRetryConfig(),
                        getContext().getSystem().getScheduler(), getContext().getSystem().getDispatcher(), // TODO TJ check if own executor should be used
                        response -> {
                            if (response instanceof RetrievePolicyResponse retrievePolicyResponse) {
                                return Optional.of(retrievePolicyResponse);
                            } else {
                                log.withCorrelationId(getCorrelationIdOrNull(response, retrieveThing))
                                        .info("No authorized response when retrieving inlined policy <{}> for thing <{}>: {}",
                                                retrievePolicy.getEntityId(), retrieveThing.getEntityId(), response);
                                return Optional.<RetrievePolicyResponse>empty();
                            }
                        }
                ).exceptionally(error -> {
                    log.withCorrelationId(getCorrelationIdOrNull(error, retrieveThing))
                            .error("Retrieving inlined policy after RetrieveThing", error);
                    return Optional.empty();
                }));
    }

    @Override
    protected ThingId getEntityId() throws Exception {
        return ThingId.of(URLDecoder.decode(getSelf().path().name(), StandardCharsets.UTF_8.name()));
    }

    @Override
    protected Props getPersistenceActorProps(final ThingId entityId) {
        return thingPersistenceActorPropsFactory.props(entityId, distributedPub);
    }

    @Override
    protected Props getPersistenceEnforcerProps(final ThingId entityId) {
        final ActorContext actorContext = getContext();
        final ActorSystem actorSystem = actorContext.getSystem();
        final DistributedAcks distributedAcks = DistributedAcks.lookup(actorSystem);
        final LiveSignalPub liveSignalPub = LiveSignalPub.of(actorContext, distributedAcks);

        // TODO TJ acks should be received by the sender - which is not available here - the supervisor must handle it somehow?!
        final ActorRef ackReceiverActor = actorContext.getSelf();

        final ThingCommandEnforcement thingCommandEnforcement = new ThingCommandEnforcement(
                actorSystem,
                ackReceiverActor,
                policiesShardRegion,
                creationRestrictionEnforcer,
                enforcementConfig,
                preEnforcer,
                liveSignalPub
        );
        return ThingEnforcerActor.props(entityId, thingCommandEnforcement, pubSubMediator);
    }

    @Override
    protected ShutdownBehaviour getShutdownBehaviour(final ThingId entityId) {
        return ShutdownBehaviour.fromId(entityId, pubSubMediator, getSelf());
    }

    @Override
    protected DittoRuntimeExceptionBuilder<?> getUnavailableExceptionBuilder(@Nullable final ThingId entityId) {
        return ThingUnavailableException.newBuilder(
                Objects.requireNonNullElseGet(entityId, () -> ThingId.of("UNKNOWN:ID")));
    }

    @Override
    protected ExponentialBackOffConfig getExponentialBackOffConfig() {
        return DittoThingsConfig.of(DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config()))
                .getThingConfig()
                .getSupervisorConfig()
                .getExponentialBackOffConfig();
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
