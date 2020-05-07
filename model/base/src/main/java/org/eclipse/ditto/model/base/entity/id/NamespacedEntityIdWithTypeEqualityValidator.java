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
package org.eclipse.ditto.model.base.entity.id;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * Validator validating {@code NamespacedEntityIdWithType} instances in a way that the {@code namespace} part of the
 * NamespacedEntityIdWithType may be empty (which can e.g. be the case for "Create Thing" commands where the default
 * namespace is added at a later step) and only the {@code name} part has to be equal to each other.
 *
 * @since 1.1.0
 */
@Immutable
final class NamespacedEntityIdWithTypeEqualityValidator extends
        EntityIdEqualityValidator<NamespacedEntityIdWithType> {

    private NamespacedEntityIdWithTypeEqualityValidator(final NamespacedEntityIdWithType expected) {
        super(expected);
    }

    static NamespacedEntityIdWithTypeEqualityValidator getInstance(final NamespacedEntityIdWithType expected) {
        return new NamespacedEntityIdWithTypeEqualityValidator(expected);
    }

    @Override
    protected boolean areEqual(final EntityIdWithType actual, final NamespacedEntityIdWithType expected) {
        return super.areEqual(actual, expected) || areNamesEqual(actual, expected);
    }

    private static boolean areNamesEqual(final EntityIdWithType actual, final NamespacedEntityId expected) {
        boolean result = false;
        if (actual instanceof NamespacedEntityId) {
            final String expectedNamespace = expected.getNamespace();
            final NamespacedEntityId actualEntityId = (NamespacedEntityId) actual;
            result = (expectedNamespace.isEmpty() || actualEntityId.getNamespace().isEmpty()) &&
                    Objects.equals(actualEntityId.getName(), expected.getName());
        }
        return result;
    }

}
