/*
Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.api;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkArgument;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.connectivity.model.PayloadMapping;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.base.model.signals.Signal;

/**
 * Represent an outbound signal that is ready to be mapped with the given {@link org.eclipse.ditto.connectivity.model.PayloadMapping}.
 * This class is used to group targets that have the same payload mapping defined.
 */
final class MappableOutboundSignal implements OutboundSignal.Mappable {

    private final OutboundSignal delegate;
    private final PayloadMapping payloadMapping;

    MappableOutboundSignal(final Signal<?> signal, final List<Target> targets, final PayloadMapping payloadMapping) {
        checkNotNull(signal, "signal");
        this.payloadMapping = checkNotNull(payloadMapping, "payloadMapping");

        checkNotNull(targets, "targets");
        checkArgument(targets, verifyPayloadMappings(payloadMapping), () -> "Payload mappings must all be equal.");
        delegate = OutboundSignalFactory.newOutboundSignal(signal, targets);
    }

    private static Predicate<List<Target>> verifyPayloadMappings(final PayloadMapping payloadMapping) {
        return targets1 -> targets1.stream().allMatch(t -> payloadMapping.equals(t.getPayloadMapping()));
    }

    @Override
    public PayloadMapping getPayloadMapping() {
        return payloadMapping;
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
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MappableOutboundSignal that = (MappableOutboundSignal) o;
        return Objects.equals(delegate, that.delegate) && Objects.equals(payloadMapping, that.payloadMapping);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate, payloadMapping);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "delegate=" + delegate +
                ", payloadMapping=" + payloadMapping +
                "]";
    }

}
