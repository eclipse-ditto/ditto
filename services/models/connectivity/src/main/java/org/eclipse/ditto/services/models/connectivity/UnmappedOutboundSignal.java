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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
import org.eclipse.ditto.services.utils.cluster.MappingStrategies;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an outbound signal before it was mapped to an {@link ExternalMessage}.
 */
@Immutable
final class UnmappedOutboundSignal implements OutboundSignal {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnmappedOutboundSignal.class);

    private final Signal<?> source;
    private final List<Target> targets;

    UnmappedOutboundSignal(final Signal<?> source, final List<Target> targets) {
        this.source = source;
        this.targets = Collections.unmodifiableList(new ArrayList<>(targets));
    }

    /**
     * Creates a new {@code OutboundSignal} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the OutboundSignal to be created.
     * @param mappingStrategies the {@link org.eclipse.ditto.services.utils.cluster.MappingStrategies} to use in order
     * to parse the in the JSON included {@code source} Signal.
     * @return a new OutboundSignal which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static OutboundSignal fromJson(final JsonObject jsonObject, final MappingStrategies mappingStrategies) {
        final JsonObject readSourceObj = jsonObject.getValueOrThrow(JsonFields.SOURCE);
        final String commandType = readSourceObj.getValueOrThrow(Command.JsonFields.TYPE);
        final Optional<MappingStrategy> mappingStrategy = mappingStrategies.getMappingStrategyFor(commandType);

        if (!mappingStrategy.isPresent()) {
            final String msgPattern = "There is no mapping strategy available for the signal of type <{0}>!";
            final String message = MessageFormat.format(msgPattern, commandType);
            LOGGER.error(message);
            throw new IllegalStateException(message);
        }

        final DittoHeaders dittoHeaders = jsonObject.getValue(JsonFields.JSON_DITTO_HEADERS)
                .map(DittoHeaders::newBuilder)
                .orElseGet(DittoHeaders::newBuilder)
                .build();
        final Jsonifiable signalJsonifiable = mappingStrategy.get().map(readSourceObj, dittoHeaders);

        final JsonArray readTargetsArr = jsonObject.getValueOrThrow(JsonFields.TARGETS);
        final List<Target> targets = readTargetsArr.stream()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(ConnectivityModelFactory::targetFromJson)
                .collect(Collectors.toList());

        return new UnmappedOutboundSignal((Signal<?>) signalJsonifiable, targets);
    }

    @Override
    public Signal<?> getSource() {
        return source;
    }

    @Override
    public List<Target> getTargets() {
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
        jsonObjectBuilder.set(JsonFields.JSON_DITTO_HEADERS, source.getDittoHeaders().toJson());

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
