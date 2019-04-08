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
package org.eclipse.ditto.model.enforcers;

import java.util.Set;

/**
 * Contains "granted" and "revoked" {@code subjectId}s in the scope of a specific
 * {@link org.eclipse.ditto.model.policies.ResourceKey} and {@code Permissions} on that resource.
 */
public interface EffectedSubjectIds {

    /**
     * Returns the subject IDs which are "granted" in the scope of the given resource and permissions.
     *
     * @return the granted subject IDs.
     */
    Set<String> getGranted();

    /**
     * Returns the subject IDs which are "revoked" in the scope of the given resource and permissions.
     *
     * @return the revoked subject IDs.
     */
    Set<String> getRevoked();

}
