/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.policies.api.commands.sudo;

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
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
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

/**
 * Command representing a request to check permissions for resources within a policy.
 * <p>
 * This class encapsulates the permission check for a specific {@link PolicyId} and a map of resources and
 * their associated permissions. It is used to verify whether a set of permissions are allowed for a given
 * policy and resource combination.
 * <p>
 * The command is immutable and provides methods for serialization to and from JSON.
 *
 * @since 3.7.0
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicySudoCommand.TYPE_PREFIX, name = PolicyCheckPermissions.NAME)
public final class PolicyCheckPermissions extends AbstractCommand<PolicyCheckPermissions>
        implements PolicySudoCommand<PolicyCheckPermissions>, SignalWithEntityId<PolicyCheckPermissions> {

    /**
     * The name of this command type.
     */
    public static final String NAME = "policyCheckPermissionCommand";

    /**
     * The key for the permissions map field in the JSON object.
     */
    public static final String PERMISSIONS_MAP = "permissionsMap";

    /**
     * The type identifier for this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final PolicyId policyId;
    private final Map<String, ResourcePermissions> permissionsMap;

    /**
     * Private constructor for creating an instance of {@code PolicyCheckPermissions}.
     *
     * @param policyId the ID of the policy being checked.
     * @param permissionsMap the map of resources and permissions to check.
     * @param dittoHeaders the headers of the command.
     */
    private PolicyCheckPermissions(final PolicyId policyId,
            final Map<String, ResourcePermissions> permissionsMap,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policy ID");
        this.permissionsMap = checkNotNull(permissionsMap, "permissions map");
    }

    /**
     * Factory method to create a {@code PolicyCheckPermissions} command.
     *
     * @param policyId the ID of the policy being checked.
     * @param permissionsMap the map of resources and permissions to check.
     * @param dittoHeaders the headers of the command.
     * @return a new {@link PolicyCheckPermissions} command.
     */
    public static PolicyCheckPermissions of(final PolicyId policyId,
            final Map<String, ResourcePermissions> permissionsMap,
            final DittoHeaders dittoHeaders) {
        return new PolicyCheckPermissions(policyId, permissionsMap, dittoHeaders);
    }

    /**
     * Creates a {@code PolicyCheckPermissions} command from a JSON string.
     *
     * @param jsonString the JSON string to parse the command from.
     * @param dittoHeaders the headers of the command.
     * @return a new {@link PolicyCheckPermissions} command.
     */
    public static PolicyCheckPermissions fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a {@code PolicyCheckPermissions} command from a {@link JsonObject}.
     *
     * @param jsonObject the JSON object to parse the command from.
     * @param dittoHeaders the headers of the command.
     * @return a new {@link PolicyCheckPermissions} command.
     */
    public static PolicyCheckPermissions fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<PolicyCheckPermissions>(TYPE, jsonObject).deserialize(() -> {
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

    /**
     * Gets the {@link PolicyId} (entity ID) associated with this command.
     *
     * @return the policy ID.
     */
    public PolicyId getEntityId() {
        return policyId;
    }

    /**
     * Gets the map of resource permissions being checked.
     *
     * @return the map of permissions.
     */
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
    public PolicyCheckPermissions setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, permissionsMap, dittoHeaders);
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final PolicyCheckPermissions that = (PolicyCheckPermissions) obj;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId) &&
                Objects.equals(permissionsMap, that.permissionsMap) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PolicyCheckPermissions;
    }

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
