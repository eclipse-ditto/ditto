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

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingRevision;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.services.utils.cache.EntityId;
import org.eclipse.ditto.services.utils.cache.entry.Entry;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;

/**
 * Loads entity ID relation for authorization of a Thing by asking the things-shard-region proxy.
 */
@Immutable
public final class ThingEnforcementIdCacheLoader implements AsyncCacheLoader<EntityId, Entry<EntityId>> {

    private final ActorAskCacheLoader<EntityId, Command> delegate;

    /**
     * Constructor.
     *
     * @param askTimeout the ask-timeout for communicating with the shard-region-proxy.
     * @param shardRegionProxy the shard-region-proxy.
     */
    public ThingEnforcementIdCacheLoader(final Duration askTimeout, final ActorRef shardRegionProxy) {
        final Function<String, Command> commandCreator = ThingCommandFactory::sudoRetrieveThing;
        final Function<Object, Entry<EntityId>> responseTransformer =
                ThingEnforcementIdCacheLoader::handleSudoRetrieveThingResponse;

        this.delegate =
                ActorAskCacheLoader.forShard(askTimeout, ThingCommand.RESOURCE_TYPE, shardRegionProxy, commandCreator,
                        responseTransformer);
    }

    @Override
    public CompletableFuture<Entry<EntityId>> asyncLoad(final EntityId key, final Executor executor) {
        return delegate.asyncLoad(key, executor);
    }

    private static Entry<EntityId> handleSudoRetrieveThingResponse(final Object response) {
        if (response instanceof SudoRetrieveThingResponse) {
            final SudoRetrieveThingResponse sudoRetrieveThingResponse = (SudoRetrieveThingResponse) response;
            final Thing thing = sudoRetrieveThingResponse.getThing();
            final String thingId = thing.getId().orElseThrow(badThingResponse("no ThingId"));
            final long revision = thing.getRevision().map(ThingRevision::toLong)
                    .orElseThrow(badThingResponse("no revision"));
            final Optional<AccessControlList> accessControlListOptional = thing.getAccessControlList();
            if (accessControlListOptional.isPresent()) {
                final EntityId resourceKey = EntityId.of(ThingCommand.RESOURCE_TYPE, thingId);
                return Entry.of(revision, resourceKey);
            } else {
                final String policyId = thing.getPolicyId().orElseThrow(badThingResponse("no PolicyId or ACL"));
                final EntityId resourceKey = EntityId.of(PolicyCommand.RESOURCE_TYPE, policyId);
                return Entry.of(revision, resourceKey);
            }
        } else if (response instanceof ThingNotAccessibleException) {
            return Entry.nonexistent();
        } else {
            throw new IllegalStateException("expect SudoRetrieveThingResponse, got: " + response);
        }
    }

    private static Supplier<RuntimeException> badThingResponse(final String message) {
        return () -> new IllegalStateException("Bad SudoRetrieveThingResponse: " + message);
    }

}
