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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.authorization.util.cache.entry.Entry;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
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

    IdCacheLoader(final Duration askTimeout, final Map<String, ActorRef> entityRegionMap) {
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

    // TODO document as extension point
    protected HashMap<String, Function<String, Object>> buildCommandMap() {
        final HashMap<String, Function<String, Object>> hashMap = new HashMap<>();
        hashMap.put(ThingCommand.RESOURCE_TYPE, IdCacheLoader::sudoRetrieveThing);
        return hashMap;
    }

    // TODO document as extension point
    protected HashMap<String, Function<Object, Entry<ResourceKey>>> buildTransformerMap() {
        final HashMap<String, Function<Object, Entry<ResourceKey>>> hashMap = new HashMap<>();
        // TODO transform responses.
        return hashMap;
    }

    private static SudoRetrieveThing sudoRetrieveThing(final String thingId) {
        return SudoRetrieveThing.of(thingId,
                JsonFieldSelector.newInstance(Thing.JsonFields.ID.getPointer(),
                        Thing.JsonFields.ACL.getPointer(),
                        Thing.JsonFields.POLICY_ID.getPointer()), DittoHeaders.empty());
    }
}
