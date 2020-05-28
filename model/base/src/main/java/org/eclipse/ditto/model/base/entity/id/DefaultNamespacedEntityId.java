/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.model.base.entity.validation.RegexPatterns.NAMESPACE_DELIMITER;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.validation.EntityIdPatternValidator;

/**
 * Default implementation for a validated {@link org.eclipse.ditto.model.base.entity.id.NamespacedEntityId}
 */
@Immutable
public final class DefaultNamespacedEntityId implements NamespacedEntityId {

    private static final NamespacedEntityId DUMMY_ID = DefaultNamespacedEntityId.of(":_");
    private static final String DEFAULT_NAMESPACE = "";

    private final String namespace;
    private final String name;
    private final String stringRepresentation;

    private DefaultNamespacedEntityId(final String namespace, final String name, final boolean shouldValidate) {
        if (shouldValidate) {
            stringRepresentation = EntityIdPatternValidator.getInstance().validate(namespace, name);
        } else {
            stringRepresentation = namespace + NAMESPACE_DELIMITER + name;
        }

        this.namespace = namespace;
        this.name = name;
    }

    private DefaultNamespacedEntityId(@Nullable final CharSequence entityId) {
        if (entityId == null) {
            throw NamespacedEntityIdInvalidException.newBuilder(entityId).build();
        }

        final EntityIdPatternValidator validator = EntityIdPatternValidator.getInstance();

        if (validator.isValid(entityId)) {
            namespace = validator.getNamespace();
            name = validator.getName();
            stringRepresentation = validator.getString();
        } else {
            throw NamespacedEntityIdInvalidException.newBuilder(entityId).build();
        }
    }

    /**
     * Returns a {@link NamespacedEntityId} based on the given entityId CharSequence. May return the same instance as
     * the parameter if the given parameter is already a DefaultNamespacedEntityId. Skips validation if the given
     * {@code entityId} is an instance of NamespacedEntityId.
     *
     * @param entityId the entity ID.
     * @return the namespaced entity ID.
     * @throws NamespacedEntityIdInvalidException if the given {@code entityId} is invalid.
     */
    public static NamespacedEntityId of(final CharSequence entityId) {
        if (entityId instanceof DefaultNamespacedEntityId) {
            return (NamespacedEntityId) entityId;
        }

        if (entityId instanceof NamespacedEntityId) {
            final String namespace = ((NamespacedEntityId) entityId).getNamespace();
            final String name = ((NamespacedEntityId) entityId).getName();
            return new DefaultNamespacedEntityId(namespace, name, false);
        }

        return new DefaultNamespacedEntityId(entityId);
    }

    /**
     * Creates {@link NamespacedEntityId} with default namespace placeholder.
     *
     * @param entityName the name of the entity.
     * @return the created namespaced entity ID.
     * @throws NamespacedEntityIdInvalidException if the given {@code entityName} is invalid.
     */
    public static NamespacedEntityId fromName(final String entityName) {
        return of(DEFAULT_NAMESPACE, entityName);
    }

    /**
     * Creates a new {@link NamespacedEntityId} with the given namespace and name.
     *
     * @param namespace the namespace of the entity.
     * @param name the name of the entity.
     * @return the created instance of {@link NamespacedEntityId}
     */
    public static NamespacedEntityId of(final String namespace, final String name) {
        return new DefaultNamespacedEntityId(namespace, name, true);
    }

    /**
     * Returns a dummy {@link NamespacedEntityId}. This ID should not be used. It can be identified by
     * checking {@link NamespacedEntityId#isDummy()}.
     *
     * @return the dummy ID.
     */
    public static NamespacedEntityId dummy() {
        return DUMMY_ID;
    }

    @Override
    public boolean isDummy() {
        return DUMMY_ID.equals(this);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultNamespacedEntityId that = (DefaultNamespacedEntityId) o;
        return Objects.equals(namespace, that.namespace) &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, name);
    }


    @Override
    public String toString() {
        return stringRepresentation;
    }

}
