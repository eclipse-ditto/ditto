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
package org.eclipse.ditto.connectivity.api.placeholders;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;

/**
 * Abstract base class for entity id placeholders.
 */
abstract class AbstractEntityIdPlaceholder<T extends NamespacedEntityId> implements EntityIdPlaceholder {

    private static final String ID_PLACEHOLDER = "id";
    private static final String NAMESPACE_PLACEHOLDER = "namespace";
    private static final String NAME_PLACEHOLDER = "name";

    private static final List<String> SUPPORTED = Collections.unmodifiableList(
            Arrays.asList(ID_PLACEHOLDER, NAMESPACE_PLACEHOLDER, NAME_PLACEHOLDER));

    protected AbstractEntityIdPlaceholder() {
    }

    @Override
    public List<String> getSupportedNames() {
        return SUPPORTED;
    }

    @Override
    public boolean supports(final String name) {
        return SUPPORTED.contains(name);
    }

    protected Optional<String> doResolve(final T entityId, final String placeholder) {
        switch (placeholder) {
            case NAMESPACE_PLACEHOLDER:
                return Optional.of(entityId.getNamespace());
            case NAME_PLACEHOLDER:
                return Optional.of(entityId.getName());
            case ID_PLACEHOLDER:
                return Optional.of(entityId.toString());
            default:
                return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[]";
    }
}
