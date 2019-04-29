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
package org.eclipse.ditto.services.models.connectivity;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.signals.base.Signal;

/**
 * Represent an outbound signal that was mapped to an external message. It wraps the original signal, the mapped
 * external message and the targets that are allowed to receive this external message.
 */
final class MappedOutboundSignal implements OutboundSignal.WithExternalMessage {

    private final OutboundSignal delegate;
    private final ExternalMessage externalMessage;

    MappedOutboundSignal(final OutboundSignal delegate, final ExternalMessage externalMessage) {
        this.delegate = delegate;
        this.externalMessage = externalMessage;
    }

    @Override
    public ExternalMessage getExternalMessage() {
        return externalMessage;
    }

    @Override
    public Signal<?> getSource() {
        return delegate.getSource();
    }

    @Override
    public List<Target> getTargets() {
        return delegate.getTargets();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        // the externalMessage is omitted as this should not be required to go over the wire
        return delegate.toJson(schemaVersion, thePredicate);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MappedOutboundSignal)) {
            return false;
        }
        final MappedOutboundSignal that = (MappedOutboundSignal) o;
        return Objects.equals(delegate, that.delegate) &&
                Objects.equals(externalMessage, that.externalMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate, externalMessage);
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "delegate=" + delegate +
                ", externalMessage=" + externalMessage +
                "]";
    }
}
