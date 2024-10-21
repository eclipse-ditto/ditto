package org.eclipse.ditto.policies.model.signals.commands.query;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourcePermissionFactory;
import org.eclipse.ditto.policies.model.ResourcePermissions;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;

@Immutable
@JsonParsableCommand(typePrefix = PolicyCommand.TYPE_PREFIX, name = PolicyCheckPermissionsCommand.NAME)
public final class PolicyCheckPermissionsCommand extends AbstractCommand<PolicyCheckPermissionsCommand>
        implements PolicyCommand<PolicyCheckPermissionsCommand> {

    public static final String NAME = "policyCheckPermissionCommand";

    public static final String PERMISSIONS_MAP = "permissionsMap";

    public static final String TYPE = TYPE_PREFIX + NAME;

    private final PolicyId policyId;
    private final Map<String, ResourcePermissions> permissionsMap;

    private PolicyCheckPermissionsCommand(final PolicyId policyId,
            final Map<String, ResourcePermissions> permissionsMap,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policy ID");
        this.permissionsMap = checkNotNull(permissionsMap, "permissions map");
    }

    public static PolicyCheckPermissionsCommand of(final PolicyId policyId,
            final Map<String, ResourcePermissions> permissionsMap,
            final DittoHeaders dittoHeaders) {
        return new PolicyCheckPermissionsCommand(policyId, permissionsMap, dittoHeaders);
    }

    public static PolicyCheckPermissionsCommand fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    public static PolicyCheckPermissionsCommand fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<PolicyCheckPermissionsCommand>(TYPE, jsonObject).deserialize(() -> {
            final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyCommand.JsonFields.JSON_POLICY_ID);
            final PolicyId policyId = PolicyId.of(extractedPolicyId);
            final JsonObject permissionsJsonObject =
                    jsonObject.getValueOrThrow(JsonFieldDefinition.ofJsonObject(PERMISSIONS_MAP));
            final Map<String, ResourcePermissions> permissionsMap = permissionsJsonObject.getKeys()
                    .stream()
                    .collect(Collectors.toMap(
                            key -> key.toString(),
                            key -> ResourcePermissionFactory.fromJson(permissionsJsonObject.getValue(key).get()
                                    .asObject())
                    ));

            return of(policyId, permissionsMap, dittoHeaders);
        });
    }

    public PolicyId getEntityId() {
        return policyId;
    }

    public Map<String, ResourcePermissions> getPermissionsMap() {
        return permissionsMap;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);

        final JsonObjectBuilder permissionsJson = JsonFactory.newObjectBuilder();
        permissionsMap.forEach((key, resourcePermissions) -> {
            permissionsJson.set(key, resourcePermissions.toJson());
        });

        jsonObjectBuilder.set(PolicyCommand.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        final JsonFieldDefinition<JsonObject> permissionsField =
                JsonFactory.newJsonObjectFieldDefinition(PERMISSIONS_MAP, FieldType.REGULAR, schemaVersion);

        jsonObjectBuilder.set(permissionsField, permissionsJson.build(), predicate);
    }


    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public PolicyCheckPermissionsCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, permissionsMap, dittoHeaders);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "OverlyComplexMethod"})
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final PolicyCheckPermissionsCommand that = (PolicyCheckPermissionsCommand) obj;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId) &&
                Objects.equals(permissionsMap, that.permissionsMap) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PolicyCheckPermissionsCommand;
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, permissionsMap);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", permissionsMap=" +
                permissionsMap + "]";
    }
}
