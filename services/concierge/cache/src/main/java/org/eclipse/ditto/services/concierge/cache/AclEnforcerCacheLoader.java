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
package org.eclipse.ditto.services.concierge.cache;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.enforcers.AclEnforcer;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingRevision;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.models.concierge.cache.Entry;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;

/**
 * Loads an acl-enforcer by asking the things shard-region-proxy.
 */
@Immutable
public final class AclEnforcerCacheLoader implements AsyncCacheLoader<EntityId, Entry<Enforcer>> {

    private final ActorAskCacheLoader<Enforcer> delegate;

    /**
     * Constructor.
     *
     * @param askTimeout the ask-timeout for communicating with the shard-region-proxy.
     * @param thingsShardRegionProxy the shard-region-proxy.
     */
    public AclEnforcerCacheLoader(final Duration askTimeout, final ActorRef thingsShardRegionProxy) {
        requireNonNull(askTimeout);
        requireNonNull(thingsShardRegionProxy);

        final Function<String, Command> commandCreator = ThingCommandFactory::sudoRetrieveThing;
        final Function<Object, Entry<Enforcer>> responseTransformer =
                AclEnforcerCacheLoader::handleSudoRetrieveThingResponse;

        this.delegate = new ActorAskCacheLoader<>(askTimeout, ThingCommand.RESOURCE_TYPE, thingsShardRegionProxy,
                commandCreator, responseTransformer);
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

    private static Supplier<RuntimeException> badThingResponse(final String message) {
        return () -> new IllegalStateException("Bad SudoRetrieveThingResponse: " + message);
    }

}
