/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.common;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;

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
     * {@link org.eclipse.ditto.signals.commands.common.GenericReason.JsonFields#TYPE} or further required fields.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ShutdownReason fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "reason JSON object");
        final ShutdownReasonType type = getType(jsonObject.getValueOrThrow(ShutdownReason.JsonFields.TYPE));
        final Optional<String> detailsOptional = jsonObject.getValue(ShutdownReason.JsonFields.DETAILS);

        if (ShutdownReasonType.Known.PURGE_NAMESPACE.equals(type)) {
            return getPurgeNamespaceReason(detailsOptional.orElseThrow(
                    () -> new JsonMissingFieldException(ShutdownReason.JsonFields.DETAILS)));
        }
        return GenericReason.getInstance(type, detailsOptional.orElse(null));
    }

    private static ShutdownReasonType getType(final CharSequence typeName) {
        return ShutdownReasonType.Known.forTypeName(typeName).orElseGet(() -> ShutdownReasonType.Unknown.of(typeName));
    }

    /**
     * Returns an instance of {@code ShutdownReasonType} for an arbitrary type without details.
     *
     * @param type the type of the returned reason.
     * @return the instance.
     * @throws NullPointerException if {@code type} is {@code null}.
     * @throws IllegalArgumentException if {@code details} is empty.
     */
    public static ShutdownReason getShutdownReason(final ShutdownReasonType type) {
        return getShutdownReason(type, null);
    }

    /**
     * Returns an instance of {@code ShutdownReasonType} for an arbitrary type.
     *
     * @param type the type of the returned reason.
     * @param details the details of the returned reason or {@code null}.
     * @return the instance.
     * @throws NullPointerException if {@code type} is {@code null}.
     */
    public static ShutdownReason getShutdownReason(final ShutdownReasonType type, @Nullable final String details) {
        return GenericReason.getInstance(type, details);
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

}
