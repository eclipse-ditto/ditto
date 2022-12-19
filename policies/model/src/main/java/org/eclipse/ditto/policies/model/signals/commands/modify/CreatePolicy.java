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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

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
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyIdInvalidException;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandSizeValidator;
import org.eclipse.ditto.policies.model.signals.commands.PolicyImportsValidator;

/**
 * This command creates a new Policy. It contains the full {@link org.eclipse.ditto.policies.model.Policy} including the Policy ID which should be used
 * for creation. If the Policy ID is already in the system, a response with a status code {@code 409} (Conflict) will be
 * generated.
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicyCommand.TYPE_PREFIX, name = CreatePolicy.NAME)
public final class CreatePolicy extends AbstractCommand<CreatePolicy> implements PolicyModifyCommand<CreatePolicy> {

    /**
     * Name of this command.
     */
    public static final String NAME = "createPolicy";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_POLICY =
            JsonFactory.newJsonObjectFieldDefinition("policy", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final Policy policy;

    private CreatePolicy(final Policy policy, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.policy = checkNotNull(policy, "policy");

        if (!policy.getEntityId().isPresent()) {
            throw PolicyIdInvalidException.newBuilder("")
                    .message("Policy ID must be present in 'CreatePolicy' payload")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }

        final JsonObject policyJsonObject = policy.toJson();

        PolicyImportsValidator.validatePolicyImports(policy.getEntityId().get(), policy.getPolicyImports());

        PolicyCommandSizeValidator.getInstance().ensureValidSize(
                policyJsonObject::getUpperBoundForStringSize,
                () -> policyJsonObject.toString().length(),
                () -> dittoHeaders);
    }

    /**
     * Returns a Command for creating a new Policy which is passed as argument.
     *
     * @param policy the Policy to create.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.policies.model.PolicyIdInvalidException if the {@link org.eclipse.ditto.policies.model.Policy}'s ID is not valid.
     */
    public static CreatePolicy of(final Policy policy, final DittoHeaders dittoHeaders) {
        return new CreatePolicy(policy, dittoHeaders);
    }

    /**
     * Creates a command for creating a {@code Policy} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static CreatePolicy fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a command for creating a {@code Policy} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static CreatePolicy fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<CreatePolicy>(TYPE, jsonObject).deserialize(() -> {
            final Policy policy = PoliciesModelFactory.newPolicy(jsonObject.getValueOrThrow(JSON_POLICY));

            return of(policy, dittoHeaders);
        });
    }

    /**
     * Returns the {@code Policy} to create.
     *
     * @return the Policy to create.
     */
    public Policy getPolicy() {
        return policy;
    }

    @Override
    public PolicyId getEntityId() {
        return policy.getEntityId().orElseThrow(() -> new NullPointerException("The Policy has no ID!"));
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
        jsonObjectBuilder.set(JSON_POLICY, policy.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public Category getCategory() {
        return Category.CREATE;
    }

    @Override
    public CreatePolicy setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policy, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof CreatePolicy;
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
        final CreatePolicy that = (CreatePolicy) obj;
        return that.canEqual(this) && Objects.equals(policy, that.policy) && super.equals(obj);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policy);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policy=" + policy + "]";
    }

}
