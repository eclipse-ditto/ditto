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
