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
package org.eclipse.ditto.model.placeholders;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.id.DefaultNamespacedEntityId;
import org.eclipse.ditto.model.base.entity.id.NamespacedEntityId;
import org.eclipse.ditto.model.base.entity.id.NamespacedEntityIdInvalidException;

/**
 * Placeholder implementation that replaces {@code entity:id}, {@code entity:namespace} and {@code entity:name}. The
 * input value is a String and must be a valid Entity ID.
 */
@Immutable
final class ImmutableEntityPlaceholder extends AbstractEntityPlaceholder<NamespacedEntityId>
        implements EntityPlaceholder {

    /**
     * Singleton instance of the ImmutableEntityPlaceholder.
     */
    static final ImmutableEntityPlaceholder INSTANCE = new ImmutableEntityPlaceholder();

    @Override
    public String getPrefix() {
        return "entity";
    }

    @Override
    public Optional<String> resolve(final CharSequence entityId, final String placeholder) {
        argumentNotEmpty(placeholder, "placeholder");
        checkNotNull(entityId, "Entity ID");
        try {
            return doResolve(DefaultNamespacedEntityId.of(entityId), placeholder);
        } catch (final NamespacedEntityIdInvalidException e) {
            // not a namespaced entity ID; does not resolve.
            return Optional.empty();
        }
    }
}
