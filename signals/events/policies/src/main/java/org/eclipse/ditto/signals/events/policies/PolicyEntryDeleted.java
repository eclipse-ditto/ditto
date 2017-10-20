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
package org.eclipse.ditto.signals.events.policies;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
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
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after a {@link org.eclipse.ditto.model.policies.PolicyEntry} was deleted.
 */
@Immutable
public final class PolicyEntryDeleted extends AbstractPolicyEvent<PolicyEntryDeleted>
        implements PolicyEvent<PolicyEntryDeleted> {

    /**
     * Name of this event.
     */
    public static final String NAME = "policyEntryDeleted";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final Label label;

    private PolicyEntryDeleted(final String policyId,
            final Label label,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, checkNotNull(policyId, "Policy identifier"), revision, timestamp, dittoHeaders);
        this.label = checkNotNull(label, "Label");
    }

    /**
     * Constructs a new {@code PolicyEntryDeleted} object.
     *
     * @param policyId the identifier of the Policy to which the deleted entry belongs
     * @param label the label of the deleted {@link org.eclipse.ditto.model.policies.PolicyEntry}
     * @param revision the revision of the Policy.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created PolicyEntryDeleted.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static PolicyEntryDeleted of(final String policyId,
            final Label label,
            final long revision,
            final DittoHeaders dittoHeaders) {

        return of(policyId, label, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code PolicyEntryDeleted} object.
     *
     * @param policyId the identifier of the Policy to which the deleted entry belongs
     * @param label the label of the deleted {@link org.eclipse.ditto.model.policies.PolicyEntry}
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created PolicyEntryDeleted.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static PolicyEntryDeleted of(final String policyId,
            final Label label,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new PolicyEntryDeleted(policyId, label, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyEntryDeleted} from a JSON string.
     *
     * @param jsonString the JSON string from which a new PolicyEntryDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyEntryDeleted} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected 'PolicyEntryDeleted'
     * format.
     */
    public static PolicyEntryDeleted fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyEntryDeleted} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new PolicyEntryDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyDeleted} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected 'PolicyEntryDeleted'
     * format.
     */
    public static PolicyEntryDeleted fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<PolicyEntryDeleted>(TYPE, jsonObject)
                .deserialize((revision, timestamp) -> {
                    final String policyId = jsonObject.getValueOrThrow(JsonFields.POLICY_ID);
                    final Label extractedLabel = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));

                    return of(policyId, extractedLabel, revision, timestamp, dittoHeaders);
                });
    }

    /**
     * Returns the {@link Label} of the deleted {@link org.eclipse.ditto.model.policies.PolicyEntry}.
     *
     * @return the {@link Label} of the deleted {@link org.eclipse.ditto.model.policies.PolicyEntry}.
     */
    public Label getLabel() {
        return label;
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + label;
        return JsonPointer.of(path);
    }

    @Override
    public PolicyEntryDeleted setRevision(final long revision) {
        return of(getPolicyId(), label, revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public PolicyEntryDeleted setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyId(), label, getRevision(), getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(label);
        return result;
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (null == o || getClass() != o.getClass()) {
            return false;
        }
        final PolicyEntryDeleted that = (PolicyEntryDeleted) o;
        return that.canEqual(this) && Objects.equals(label, that.label) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PolicyEntryDeleted;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", label=" + label + "]";
    }

}
