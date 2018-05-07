/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.commands.policies.modify;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyIdInvalidException;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * This command creates a new Policy. It contains the full {@link Policy} including the Policy ID which should be used
 * for creation. If the Policy ID is already in the system, a response with a status code {@code 409} (Conflict) will be
 * generated.
 */
@Immutable
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
        this.policy = policy;
    }

    /**
     * Returns a Command for creating a new Policy which is passed as argument.
     *
     * @param policy the Policy to create.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws PolicyIdInvalidException if the {@link Policy}'s ID is not valid.
     */
    public static CreatePolicy of(final Policy policy, final DittoHeaders dittoHeaders) {
        Objects.requireNonNull(policy, "The Policy must not be null!");
        if (!policy.getId().isPresent()) {
            throw PolicyIdInvalidException.newBuilder("")
                    .message("Policy ID must be present in 'CreatePolicy' payload")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
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
    public String getId() {
        return policy.getId().orElseThrow(() -> new NullPointerException("The Policy has no ID!"));
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
        return Category.MODIFY;
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
