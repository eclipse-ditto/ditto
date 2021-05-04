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

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * A dedicated {@link ShutdownReason} for purging a namespace.
 * The details are guaranteed to be the non-empty namespace.
 */
@Immutable
final class PurgeNamespaceReason implements ShutdownReason {

    private static final ShutdownReasonType type = ShutdownReasonType.Known.PURGE_NAMESPACE;
    private final String namespaceToPurge;

    private PurgeNamespaceReason(final String namespaceToPurge) {
        this.namespaceToPurge = namespaceToPurge;
    }

    /**
     * Returns an instance of {@code PurgeNamespaceReason}.
     *
     * @param namespace the namespace to be purged.
     * @return the instance.
     * @throws NullPointerException if {@code namespace} is {@code null}.
     * @throws IllegalArgumentException if {@code namespace} is empty.
     */
    public static PurgeNamespaceReason of(final CharSequence namespace) {
        return new PurgeNamespaceReason(argumentNotEmpty(namespace, "namespace").toString());
    }

    static PurgeNamespaceReason fromJson(final JsonObject jsonObject) {
        return new PurgeNamespaceReason(jsonObject.getValueOrThrow(JsonFields.DETAILS).asString());
    }

    @Override
    public ShutdownReasonType getType() {
        return type;
    }

    @Override
    public boolean isRelevantFor(final Object value) {
        return namespaceToPurge.equals(value);
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
        jsonObjectBuilder.set(JsonFields.DETAILS, JsonValue.of(namespaceToPurge), extendedPredicate);

        return jsonObjectBuilder.build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PurgeNamespaceReason that = (PurgeNamespaceReason) o;
        return Objects.equals(namespaceToPurge, that.namespaceToPurge);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespaceToPurge);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "namespaceToPurge=" + namespaceToPurge +
                "]";
    }

}
