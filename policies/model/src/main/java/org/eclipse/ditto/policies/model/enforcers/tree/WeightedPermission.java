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

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;

import java.util.Objects;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;

/**
 * This class represents a permission which has a weight. The higher the weight of a permission is the more likely it is
 * applied during policy enforcement.
 */
@Immutable
@ParametersAreNonnullByDefault
final class WeightedPermission {

    private final String permission;
    private final int weight;

    private WeightedPermission(final String thePermission, final int theWeight) {
        permission = thePermission;
        weight = theWeight;
    }

    /**
     * Returns a new instance of {@code WeightedPermission}.
     *
     * @param permission the permission.
     * @param weight the weight of the permission.
     * @return the instance.
     * @throws NullPointerException if {@code permission} is {@code null}.
     * @throws IllegalArgumentException if {@code permission} is empty.
     */
    public static WeightedPermission of(final CharSequence permission, final int weight) {
        argumentNotEmpty(permission, "permission");
        return new WeightedPermission(permission.toString(), weight);
    }

    /**
     * Returns the permission.
     *
     * @return the permission.
     */
    public String getPermission() {
        return permission;
    }

    /**
     * Returns the weight.
     *
     * @return the weight.
     */
    public int getWeight() {
        return weight;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final WeightedPermission that = (WeightedPermission) o;
        return weight == that.weight &&
                Objects.equals(permission, that.permission);
    }

    @Override
    public int hashCode() {
        return Objects.hash(permission, weight);
    }

    /**
     * Returns a string representation of this weighted permission according to pattern
     * {@code ({permission}, {weight})} like for example {@code (READ, 3)}.
     *
     * @return the string representation.
     */
    @Override
    public String toString() {
        return "(" + permission + ", " + weight + ")";
    }

}
