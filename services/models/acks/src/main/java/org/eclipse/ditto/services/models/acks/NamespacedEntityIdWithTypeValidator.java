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
package org.eclipse.ditto.services.models.acks;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.entity.id.NamespacedEntityId;
import org.eclipse.ditto.model.base.entity.id.NamespacedEntityIdWithType;

/**
 * Validator validating {@code NamespacedEntityIdWithType} instances in a way that the {@code namespace} part of the
 * NamespacedEntityIdWithType may be empty (which can e.g. be the case for "Create Thing" commands where the default
 * namespace is added at a later step) and only the {@code name} part has to be equal to each other.
 *
 * @since 1.1.0
 */
@Immutable
final class NamespacedEntityIdWithTypeValidator extends AbstractEntityIdValidator<NamespacedEntityIdWithType> {

    private NamespacedEntityIdWithTypeValidator(final NamespacedEntityIdWithType expected) {
        super(expected);
    }

    static NamespacedEntityIdWithTypeValidator getInstance(final NamespacedEntityIdWithType expected) {
        return new NamespacedEntityIdWithTypeValidator(expected);
    }

    @Override
    protected boolean areEqual(final EntityIdWithType actual, final NamespacedEntityIdWithType expected) {
        return super.areEqual(actual, expected) || areNamesEqual(actual, expected);
    }

    private static boolean areNamesEqual(final EntityIdWithType actual, final NamespacedEntityId expected) {
        boolean result = false;
        if (actual instanceof NamespacedEntityId) {
            final String expectedNamespace = expected.getNamespace();
            final String actualName = ((NamespacedEntityId) actual).getName();
            result = expectedNamespace.isEmpty() && Objects.equals(actualName, expected.getName());
        }
        return result;
    }

}
