/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.cacheloaders;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyRevision;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.services.utils.cache.EntityId;
import org.eclipse.ditto.services.utils.cache.entry.Entry;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;

/**
 * Loads a policy-enforcer by asking the policies shard-region-proxy.
 */
@Immutable
public final class PolicyEnforcerCacheLoader implements AsyncCacheLoader<EntityId, Entry<Enforcer>> {

    private final ActorAskCacheLoader<Enforcer, Command> delegate;

    /**
     * Constructor.
     *
     * @param askTimeout the ask-timeout for communicating with the shard-region-proxy.
     * @param policiesShardRegionProxy the shard-region-proxy.
     * @param predicate the test to execute before loading a cache entry, or null.
     */
    public PolicyEnforcerCacheLoader(final Duration askTimeout, final ActorRef policiesShardRegionProxy,
            @Nullable final Function<EntityId, CompletionStage<Boolean>> predicate) {
        requireNonNull(askTimeout);
        requireNonNull(policiesShardRegionProxy);

        final Function<String, Command> commandCreator = PolicyCommandFactory::sudoRetrievePolicy;
        final Function<Object, Entry<Enforcer>> responseTransformer =
                PolicyEnforcerCacheLoader::handleSudoRetrievePolicyResponse;

        delegate = ActorAskCacheLoader.forShard(askTimeout, PolicyCommand.RESOURCE_TYPE, policiesShardRegionProxy,
                commandCreator, responseTransformer).withPredicate(predicate);
    }

    @Override
    public CompletableFuture<Entry<Enforcer>> asyncLoad(final EntityId key, final Executor executor) {
        return delegate.asyncLoad(key, executor);
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

    private static Supplier<RuntimeException> badPolicyResponse(final String message) {
        return () -> new IllegalStateException("Bad SudoRetrievePolicyResponse: " + message);
    }

}
