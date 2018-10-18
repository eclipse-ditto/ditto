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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * A dedicated {@link ShutdownReason} for purging a namespace.
 * The details are guaranteed to be the non-empty namespace.
 */
@Immutable
final class PurgeNamespaceReason implements ShutdownReason {

    private final ShutdownReason reason;

    private PurgeNamespaceReason(final ShutdownReason theReason) {
        reason = theReason;
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
        return new PurgeNamespaceReason(
                ShutdownReasonFactory.getShutdownReason(ShutdownReasonType.Known.PURGE_NAMESPACE,
                        argumentNotEmpty(namespace, "namespace").toString()));
    }

    @Override
    public ShutdownReasonType getType() {
        return reason.getType();
    }

    @Override
    public Optional<String> getDetails() {
        return reason.getDetails();
    }

    /**
     * Returns the namespace to be purged.
     *
     * @return the namespace.
     */
    @SuppressWarnings({"OptionalGetWithoutIsPresent", "squid:S3655"})
    public String getNamespace() {
        return getDetails().get();
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
        return Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reason);
    }

    @Override
    public JsonObject toJson() {
        return reason.toJson();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        return reason.toJson(schemaVersion, predicate);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "reason=" + reason +
                "]";
    }

}
