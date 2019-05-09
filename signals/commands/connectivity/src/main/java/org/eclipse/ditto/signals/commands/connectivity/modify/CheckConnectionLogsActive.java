package org.eclipse.ditto.signals.commands.connectivity.modify;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;

/**
 * Command that will enable logging in a {@link org.eclipse.ditto.model.connectivity.Connection}.
 */
@Immutable
@JsonParsableCommand(typePrefix = CheckConnectionLogsActive.TYPE_PREFIX, name = CheckConnectionLogsActive.NAME)
public final class CheckConnectionLogsActive extends AbstractCommand<CheckConnectionLogsActive>
        implements ConnectivityModifyCommand<CheckConnectionLogsActive> {


    /**
     * Name of this command.
     */
    public static final String NAME = "CheckConnectionLogsActive";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final String connectionId;
    private final Instant timestamp;

    private CheckConnectionLogsActive(final String connectionId, final DittoHeaders dittoHeaders,
            final Instant timestamp) {
        super(TYPE, dittoHeaders);
        this.connectionId = connectionId;
        this.timestamp = timestamp;
    }

    /**
     * Creates a new instance of {@code CheckConnectionLogsActive}.
     *
     * @param connectionId the connection for which logging should be enabled.
     * @param dittoHeaders the headers of the request.
     * @return a new instance of the command.
     * @throws java.lang.NullPointerException if any argument is {@code null}.
     */
    public static CheckConnectionLogsActive of(final String connectionId, final DittoHeaders dittoHeaders,
            final Instant timestamp) {
        checkNotNull(connectionId, "Connection ID");
        checkNotNull(timestamp, "timestamp");
        return new CheckConnectionLogsActive(connectionId, dittoHeaders, timestamp);
    }

    /**
     * Creates a new {@code CheckConnectionLogsActive} from a JSON string.
     *
     * @param jsonString the JSON containing the command.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static CheckConnectionLogsActive fromJson(final String jsonString, final DittoHeaders dittoHeaders,
            final Instant timestamp) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders, timestamp);
    }

    /**
     * Creates a new {@code CheckConnectionLogsActive} from a JSON object.
     *
     * @param jsonObject the JSON containing the command.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static CheckConnectionLogsActive fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders,
            Instant timestamp) {
        return new CommandJsonDeserializer<CheckConnectionLogsActive>(TYPE, jsonObject).deserialize(() -> {
            final String readConnectionId =
                    jsonObject.getValueOrThrow(ConnectivityCommand.JsonFields.JSON_CONNECTION_ID);

            return of(readConnectionId, dittoHeaders, timestamp);
        });
    }

    public static CheckConnectionLogsActive fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final Instant now = Instant.now();
        return CheckConnectionLogsActive.fromJson(jsonObject, dittoHeaders, now);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ConnectivityCommand.JsonFields.JSON_CONNECTION_ID, connectionId, predicate);
    }

    @Override
    public String getConnectionId() {
        return connectionId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public CheckConnectionLogsActive setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connectionId, dittoHeaders, timestamp);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof EnableConnectionLogs);
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
        final CheckConnectionLogsActive that = (CheckConnectionLogsActive) o;
        return Objects.equals(connectionId, that.connectionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connectionId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", connectionId=" + connectionId +
                "]";
    }

}
