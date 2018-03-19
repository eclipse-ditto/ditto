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
import java.util.Optional;
import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingRevision;
import org.eclipse.ditto.services.authorization.util.EntityRegionMap;
import org.eclipse.ditto.services.authorization.util.cache.entry.Entry;
import org.eclipse.ditto.services.models.authorization.EntityId;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;

/**
 * Loads entity ID relation for authorization by asking entity shard regions.
 * <p>
 * TODO: make extensible.
 */
@Immutable
public final class IdCacheLoader extends AbstractAskCacheLoader<EntityId> {

    private final AuthorizationCaches authorizationCaches;

    protected IdCacheLoader(final Duration askTimeout, final EntityRegionMap entityRegionMap,
            final AuthorizationCaches authorizationCaches) {
        super(askTimeout, entityRegionMap);
        this.authorizationCaches = authorizationCaches;
    }

    @Override
    protected HashMap<String, Function<String, Object>> buildCommandMap() {
        final HashMap<String, Function<String, Object>> hashMap = super.buildCommandMap();
        hashMap.put(ThingCommand.RESOURCE_TYPE, AbstractAskCacheLoader::sudoRetrieveThing);
        return hashMap;
    }

    @Override
    protected HashMap<String, Function<Object, Entry<EntityId>>> buildTransformerMap() {
        final HashMap<String, Function<Object, Entry<EntityId>>> hashMap = super.buildTransformerMap();
        hashMap.put(ThingCommand.RESOURCE_TYPE, this::handleSudoRetrieveThingResponse);
        return hashMap;
    }

    private Entry<EntityId> handleSudoRetrieveThingResponse(final Object response) {
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
}
