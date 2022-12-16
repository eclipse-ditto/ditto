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
package org.eclipse.ditto.policies.model.signals.commands.modify;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandSizeValidator;
import org.eclipse.ditto.policies.model.signals.commands.PolicyImportsValidator;

/**
 * This command modifies a {@link org.eclipse.ditto.policies.model.Policy}.
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicyCommand.TYPE_PREFIX, name = ModifyPolicy.NAME)
public final class ModifyPolicy extends AbstractCommand<ModifyPolicy> implements PolicyModifyCommand<ModifyPolicy> {

    /**
     * Name of this command.
     */
    public static final String NAME = "modifyPolicy";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_POLICY =
            JsonFactory.newJsonObjectFieldDefinition("policy", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final Policy policy;

    private ModifyPolicy(final PolicyId policyId, final Policy policy, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.policyId = policyId;
        this.policy = policy;

        final JsonObject policyJsonObject = policy.toJson();

        PolicyImportsValidator.validatePolicyImports(policyId, policy.getPolicyImports());

        PolicyCommandSizeValidator.getInstance().ensureValidSize(
                policyJsonObject::getUpperBoundForStringSize,
                () -> policyJsonObject.toString().length(),
                () -> dittoHeaders);
    }

    /**
     * Creates a command for modifying a {@code Policy}.
     *
     * @param policyId the Policy ID of the Policy to modify.
     * @param policy the Policy to modify.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicy of(final PolicyId policyId, final Policy policy, final DittoHeaders dittoHeaders) {
        Objects.requireNonNull(policyId, "The Policy identifier must not be null!");
        Objects.requireNonNull(policy, "The Policy must not be null!");
        return new ModifyPolicy(policyId, policy, dittoHeaders);
    }

    /**
     * Creates a command for modifying a {@code Policy} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyPolicy fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a command for modifying a {@code Policy} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain a field for {@link
     * PolicyModifyCommand.JsonFields#JSON_POLICY_ID} or {@link #JSON_POLICY}.
     */
    public static ModifyPolicy fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ModifyPolicy>(TYPE, jsonObject).deserialize(() -> {
            final JsonObject policyJsonObject = jsonObject.getValueOrThrow(JSON_POLICY);
            final Policy policy = PoliciesModelFactory.newPolicy(policyJsonObject);

            final Optional<String> optionalPolicyId =
                    jsonObject.getValue(PolicyCommand.JsonFields.JSON_POLICY_ID);
            final PolicyId policyId = optionalPolicyId.map(PolicyId::of)
                    .orElseGet(() -> policy.getEntityId().orElseThrow(() ->
                            new JsonMissingFieldException(PolicyCommand.JsonFields.JSON_POLICY_ID)
                    ));

            return of(policyId, policy, dittoHeaders);
        });
    }

    /**
     * Returns the {@code Policy} to modify.
     *
     * @return the Policy to modify.
     */
    public Policy getPolicy() {
        return policy;
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(policy.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommand.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_POLICY, policy.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyPolicy setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, policy, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ModifyPolicy);
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (null == obj || getClass() != obj.getClass()) {
            return false;
        }
        final ModifyPolicy that = (ModifyPolicy) obj;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId)
                && Objects.equals(policy, that.policy) && super.equals(obj);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, policy);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", policy=" + policy +
                "]";
    }

}
