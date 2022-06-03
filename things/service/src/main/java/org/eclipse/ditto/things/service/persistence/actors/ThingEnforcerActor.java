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

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.policies.enforcement.AbstractPolicyLoadingEnforcerActor;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;
import org.eclipse.ditto.things.service.enforcement.ThingEnforcement;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.pattern.Patterns;

/**
 * Enforcer responsible for enforcing {@link ThingCommand}s and filtering {@link ThingCommandResponse}s utilizing the
 * {@link ThingEnforcement}.
 */
public final class ThingEnforcerActor
        extends AbstractPolicyLoadingEnforcerActor<ThingId, Signal<?>, CommandResponse<?>, ThingEnforcement> {

    @Nullable private AsyncCacheLoader<EnforcementCacheKey, Entry<PolicyEnforcer>> policyEnforcerCacheLoader;

    @SuppressWarnings("unused")
    private ThingEnforcerActor(final ThingId thingId,
            final ThingEnforcement thingEnforcement,
            final ActorRef pubSubMediator,
            @Nullable final BlockedNamespaces blockedNamespaces,
            final AskWithRetryConfig askWithRetryConfig,
            final ActorRef policiesShardRegion) {

        super(thingId, thingEnforcement, pubSubMediator, blockedNamespaces, askWithRetryConfig, policiesShardRegion);
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param thingId the ThingId this enforcer actor is responsible for.
     * @param thingEnforcement the thing enforcement logic to apply in the enforcer.
     * @param pubSubMediator the ActorRef of the distributed pub-sub-mediator used to subscribe for policy updates in
     * order to perform invalidations.
     * @param blockedNamespaces the blocked namespaces functionality to retrieve/subscribe for blocked namespaces.
     * @param askWithRetryConfig used to configure retry mechanism policy loading.
     * @param policiesShardRegion used to load the policy.
     * @return the {@link Props} to create this actor.
     */
    public static Props props(final ThingId thingId,
            final ThingEnforcement thingEnforcement,
            final ActorRef pubSubMediator,
            @Nullable final BlockedNamespaces blockedNamespaces,
            final AskWithRetryConfig askWithRetryConfig,
            final ActorRef policiesShardRegion) {

        return Props.create(ThingEnforcerActor.class, thingId, thingEnforcement, pubSubMediator,
                blockedNamespaces, askWithRetryConfig, policiesShardRegion);
    }

    @Override
    protected CompletionStage<PolicyId> providePolicyIdForEnforcement() {
        return getPolicyIdOfCachedEnforcer()
                .map(CompletableFuture::completedStage)
                .orElseGet(this::loadPolicyIdFromPersistenceActor);
    }

    private CompletionStage<PolicyId> loadPolicyIdFromPersistenceActor() {
        return Patterns.ask(getContext().getParent(), SudoRetrieveThing.of(entityId,
                        JsonFieldSelector.newInstance("policyId"),
                        DittoHeaders.newBuilder()
                                .correlationId("sudoRetrieveThingFromThingEnforcerActor-" + UUID.randomUUID())
                                .build()
                ), DEFAULT_LOCAL_ASK_TIMEOUT
        ).thenApply(response -> extractPolicyIdFromSudoRetrieveThingResponse(response).orElse(null));
    }

    /**
     * Extracts a {@link PolicyId} from the passed {@code response} which is expected to be a
     * {@link SudoRetrieveThingResponse}. A {@code response} being a {@link ThingNotAccessibleException} leads to an
     * empty Optional.
     *
     * @param response the response to extract the PolicyId from.
     * @return the optional extracted PolicyId.
     */
    static Optional<PolicyId> extractPolicyIdFromSudoRetrieveThingResponse(final Object response) {
        if (response instanceof SudoRetrieveThingResponse sudoRetrieveThingResponse) {
            return sudoRetrieveThingResponse.getThing().getPolicyId();
        } else if (response instanceof ThingNotAccessibleException) {
            return Optional.empty();
        } else {
            throw new IllegalStateException("expected SudoRetrieveThingResponse, got: " + response);
        }
    }

    @Override
    protected boolean shouldInvalidatePolicyEnforcerAfterEnforcement(final Signal<?> signal) {
        return signal instanceof ThingModifyCommand<?> tmc && tmc.changesAuthorization();
    }

}
