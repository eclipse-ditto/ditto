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
package org.eclipse.ditto.services.models.connectivity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Represents an outbound signal before it was mapped to an {@link ExternalMessage}.
 */
@Immutable
final class UnmappedOutboundSignal implements OutboundSignal {

    private final Signal<?> source;
    private final Set<Target> targets;

    UnmappedOutboundSignal(final Signal<?> source, final Set<Target> targets) {
        this.source = source;
        this.targets = Collections.unmodifiableSet(new HashSet<>(targets));
    }

    /**
     * Creates a new {@code OutboundSignal} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the OutboundSignal to be created.
     * @param mappingStrategy the {@link MappingStrategy} to use in order to parse the in the JSON included
     * {@code source} Signal
     * @return a new OutboundSignal which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static OutboundSignal fromJson(final JsonObject jsonObject, final MappingStrategy mappingStrategy) {

        final JsonObject readSourceObj = jsonObject.getValueOrThrow(JsonFields.SOURCE);
        final String commandType = readSourceObj.getValueOrThrow(Command.JsonFields.TYPE);
        final Jsonifiable signalJsonifiable = mappingStrategy.determineStrategy().get(commandType)
                .apply(readSourceObj, DittoHeaders.empty());

        final JsonArray readTargetsArr = jsonObject.getValueOrThrow(JsonFields.TARGETS);
        final Set<Target> targets = readTargetsArr.stream()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(ConnectivityModelFactory::targetFromJson)
                .collect(Collectors.toSet());

        return new UnmappedOutboundSignal((Signal<?>) signalJsonifiable, targets);
    }

    @Override
    public Signal<?> getSource() {
        return source;
    }

    @Override
    public Set<Target> getTargets() {
        return targets;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(JsonFields.SOURCE, source.toJson(schemaVersion, thePredicate), predicate);
        jsonObjectBuilder.set(JsonFields.TARGETS, targets.stream()
                .map(t -> t.toJson(schemaVersion, thePredicate))
                .collect(JsonCollectors.valuesToArray()), predicate);

        return jsonObjectBuilder.build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UnmappedOutboundSignal)) {
            return false;
        }
        final UnmappedOutboundSignal that = (UnmappedOutboundSignal) o;
        return Objects.equals(source, that.source) &&
                Objects.equals(targets, that.targets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, targets);
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "source=" + source +
                ", targets=" + targets +
                "]";
    }
}
