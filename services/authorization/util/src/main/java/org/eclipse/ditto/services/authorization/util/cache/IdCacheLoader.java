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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingRevision;
import org.eclipse.ditto.services.authorization.util.cache.entry.Entry;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;

import akka.actor.ActorRef;

/**
 * Loads entity ID relation for authorization by asking entity shard regions.
 */
@Immutable
@AllParametersAndReturnValuesAreNonnullByDefault
public class IdCacheLoader extends AbstractAskCacheLoader<ResourceKey> {

    private final Duration askTimeout;
    private final Map<String, ActorRef> entityRegionMap;
    private final Map<String, Function<String, Object>> commandMap;
    private final Map<String, Function<Object, Entry<ResourceKey>>> transformerMap;
    private final AuthorizationCache authorizationCache;

    IdCacheLoader(final Duration askTimeout, final Map<String, ActorRef> entityRegionMap,
            final AuthorizationCache authorizationCache) {
        this.authorizationCache = authorizationCache;

        this.askTimeout = askTimeout;
        this.entityRegionMap = entityRegionMap;
        this.commandMap = Collections.unmodifiableMap(buildCommandMap());
        this.transformerMap = Collections.unmodifiableMap(buildTransformerMap());
    }

    @Override
    protected Duration getAskTimeout() {
        return askTimeout;
    }

    @Override
    protected ActorRef getEntityRegion(final String resourceType) {
        return checkNotNull(entityRegionMap.get(resourceType), resourceType);
    }

    @Override
    protected Object getCommand(final String resourceType, final String id) {
        return checkNotNull(commandMap.get(resourceType), resourceType).apply(id);
    }

    @Override
    protected Entry<ResourceKey> transformResponse(final String resourceType, final Object response) {
        return checkNotNull(transformerMap.get(resourceType), resourceType).apply(response);
    }

    /**
     * Map resource type to the command used to retrieve the ID of the authorization data for the entity.
     * Subclasses may override this method to handle additional resource types a la "cake pattern".
     *
     * @return A mutable map from resource types to authorization retrieval commands.
     */
    protected HashMap<String, Function<String, Object>> buildCommandMap() {
        final HashMap<String, Function<String, Object>> hashMap = new HashMap<>();
        hashMap.put(ThingCommand.RESOURCE_TYPE, IdCacheLoader::sudoRetrieveThing);
        return hashMap;
    }

    /**
     * Map resource type to the transformation applied to responses. Subclasses may override this method to handle
     * additional resource types a la "cake pattern".
     *
     * @return A mutable map containing response transformations.
     */
    protected HashMap<String, Function<Object, Entry<ResourceKey>>> buildTransformerMap() {
        final HashMap<String, Function<Object, Entry<ResourceKey>>> hashMap = new HashMap<>();
        hashMap.put(ThingCommand.RESOURCE_TYPE, this::handleSudoRetrieveThingResponse);
        return hashMap;
    }

    private static SudoRetrieveThing sudoRetrieveThing(final String thingId) {
        final JsonFieldSelector jsonFieldSelector = JsonFieldSelector.newInstance(
                Thing.JsonFields.ID.getPointer(),
                Thing.JsonFields.REVISION.getPointer(),
                Thing.JsonFields.ACL.getPointer(),
                Thing.JsonFields.POLICY_ID.getPointer());
        return SudoRetrieveThing.withOriginalSchemaVersion(thingId, jsonFieldSelector, DittoHeaders.empty());
    }

    private Entry<ResourceKey> handleSudoRetrieveThingResponse(final Object response) {
        if (response instanceof SudoRetrieveThingResponse) {
            final SudoRetrieveThingResponse sudoRetrieveThingResponse = (SudoRetrieveThingResponse) response;
            final Thing thing = sudoRetrieveThingResponse.getThing();
            final String thingId = thing.getId().orElseThrow(badResponse("no ThingId"));
            final long revision = thing.getRevision().map(ThingRevision::toLong)
                    .orElseThrow(badResponse("no revision"));
            if (thing.getAccessControlList().isPresent()) {
                final ResourceKey resourceKey = ResourceKey.newInstance(ThingCommand.RESOURCE_TYPE, thingId);
                final AccessControlList acl = thing.getAccessControlList().get();
                authorizationCache.updateAcl(resourceKey, revision, acl);
                return Entry.of(revision, resourceKey);
            } else {
                final String policyId = thing.getPolicyId().orElseThrow(badResponse("no PolicyId or ACL"));
                final ResourceKey resourceKey = ResourceKey.newInstance(PolicyCommand.RESOURCE_TYPE, policyId);
                return Entry.of(revision, resourceKey);
            }
        } else if (response instanceof ThingNotAccessibleException) {
            return Entry.nonexistent();
        } else {
            throw new IllegalStateException("expect SudoRetrieveThingResponse, got: " + response);
        }
    }

    private static Supplier<RuntimeException> badResponse(final String message) {
        return () -> new IllegalStateException("Bad SudoRetrieveThingResponse: " + message);
    }
}
