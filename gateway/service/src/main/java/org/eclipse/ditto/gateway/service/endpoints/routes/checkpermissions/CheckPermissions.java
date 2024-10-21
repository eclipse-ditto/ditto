package org.eclipse.ditto.gateway.service.endpoints.routes.checkpermissions;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.api.common.CommonCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;

import javax.annotation.concurrent.Immutable;

/**
 * Command to check multiple permissions for different resources in a single request.
 *
 * @since 3.7.0
 */
@Immutable
@JsonParsableCommand(typePrefix = CheckPermissions.TYPE_PREFIX, name = CheckPermissions.NAME)
public final class CheckPermissions extends CommonCommand<CheckPermissions> {

    static final String TYPE_PREFIX = CommonCommand.TYPE_PREFIX;

    /**
     * The name of the command.
     */
    static final String NAME = "checkPermissions";

    /**
     * The type of the command.
     */
    public static final String TYPE = TYPE_PREFIX + CheckPermissions.NAME;

    private final Map<String, ImmutablePermissionCheck> permissionChecks;

    /**
     * Constructs a new {@code CheckPermissionsCommand} object.
     *
     * @param dittoHeaders the headers of the command.
     * @param permissionChecks a map of permission checks to be performed.
     */
    private CheckPermissions(final DittoHeaders dittoHeaders,
            final Map<String, ImmutablePermissionCheck> permissionChecks) {
        super(TYPE, Category.QUERY, dittoHeaders);
        this.permissionChecks = Map.copyOf(permissionChecks);
    }

    /**
     * Creates a new {@code CheckPermissionsCommand} with the provided headers and permission checks.
     *
     * @param headers the headers of the command.
     * @param permissionChecks the permission checks to be included in the command.
     * @return a new {@code CheckPermissionsCommand}.
     */
    public static CheckPermissions of(final DittoHeaders headers,
            final Map<String, ImmutablePermissionCheck> permissionChecks) {
        return new CheckPermissions(headers, permissionChecks);
    }

    /**
     * Creates a {@code CheckPermissionsCommand} from a JSON object.
     *
     * @param jsonObject the JSON object containing the data.
     * @param dittoHeaders the headers of the command.
     * @return a new {@code CheckPermissionsCommand}.
     */
    public static CheckPermissions fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final Map<String, ImmutablePermissionCheck> permissionChecks = jsonObject.stream()
                .collect(Collectors.toMap(
                        entry -> String.valueOf(entry.getKey()),
                        entry -> ImmutablePermissionCheck.fromJson(entry.getValue().asObject())
                ));
        return new CheckPermissions(dittoHeaders, permissionChecks);
    }


    /**
     * Returns the permission checks contained in this command.
     *
     * @return a map of permission checks.
     */
    public Map<String, ImmutablePermissionCheck> getPermissionChecks() {
        return permissionChecks;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        JsonObjectBuilder permissionChecksBuilder = JsonFactory.newObjectBuilder();
        permissionChecks.forEach((key, permissionCheck) -> permissionChecksBuilder.set(key, permissionCheck.toJson()));
        jsonObjectBuilder.set("permissionChecks", permissionChecksBuilder.build(), predicate);
    }

    @Override
    public CheckPermissions setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new CheckPermissions(dittoHeaders, permissionChecks);
    }


    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final CheckPermissions that = (CheckPermissions) obj;
        return Objects.equals(permissionChecks, that.permissionChecks) && super.equals(that);
    }


    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), permissionChecks);
    }

    @Override
    public String toString() {
        return "CheckPermissionsCommand[" + super.toString() + ", permissionChecks=" + permissionChecks + "]";
    }
}

