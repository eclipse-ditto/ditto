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
package org.eclipse.ditto.policies.model.enforcers.tree;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

/**
 * Wrapper to hold the granted and revoked resources.
 */
@Immutable
final class EffectedResources {

    private final Set<TreeBasedPolicyEnforcer.PointerAndPermission> grantedResources;
    private final Set<TreeBasedPolicyEnforcer.PointerAndPermission> revokedResources;

    private EffectedResources(final Set<TreeBasedPolicyEnforcer.PointerAndPermission> grantedResources,
            final Set<TreeBasedPolicyEnforcer.PointerAndPermission> revokedResources) {
        this.grantedResources = Collections.unmodifiableSet(new HashSet<>(grantedResources));
        this.revokedResources = Collections.unmodifiableSet(new HashSet<>(revokedResources));
    }

    /**
     * Returns a new {@code EffectedResources} for the given {@code grantedResources} and {@code revokedResources}.
     *
     * @param grantedResources the resources which have granted permission.
     * @param revokedResources the resources which have revoked permission.
     * @return the EffectedResources.
     */
    public static EffectedResources of(final Set<TreeBasedPolicyEnforcer.PointerAndPermission> grantedResources,
            final Set<TreeBasedPolicyEnforcer.PointerAndPermission> revokedResources) {
        checkNotNull(grantedResources, "Granted Resources");
        checkNotNull(revokedResources, "Revoked Resources");

        return new EffectedResources(grantedResources, revokedResources);
    }

    /**
     * Returns all granted resources.
     *
     * @return a list of granted resources
     */
    public Set<TreeBasedPolicyEnforcer.PointerAndPermission> getGrantedResources() {
        return grantedResources;
    }

    /**
     * Returns all revoked resources.
     *
     * @return a list of revoked resources
     */
    public Set<TreeBasedPolicyEnforcer.PointerAndPermission> getRevokedResources() {
        return revokedResources;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EffectedResources that = (EffectedResources) o;
        return Objects.equals(grantedResources, that.grantedResources) &&
                Objects.equals(revokedResources, that.revokedResources);
    }

    @Override
    public int hashCode() {
        return Objects.hash(grantedResources, revokedResources);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "grantedResources=" + grantedResources +
                ", revokedResources=" + revokedResources +
                ']';
    }

}
