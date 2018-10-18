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

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * A generic implementation of {@link ShutdownReason}, i. e. it can be instantiated with an arbitrary type and optional
 * details.
 */
@Immutable
final class GenericReason implements ShutdownReason {

    private final ShutdownReasonType type;
    @Nullable private final String details;

    private GenericReason(final ShutdownReasonType theType, @Nullable final CharSequence theDetails) {
        type = checkNotNull(theType, "ShutdownReasonType");
        details = null != theDetails ? argumentNotEmpty(theDetails, "details").toString() : null;
    }

    /**
     * Returns an instance of {@code GenericReason}.
     *
     * @param type the type of the returned reason.
     * @param details the details of the returned reason.
     * @return the instance.
     * @throws NullPointerException if {@code type} is {@code null}.
     * @throws IllegalArgumentException if {@code details} is empty.
     */
    public static GenericReason getInstance(final ShutdownReasonType type, @Nullable final CharSequence details) {
        return new GenericReason(type, details);
    }

    @Override
    public ShutdownReasonType getType() {
        return type;
    }

    @Override
    public Optional<String> getDetails() {
        return Optional.ofNullable(details);
    }

    @Override
    public String getDetailsOrThrow() {
        if (null != details) {
            return details;
        }
        throw new NoSuchElementException("This reason does not provide details!");
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GenericReason that = (GenericReason) o;
        return Objects.equals(type, that.type) && Objects.equals(details, that.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, details);
    }

    @Override
    public JsonObject toJson() {
        return toJson(FieldType.REGULAR.and(FieldType.notHidden()));
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        final Predicate<JsonField> extendedPredicate = schemaVersion.and(predicate);

        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder()
                .set(JsonFields.TYPE, getType().toString(), extendedPredicate);

        getDetails().ifPresent(d -> jsonObjectBuilder.set(JsonFields.DETAILS, d, extendedPredicate));

        return jsonObjectBuilder.build();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "type=" + type +
                ", details=" + details +
                "]";
    }

}
