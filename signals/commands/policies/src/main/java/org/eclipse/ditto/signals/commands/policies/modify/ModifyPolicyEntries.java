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
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonCollectors;
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
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * This command modifies {@link PolicyEntry}s.
 */
@Immutable
public final class ModifyPolicyEntries extends AbstractCommand<ModifyPolicyEntries> implements
        PolicyModifyCommand<ModifyPolicyEntries> {

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

    private final String policyId;
    private final Iterable<PolicyEntry> policyEntries;

    private ModifyPolicyEntries(final String policyId, final Iterable<PolicyEntry> policyEntries,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        this.policyId = policyId;
        this.policyEntries = policyEntries;
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
    public static ModifyPolicyEntries of(final String policyId, final Iterable<PolicyEntry> policyEntries,
            final DittoHeaders dittoHeaders) {

        Objects.requireNonNull(policyId, "The Policy identifier must not be null!");
        Objects.requireNonNull(policyEntries, "The PolicyEntries must not be null!");
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
            final String policyId = jsonObject.getValueOrThrow(PolicyModifyCommand.JsonFields.JSON_POLICY_ID);
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
    public String getId() {
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
        jsonObjectBuilder.set(PolicyModifyCommand.JsonFields.JSON_POLICY_ID, policyId, predicate);
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
