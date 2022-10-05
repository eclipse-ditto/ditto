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
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandSizeValidator;

/**
 * This command modifies {@link org.eclipse.ditto.policies.model.PolicyEntry}s.
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicyCommand.TYPE_PREFIX, name = ModifyPolicyEntries.NAME)
public final class ModifyPolicyEntries extends AbstractCommand<ModifyPolicyEntries>
        implements PolicyModifyCommand<ModifyPolicyEntries> {

    /**
     * Name of this command.
     */
    public static final String NAME = "modifyPolicyEntries";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_POLICY_ENTRIES =
            JsonFactory.newJsonObjectFieldDefinition("policyEntries", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final Iterable<PolicyEntry> policyEntries;

    private ModifyPolicyEntries(final PolicyId policyId, final Iterable<PolicyEntry> policyEntries,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        this.policyId = policyId;
        this.policyEntries = policyEntries;

        final JsonArray policyEntriesJsonArray = StreamSupport.stream(policyEntries.spliterator(), false)
                .map(PolicyEntry::toJson)
                .collect(JsonCollectors.valuesToArray());

        PolicyCommandSizeValidator.getInstance().ensureValidSize(
                policyEntriesJsonArray::getUpperBoundForStringSize,
                () -> policyEntriesJsonArray.toString().length(),
                () -> dittoHeaders);
    }

    /**
     * Creates a command for modifying {@code PolicyEntry}s.
     *
     * @param policyId the identifier of the Policy.
     * @param policyEntries the new PolicyEntries.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyEntries of(final PolicyId policyId, final Iterable<PolicyEntry> policyEntries,
            final DittoHeaders dittoHeaders) {

        checkNotNull(policyId, "Policy identifier");
        checkNotNull(policyEntries, "PolicyEntries");
        return new ModifyPolicyEntries(policyId, policyEntries, dittoHeaders);
    }

    /**
     * Creates a command for modifying {@code PolicyEntry}s from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyPolicyEntries fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a command for modifying a {@code PolicyEntry} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyPolicyEntries fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ModifyPolicyEntries>(TYPE, jsonObject).deserialize(() -> {
            final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyCommand.JsonFields.JSON_POLICY_ID);
            final PolicyId policyId = PolicyId.of(extractedPolicyId);
            final JsonObject policyEntriesJsonObject = jsonObject.getValueOrThrow(JSON_POLICY_ENTRIES);
            final Iterable<PolicyEntry> policyEntries = PoliciesModelFactory.newPolicyEntries(policyEntriesJsonObject);

            return of(policyId, policyEntries, dittoHeaders);
        });
    }

    /**
     * Returns the {@code PolicyEntry}s to modify.
     *
     * @return the PolicyEntrys to modify.
     */
    public Iterable<PolicyEntry> getPolicyEntries() {
        return policyEntries;
    }

    /**
     * Returns the identifier of the {@code Policy} whose {@code PolicyEntry}s to modify.
     *
     * @return the identifier of the Policy whose PolicyEntrys to modify.
     */
    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        final JsonObject jsonObject = StreamSupport.stream(policyEntries.spliterator(), false)
                .map(entry -> JsonFactory.newObjectBuilder()
                        .set(entry.getLabel().getJsonFieldDefinition(),
                                entry.toJson(schemaVersion, FieldType.regularOrSpecial()))
                        .build())
                .collect(JsonCollectors.objectsToObject());
        return Optional.ofNullable(jsonObject);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/entries");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommand.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_POLICY_ENTRIES, StreamSupport.stream(policyEntries.spliterator(), false)
                        .map(entry -> JsonFactory.newObjectBuilder()
                                .set(entry.getLabel().getJsonFieldDefinition(),
                                        entry.toJson(schemaVersion, thePredicate), predicate)
                                .build())
                        .collect(JsonCollectors.objectsToObject()),
                predicate);
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyPolicyEntries setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, policyEntries, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyPolicyEntries;
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
        final ModifyPolicyEntries that = (ModifyPolicyEntries) obj;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId)
                && Objects.equals(policyEntries, that.policyEntries) && super.equals(obj);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, policyEntries);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", policyEntries="
                + policyEntries + "]";
    }

}
