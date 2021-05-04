/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.base.model.signals.Signal;

import akka.actor.ActorRef;

/**
 * Represent outbound signals that was mapped from one signal.
 */
final class MultiMappedOutboundSignal implements OutboundSignal.MultiMapped {

    private final List<Mapped> outboundSignals;
    private final ActorRef sender;

    MultiMappedOutboundSignal(final List<Mapped> outboundSignals, @Nullable final ActorRef sender) {
        this.outboundSignals = argumentNotEmpty(outboundSignals, "outboundSignals");
        this.sender = sender;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final MultiMappedOutboundSignal that = (MultiMappedOutboundSignal) o;
        return Objects.equals(outboundSignals, that.outboundSignals) && Objects.equals(sender, that.sender);
    }

    @Override
    public int hashCode() {
        return Objects.hash(outboundSignals, sender);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "outboundSignals=" + outboundSignals +
                ", sender=" + sender +
                "]";
    }

    @Override
    public Signal<?> getSource() {
        return first().getSource();
    }

    @Override
    public List<Target> getTargets() {
        return first().getTargets();
    }

    @Override
    public List<Mapped> getMappedOutboundSignals() {
        return outboundSignals;
    }

    @Override
    public Optional<ActorRef> getSender() {
        return Optional.ofNullable(sender);
    }

    @Override
    public Mapped first() {
        return outboundSignals.get(0);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        final JsonArray items = outboundSignals.stream()
                .map(outboundSignal -> outboundSignal.toJson(schemaVersion, predicate))
                .collect(JsonCollectors.valuesToArray());
        return JsonObject.newBuilder()
                .set("mappedOutboundSignals", items)
                .set("sender", sender.toString())
                .build();
    }
}
