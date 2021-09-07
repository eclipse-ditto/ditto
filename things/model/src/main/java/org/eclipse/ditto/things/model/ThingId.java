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
package org.eclipse.ditto.things.model;

import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.AbstractNamespacedEntityId;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityIdInvalidException;
import org.eclipse.ditto.base.model.entity.id.TypedEntityId;

/**
 * Java representation of a validated Thing ID.
 */
@Immutable
@TypedEntityId(type = "thing")
public final class ThingId extends AbstractNamespacedEntityId {

    private static final String DEFAULT_NAMESPACE = "";

    private ThingId(final CharSequence thingId) {
        super(ThingConstants.ENTITY_TYPE, thingId);
    }

    private ThingId(final String namespace, final String name, final boolean shouldValidate) {
        super(ThingConstants.ENTITY_TYPE, namespace, name, shouldValidate);
    }

    /**
     * Returns an instance of this class based on the given CharSequence.
     * May return the same instance as the parameter if the given parameter is already a ThingId.
     * Skips validation if the given {@code thingId} is an instance of NamespacedEntityId.
     *
     * @param thingId the thing ID.
     * @return the ID.
     * @throws ThingIdInvalidException if the given {@code thingId} is invalid.
     */
    public static ThingId of(final CharSequence thingId) {
        if (thingId instanceof ThingId) {
            return (ThingId) thingId;
        }

        if (thingId instanceof NamespacedEntityId) {
            final NamespacedEntityId namespacedEntityId = (NamespacedEntityId) thingId;
            return new ThingId(namespacedEntityId.getNamespace(), namespacedEntityId.getName(), false);
        }

        return wrapInThingIdInvalidException(() -> new ThingId(thingId));
    }

    /**
     * Returns an instance of this class with the given namespace and name.
     *
     * @param namespace the namespace of the thing.
     * @param name the name of the thing.
     * @return the created ID.
     */
    public static ThingId of(final String namespace, final String name) {
        return wrapInThingIdInvalidException(() -> new ThingId(namespace, name, true));
    }

    /**
     * Returns an instance of this class with default namespace placeholder.
     *
     * @param name the name of the thing.
     * @return the created ID.
     * @throws ThingIdInvalidException if for the given {@code name} a ThingId cannot be derived.
     */
    public static ThingId inDefaultNamespace(final String name) {
        return wrapInThingIdInvalidException(() -> new ThingId(DEFAULT_NAMESPACE, name, true));
    }

    /**
     * Generates a new thing ID with the default namespace placeholder and a unique name.
     *
     * @return the generated thing ID.
     */
    public static ThingId generateRandom() {
        return wrapInThingIdInvalidException(() -> new ThingId(DEFAULT_NAMESPACE, UUID.randomUUID().toString(), true));
    }

    private static <T> T wrapInThingIdInvalidException(final Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (final NamespacedEntityIdInvalidException e) {
            throw ThingIdInvalidException.newBuilder(e.getEntityId().orElse(null)).cause(e).build();
        }
    }

}
