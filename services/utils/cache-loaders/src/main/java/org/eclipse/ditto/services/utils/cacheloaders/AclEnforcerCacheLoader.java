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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.enforcers.AclEnforcer;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingRevision;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.services.utils.cache.CacheLookupContext;
import org.eclipse.ditto.services.utils.cache.EntityIdWithResourceType;
import org.eclipse.ditto.services.utils.cache.entry.Entry;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;

/**
 * Loads an acl-enforcer by asking the things shard-region-proxy.
 */
@Immutable
public final class AclEnforcerCacheLoader
        implements AsyncCacheLoader<EntityIdWithResourceType, Entry<Enforcer>> {

    private final ActorAskCacheLoader<Enforcer, Command> delegate;

    /**
     * Constructor.
     *
     * @param askTimeout the ask-timeout for communicating with the shard-region-proxy.
     * @param thingsShardRegionProxy the shard-region-proxy.
     */
    public AclEnforcerCacheLoader(final Duration askTimeout, final ActorRef thingsShardRegionProxy) {
        requireNonNull(askTimeout);
        requireNonNull(thingsShardRegionProxy);

        final BiFunction<EntityId, CacheLookupContext, Command> commandCreator = ThingCommandFactory::sudoRetrieveThing;
        final BiFunction<Object, CacheLookupContext, Entry<Enforcer>> responseTransformer =
                AclEnforcerCacheLoader::handleSudoRetrieveThingResponse;

        this.delegate = ActorAskCacheLoader.forShard(askTimeout, ThingCommand.RESOURCE_TYPE, thingsShardRegionProxy,
                commandCreator, responseTransformer);
    }

    @Override
    public CompletableFuture<Entry<Enforcer>> asyncLoad(final EntityIdWithResourceType key,
            final Executor executor) {
        return delegate.asyncLoad(key, executor);
    }

    @Nullable
    private static Entry<Enforcer> handleSudoRetrieveThingResponse(final Object response,
            @Nullable final CacheLookupContext cacheLookupContext) {
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

    private static Supplier<RuntimeException> badThingResponse(final String message) {
        return () -> new IllegalStateException("Bad SudoRetrieveThingResponse: " + message);
    }

}
