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
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;

/**
 * Loads an enforcer by asking entity shard regions.
 */
@Immutable
public class EnforcerCacheLoader extends AbstractAskCacheLoader<Enforcer> {

    protected EnforcerCacheLoader(final Duration askTimeout, final EntityRegionMap entityRegionMap) {
        super(askTimeout, entityRegionMap);
    }

    @Override
    protected HashMap<String, Function<String, Object>> buildCommandMap() {
        final HashMap<String, Function<String, Object>> hashMap = super.buildCommandMap();
        hashMap.put(ThingCommand.RESOURCE_TYPE, AbstractAskCacheLoader::sudoRetrieveThing);
        hashMap.put(PolicyCommand.RESOURCE_TYPE, policyId -> SudoRetrievePolicy.of(policyId, DittoHeaders.empty()));
        return hashMap;
    }

    @Override
    protected HashMap<String, Function<Object, Entry<Enforcer>>> buildTransformerMap() {
        final HashMap<String, Function<Object, Entry<Enforcer>>> hashMap = super.buildTransformerMap();
        hashMap.put(ThingCommand.RESOURCE_TYPE, this::handleSudoRetrieveThingResponse);
        hashMap.put(PolicyCommand.RESOURCE_TYPE, this::handleSudoRetrievePolicyResponse);
        return hashMap;
    }

    @Nullable
    private Entry<Enforcer> handleSudoRetrieveThingResponse(final Object response) {
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

    private Entry<Enforcer> handleSudoRetrievePolicyResponse(final Object response) {
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
}
