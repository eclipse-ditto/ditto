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
package org.eclipse.ditto.base.api.common;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.entity.id.EntityId;

/**
 * A factory for getting instances of {@link ShutdownReason}.
 */
@Immutable
public final class ShutdownReasonFactory {

    private ShutdownReasonFactory() {
        throw new AssertionError();
    }

    /**
     * Creates a new {@code ShutdownReason} object from the given JSON object.
     *
     * @param jsonObject the JSON object of which the ShutdownReason is to be created.
     * @return the parsed reason.
     * @throws NullPointerException if {@code reasonJsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain
     * {@link org.eclipse.ditto.base.api.common.ShutdownReason.JsonFields#TYPE} or further required fields.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ShutdownReason fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "reason JSON object");

        final ShutdownReasonType type = getReasonType(jsonObject);

        if (ShutdownReasonType.Known.PURGE_NAMESPACE.equals(type)) {
            return PurgeNamespaceReason.fromJson(jsonObject);
        } else if (ShutdownReasonType.Known.PURGE_ENTITIES.equals(type)) {
            return PurgeEntitiesReason.fromJson(jsonObject);
        } else {
            return ShutdownNoReason.INSTANCE;
        }
    }

    private static ShutdownReasonType getReasonType(final JsonObject shutdownReasonJson) {
        final String typeName = shutdownReasonJson.getValue(ShutdownReason.JsonFields.TYPE).orElse("");
        return ShutdownReasonType.Known.forTypeName(typeName).orElseGet(() -> ShutdownReasonType.Unknown.of(typeName));
    }

    /**
     * Returns an instance of {@code ShutdownReason} for indicating the purging of a namespace.
     *
     * @param namespace the namespace to be purged.
     * @return the instance.
     * @throws NullPointerException if {@code namespace} is {@code null}.
     * @throws IllegalArgumentException if {@code namespace} is empty.
     */
    public static ShutdownReason getPurgeNamespaceReason(final CharSequence namespace) {
        return PurgeNamespaceReason.of(namespace);
    }

    /**
     * Returns an instance of {@code ShutdownReason} for indicating the purging of entities.
     *
     * @param entityIds the entities to be purged.
     * @return the instance.
     * @throws NullPointerException if {@code entityIds} is {@code null}.
     * @throws IllegalArgumentException if {@code entityIds} is empty.
     */
    public static ShutdownReason getPurgeEntitiesReason(final List<EntityId> entityIds) {
        return PurgeEntitiesReason.of(entityIds);
    }

}
