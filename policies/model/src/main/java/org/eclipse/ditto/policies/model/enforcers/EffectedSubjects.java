/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model.enforcers;

import java.util.Set;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;

/**
 * Contains "granted" and "revoked" AuthorizationSubjects in the scope of a specific
 * {@link org.eclipse.ditto.policies.model.ResourceKey} and {@code Permissions} on that resource.
 * <p>
 * Implementations of this interface are required to be immutable!
 * </p>
 */
public interface EffectedSubjects {

    /**
     * Returns the authorization subjects which are "granted" in the scope of the given resource and permissions.
     *
     * @return an unmodifiable Set of the granted authorization subjects.
     */
    Set<AuthorizationSubject> getGranted();

    /**
     * Returns the authorization subjects which are "revoked" in the scope of the given resource and permissions.
     *
     * @return an unmodifiable Set of the revoked authorization subjects.
     */
    Set<AuthorizationSubject> getRevoked();

}
