/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.authorization.util.cache;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.enforcers.AclEnforcer;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyRevision;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingRevision;
import org.eclipse.ditto.services.authorization.util.EntityRegionMap;
import org.eclipse.ditto.services.authorization.util.cache.entry.Entry;
import org.eclipse.ditto.services.models.authorization.EntityId;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;

/**
 * Loads an enforcer by asking entity shard regions.
 */
@Immutable
public final class EnforcerCacheLoader implements AsyncCacheLoader<EntityId, Entry<Enforcer>> {

    private final ActorAskCacheLoader<Enforcer> delegate;

    public EnforcerCacheLoader(final Duration askTimeout, final ActorRef thingsShardRegion,
            final ActorRef policiesShardRegion) {
        final EntityRegionMap entityRegionProvider =
                EntityRegionMap.newBuilder()
                        .put(ThingCommand.RESOURCE_TYPE, thingsShardRegion)
                        .put(PolicyCommand.RESOURCE_TYPE, policiesShardRegion)
                        .build();

        final Map<String, Function<String, Object>> commandMap = new HashMap<>();
        commandMap.put(ThingCommand.RESOURCE_TYPE, ThingCommandFactory::sudoRetrieveThing);
        commandMap.put(PolicyCommand.RESOURCE_TYPE, policyId -> SudoRetrievePolicy.of(policyId, DittoHeaders.empty()));

        final Map<String, Function<Object, Entry<Enforcer>>> transformerMap = new HashMap<>();
        transformerMap.put(ThingCommand.RESOURCE_TYPE, EnforcerCacheLoader::handleSudoRetrieveThingResponse);
        transformerMap.put(PolicyCommand.RESOURCE_TYPE, EnforcerCacheLoader::handleSudoRetrievePolicyResponse);

        this.delegate = new ActorAskCacheLoader<>(askTimeout, entityRegionProvider,
                commandMap, transformerMap);
    }

    @Override
    public CompletableFuture<Entry<Enforcer>> asyncLoad(final EntityId key, final Executor executor) {
        return delegate.asyncLoad(key, executor);
    }

    @Nullable
    private static Entry<Enforcer> handleSudoRetrieveThingResponse(final Object response) {
        if (response instanceof SudoRetrieveThingResponse) {
            final SudoRetrieveThingResponse sudoRetrieveThingResponse = (SudoRetrieveThingResponse) response;
            final Thing thing = sudoRetrieveThingResponse.getThing();
            final Optional<AccessControlList> accessControlListOptional = thing.getAccessControlList();
            if (accessControlListOptional.isPresent()) {
                final AccessControlList accessControlList = accessControlListOptional.get();

                final long revision = thing.getRevision().map(ThingRevision::toLong)
                        .orElseThrow(badThingResponse("no revision"));

                return Entry.of(revision, AclEnforcer.of(accessControlList));
            } else {
                // The thing exists, but it has a policy. Remove entry from cache.
                return null;
            }
        } else if (response instanceof ThingNotAccessibleException) {
            return Entry.nonexistent();
        } else {
            throw new IllegalStateException("expect SudoRetrieveThingResponse, got: " + response);
        }
    }

    private static Entry<Enforcer> handleSudoRetrievePolicyResponse(final Object response) {
        if (response instanceof SudoRetrievePolicyResponse) {
            final SudoRetrievePolicyResponse sudoRetrievePolicyResponse = (SudoRetrievePolicyResponse) response;
            final Policy policy = sudoRetrievePolicyResponse.getPolicy();
            final long revision = policy.getRevision().map(PolicyRevision::toLong)
                    .orElseThrow(badPolicyResponse("no revision"));
            return Entry.of(revision, PolicyEnforcers.defaultEvaluator(policy));
        } else if (response instanceof PolicyNotAccessibleException) {
            return Entry.nonexistent();
        } else {
            throw new IllegalStateException("expect SudoRetrievePolicyResponse, got: " + response);
        }
    }

    private static Supplier<RuntimeException> badThingResponse(final String message) {
        return () -> new IllegalStateException("Bad SudoRetrieveThingResponse: " + message);
    }

    private static Supplier<RuntimeException> badPolicyResponse(final String message) {
        return () -> new IllegalStateException("Bad SudoRetrievePolicyResponse: " + message);
    }

}
