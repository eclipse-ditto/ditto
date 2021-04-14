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

import static org.eclipse.ditto.model.base.entity.id.RegexPatterns.NAMESPACE_DELIMITER;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.type.EntityType;
import org.eclipse.ditto.model.base.entity.validation.EntityIdPatternValidator;

/**
 * Base implementation for namespaced entity IDs which are aware of their entity type.
 * <em>Subclasses are required to be immutable.</em>
 *
 * @since 1.1.0
 */
@Immutable
public abstract class AbstractNamespacedEntityId extends AbstractEntityId implements NamespacedEntityId {

    private final String namespace;
    private final String name;

    protected AbstractNamespacedEntityId(final EntityType entityType, final String namespace, final String name,
            final boolean shouldValidate) {
        super(entityType, shouldValidate ? validate(namespace, name) : namespace + NAMESPACE_DELIMITER + name);

        this.namespace = namespace;
        this.name = name;
    }

    protected AbstractNamespacedEntityId(final EntityType entityType, @Nullable final CharSequence entityId) {
        super(entityType, getNonEmptyEntityId(entityId));

        final EntityIdPatternValidator validator = EntityIdPatternValidator.getInstance(entityId);
        if (validator.isValid()) {
            // the given entityId is valid, so we can safely split at NAMESPACE_DELIMITER
            final String[] elements = entityId.toString().split(NAMESPACE_DELIMITER, 2);
            namespace = elements[0];
            name = elements[1];
        } else {
            throw NamespacedEntityIdInvalidException.newBuilder(entityId).build();
        }
    }

    private static CharSequence getNonEmptyEntityId(@Nullable final CharSequence entityId) {
        if (entityId == null || entityId.toString().isEmpty()) {
            throw NamespacedEntityIdInvalidException.newBuilder(entityId).build();
        }
        return entityId;
    }

    private static String validate(final @Nullable String namespace, final @Nullable String name) {
        final String sp = namespace + NAMESPACE_DELIMITER + name;

        if (namespace == null || name == null || !EntityIdPatternValidator.getInstance(sp).isValid()) {
            throw NamespacedEntityIdInvalidException.newBuilder(sp).build();
        }

        return sp;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

}
