/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.notifications.base;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Superclass of implementations of notification.
 *
 * @param <T> type of a concrete subclass.
 */
public abstract class AbstractNotification<T extends AbstractNotification<T>> implements Notification<T> {

    private final DittoHeaders dittoHeaders;

    /**
     * Create a notification object.
     *
     * @param dittoHeaders the Ditto headers.
     */
    protected AbstractNotification(final DittoHeaders dittoHeaders) {
        this.dittoHeaders = checkNotNull(dittoHeaders, "dittoHeaders");
    }

    /**
     * Append notification-specific payload to the passed {@code jsonObjectBuilder}.
     *
     * @param jsonObjectBuilder the JsonObjectBuilder to add the payload to.
     * @param predicate the predicate to evaluate when adding the payload.
     */
    protected abstract void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final Predicate<JsonField> predicate);

    @Override
    public String getManifest() {
        return getType();
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    @Override
    public JsonObject toJson() {
        return toJson(JsonSchemaVersion.LATEST, FieldType.all());
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(JSON_TYPE, getType(), predicate);

        appendPayload(jsonObjectBuilder, predicate);

        return jsonObjectBuilder.build();
    }

    @Override
    public String toString() {
        return "type=" + getType() + ", dittoHeaders=" + dittoHeaders;
    }

    @Override
    public boolean equals(final Object other) {
        if (other != null) {
            return getClass().equals(other.getClass()) &&
                    Objects.equals(dittoHeaders, ((AbstractNotification<?>) other).dittoHeaders);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(dittoHeaders);
    }
}
