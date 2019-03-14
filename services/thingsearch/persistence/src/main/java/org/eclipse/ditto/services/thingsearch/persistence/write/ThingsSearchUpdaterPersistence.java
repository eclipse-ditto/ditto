/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.thingsearch.persistence.write;

import java.util.Map;

import org.eclipse.ditto.services.models.policies.PolicyReferenceTag;
import org.eclipse.ditto.services.models.policies.PolicyTag;
import org.eclipse.ditto.services.utils.persistence.mongo.namespace.NamespaceOps;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * The persistence for the search updater service.
 */
public interface ThingsSearchUpdaterPersistence extends NamespaceOps<String> {

    /**
     * Retrieves modifiable unsorted list of policy reference tags that match the given policy IDs.
     *
     * @param policyRevisions map from relevant policy IDs to their revisions.
     * @return a {@link Source} holding the publisher to execute the operation.
     */
    Source<PolicyReferenceTag, NotUsed> getPolicyReferenceTags(Map<String, Long> policyRevisions);

    /**
     * Retrieves a source of Thing IDs with the given policy ID and out-dated revision.
     *
     * @param policyTag contains policy ID and policy revision.
     * @return a Source holding the publisher to execute the operation.
     */
    Source<String, NotUsed> getOutdatedThingIds(PolicyTag policyTag);
}
