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
package org.eclipse.ditto.base.model.namespaces;

import java.util.Optional;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;

/**
 * A reader which provides functionality to parse namespaces.
 */
public final class NamespaceReader {

    private static final char NAMESPACE_SEPARATOR = ':';

    private NamespaceReader() {
        throw new AssertionError();
    }

    /**
     * Reads the namespace from the identifier of an entity.
     *
     * @param id the identifier.
     * @return the optional namespace or an empty optional if a namespace can't be read.
     */
    public static Optional<String> fromEntityId(final EntityId id) {
        if (id instanceof NamespacedEntityId) {
           return fromEntityId((NamespacedEntityId) id);
        }

        return fromEntityId(id.toString());
    }

    /**
     * Reads the namespace from the identifier of an entity.
     *
     * @param id the identifier.
     * @return the optional namespace or an empty optional if a namespace can't be read.
     */
    public static Optional<String> fromEntityId(final String id) {
        final int i = id.indexOf(NAMESPACE_SEPARATOR);
        return i >= 0
                ? Optional.of(id.substring(0, i))
                : Optional.empty();
    }

    /**
     * Reads the namespace from the identifier of an entity.
     *
     * @param id the identifier.
     * @return the optional namespace or an empty optional if a namespace can't be read.
     */
    public static Optional<String> fromEntityId(final NamespacedEntityId id) {
        return Optional.of(id.getNamespace());
    }
}
