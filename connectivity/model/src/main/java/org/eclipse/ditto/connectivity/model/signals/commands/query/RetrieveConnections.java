/*
 * Copyright Bosch.IO GmbH 2021
 *
 *  All rights reserved, also regarding any disposal, exploitation,
 *  reproduction, editing, distribution, as well as in the event of
 *  applications for industrial property rights.
 *
 *  This software is the confidential and proprietary information
 *  of Bosch.IO GmbH. You shall not disclose
 *  such Confidential Information and shall use it only in
 *  accordance with the terms of the license agreement you
 *  entered into with Bosch.IO GmbH.
 */
package org.eclipse.ditto.connectivity.model.signals.commands.query;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Command that retrieves several {@link org.eclipse.ditto.connectivity.model.Connection}s based on the passed in list
 * of Connection IDs.
 */
@Immutable
@JsonParsableCommand(typePrefix = ConnectivityCommand.TYPE_PREFIX, name = RetrieveConnections.NAME)
public final class RetrieveConnections extends AbstractCommand<RetrieveConnections>
        implements ConnectivityQueryCommand<RetrieveConnections> {

    /**
     * Name of the "Retrieve Connections" command.
     */
    public static final String NAME = "retrieveConnections";

    /**
     * Type of this command.
     */
    public static final String TYPE = ConnectivityCommand.TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonArray> JSON_CONNECTION_IDS =
            JsonFactory.newJsonArrayFieldDefinition("connectionIds", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    private final Set<ConnectionId> connectionIds;

    private RetrieveConnections(final Set<ConnectionId> connectionIds, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.connectionIds = Collections.unmodifiableSet(connectionIds);
    }

    /**
     * Returns a new instance of the retrieve connections command.
     *
     * @param connectionIds the IDs of the connections to be retrieved.
     * @param dittoHeaders provide additional information regarding connections retrieval like a correlation ID.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnections newInstance(final Collection<ConnectionId> connectionIds,
            final DittoHeaders dittoHeaders) {

        return new RetrieveConnections(new LinkedHashSet<>(checkNotNull(connectionIds, "connectionIds")), dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveConnections} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveConnections fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveConnections} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveConnections fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final Set<ConnectionId> extractedConnectionIds = jsonObject.getValueOrThrow(JSON_CONNECTION_IDS).stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(ConnectionId::of)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new RetrieveConnections(extractedConnectionIds, dittoHeaders);
    }

    public Set<ConnectionId> getConnectionIds() {
        return connectionIds;
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public RetrieveConnections setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new RetrieveConnections(connectionIds, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion jsonSchemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = jsonSchemaVersion.and(thePredicate);
        final JsonArray connectionIdsArray = connectionIds.stream()
                .map(String::valueOf)
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray());

        jsonObjectBuilder.set(JSON_CONNECTION_IDS, connectionIdsArray, predicate);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final RetrieveConnections that = (RetrieveConnections) o;
        return Objects.equals(connectionIds, that.connectionIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connectionIds);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", connectionIds=" + connectionIds +
                "]";
    }

}
