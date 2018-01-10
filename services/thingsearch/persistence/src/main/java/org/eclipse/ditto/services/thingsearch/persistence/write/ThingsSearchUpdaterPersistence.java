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
package org.eclipse.ditto.services.thingsearch.persistence.write;

import java.util.List;
import java.util.Set;

import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.models.policies.PolicyTag;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * The persistence for the search updater service.
 */
public interface ThingsSearchUpdaterPersistence {

    /**
     * Inserts or updates a passed in {@link Thing}, enforcing restrictions on its properties.
     *
     * @param thing the thing to insert or update.
     * @param revision the revision to perform the upsert operation with.
     * @param policyRevision the revision of the policy to also persist.
     * @return a {@link Source} holding the publisher to execute the operation.
     */
    Source<Boolean, NotUsed> insertOrUpdate(Thing thing, long revision, long policyRevision);

    /**
     * Marks the thing with the passed in thingId as deleted.
     *
     * @param thingId the thingId of the thing to delete.
     * @param revision the revision to check.
     * @return a {@link Source} holding the publisher to execute the operation.
     */
    Source<Boolean, NotUsed> delete(String thingId, long revision);

    /**
     * Marks the thing with the passed in thingId as deleted.
     *
     * @param thingId the thingId of the thing to delete.
     * @return a {@link Source} holding the publisher to execute the operation.
     */
    Source<Boolean, NotUsed> delete(String thingId);

    /**
     * Perform all updates represented by {@code gatheredEvents} in an ordered manner. If any ot the updates fails,
     * no update will be executed.
     *
     * @param thingId the id of the thing to update.
     * @param gatheredEvents the combined thing writes to execute in this update.
     * @param policyEnforcer The policy enforcer to enforce a policy.
     * @param targetRevision The target revision number after the writes have been executed. Should be equal to the
     * revision of the last element in {@code gatheredEvents}.
     * @return a {@link Source} holding the publisher to execute the operation.
     */
    Source<Boolean, NotUsed> executeCombinedWrites(String thingId, List<ThingEvent> gatheredEvents,
            PolicyEnforcer policyEnforcer, long targetRevision);

    /**
     * Updates the thing representation as well as the policy index due to the passed in thing; must be called after the
     * parameter {@code thing} was written into the index via {@link #insertOrUpdate(Thing, long, long)}.
     *
     * @param thing the thing for which there is updated the policy.
     * @param policyEnforcer the enforcer holding the current policy.
     * @return a {@link Source} holding the publisher to execute the operation.
     */
    Source<Boolean, NotUsed> updatePolicy(Thing thing, PolicyEnforcer policyEnforcer);

    /**
     * Retrieves a modifiable unsorted list of thing ids which all share the same policy.
     *
     * @param policyId the id of the policy for which the thing ids should be loaded.
     * @return a {@link Source} holding the publisher to execute the operation.
     */
    Source<Set<String>, NotUsed> getThingIdsForPolicy(String policyId);

    /**
     * Retrieves a source of Thing IDs with the given policy ID and out-dated revision.
     *
     * @param policyTag contains policy ID and policy revision.
     * @return a Source holding the publisher to execute the operation.
     */
    Source<String, NotUsed> getOutdatedThingIds(PolicyTag policyTag);

    /**
     * Retrieves the metadata (revision, policyId and policyRevision) how it is persisted in the search index of the
     * passed {@code thingId}.
     *
     * @param thingId the id of the Thing for which to retrieve the metadata for.
     * @return a {@link Source} holding the publisher to execute the operation.
     */
    Source<ThingMetadata, NotUsed> getThingMetadata(String thingId);

}
