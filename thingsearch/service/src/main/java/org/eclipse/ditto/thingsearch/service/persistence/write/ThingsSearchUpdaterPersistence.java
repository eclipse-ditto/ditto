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
package org.eclipse.ditto.thingsearch.service.persistence.write;

import java.util.Map;

import org.eclipse.ditto.internal.utils.persistence.operations.NamespacePersistenceOperations;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.thingsearch.api.PolicyReferenceTag;

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

}
