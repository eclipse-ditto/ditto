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
package org.eclipse.ditto.signals.commands.policies.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * Command which retrieves the Policy entry of a {@code Policy} based on the passed in Policy ID and Label.
 */
@Immutable
public final class RetrievePolicyEntry extends AbstractCommand<RetrievePolicyEntry> implements
        PolicyQueryCommand<RetrievePolicyEntry> {

    /**
     * Name of the retrieve "Retrieve Policy Entry" command.
     */
    public static final String NAME = "retrievePolicyEntry";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final String policyId;
    private final Label label;

    private RetrievePolicyEntry(final Label label, final String policyId, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);

        this.policyId = checkNotNull(policyId, "Policy identifier");
        this.label = checkNotNull(label, "Label");
    }

    /**
     * Returns a command for retrieving a specific Policy entry with the given ID and Label.
     *
     * @param policyId the ID of a single Policy whose Policy entry will be retrieved by this command.
     * @param label the specified label for which to retrieve the Policy entry for.
     * @param dittoHeaders the optional command headers of the request.
     * @return a Command for retrieving one Policy entry with the {@code policyId} and {@code label} which is readable
     * from the passed authorization context.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrievePolicyEntry of(final String policyId, final Label label, final DittoHeaders dittoHeaders) {
        return new RetrievePolicyEntry(label, policyId, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrievePolicyEntry} from a JSON string.
     *
     * @param jsonString the JSON string of which a new RetrievePolicyEntry instance is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the {@code RetrievePolicyEntry} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrievePolicyEntry fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        final JsonObject jsonObject = JsonFactory.newObject(jsonString);

        return fromJson(jsonObject, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrievePolicyEntry} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new RetrievePolicyEntry instance is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the {@code RetrievePolicyEntry} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrievePolicyEntry fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<RetrievePolicyEntry>(TYPE, jsonObject).deserialize(() -> {
            final String policyId = jsonObject.getValueOrThrow(PolicyQueryCommand.JsonFields.JSON_POLICY_ID);
            final Label extractedLabel = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));

            return of(policyId, extractedLabel, dittoHeaders);
        });
    }

    /**
     * Returns the identifier of the {@code Label} of the {@code PolicyEntry} to retrieve.
     *
     * @return the identifier of the Label of the PolicyEntry to retrieve.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the identifier of the {@code Policy} to retrieve the {@code PolicyEntry} from.
     *
     * @return the identifier of the Policy to retrieve the PolicyEntry from.
     */
    @Override
    public String getId() {
        return policyId;
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + label;
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyQueryCommand.JsonFields.JSON_POLICY_ID, policyId, predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
    }

    @Override
    public RetrievePolicyEntry setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, label, dittoHeaders);
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
        final RetrievePolicyEntry that = (RetrievePolicyEntry) obj;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId) && Objects.equals(label, that.label)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrievePolicyEntry;
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", label=" + label +
                "]";
    }

}
