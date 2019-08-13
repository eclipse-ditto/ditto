/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.thingsearch.persistence.write;

import java.util.Map;

import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.models.policies.PolicyReferenceTag;
import org.eclipse.ditto.services.models.policies.PolicyTag;
import org.eclipse.ditto.services.utils.persistence.operations.NamespacePersistenceOperations;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * The persistence for the search updater service.
 */
public interface ThingsSearchUpdaterPersistence extends NamespacePersistenceOperations {

    /**
     * Retrieves modifiable unsorted list of policy reference tags that match the given policy IDs.
     *
     * @param policyRevisions map from relevant policy IDs to their revisions.
     * @return a {@link Source} holding the publisher to execute the operation.
     */
    Source<PolicyReferenceTag, NotUsed> getPolicyReferenceTags(Map<PolicyId, Long> policyRevisions);

    /**
     * Retrieves a source of Thing IDs with the given policy ID and out-dated revision.
     *
     * @param policyTag contains policy ID and policy revision.
     * @return a Source holding the publisher to execute the operation.
     */
    Source<ThingId, NotUsed> getOutdatedThingIds(PolicyTag policyTag);
}
