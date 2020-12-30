/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.policies.modify;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.model.base.json.FieldType.REGULAR;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_2;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;

/**
 * This command activates a token subject in a policy entry.
 *
 * @since 2.0.0
 */
@Immutable
@JsonParsableCommand(typePrefix = ActivateSubject.TYPE_PREFIX, name = ActivateSubject.NAME)
public final class ActivateSubject extends AbstractCommand<ActivateSubject>
        implements PolicyActionCommand<ActivateSubject> {

    /**
     * NAME of this command.
     */
    public static final String NAME = "activateSubject";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final PolicyId policyId;
    private final Label label;
    private final SubjectId subjectId;
    private final Instant expiry;

    private ActivateSubject(final PolicyId policyId, final Label label, final SubjectId subjectId,
            final Instant expiry, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.label = checkNotNull(label, "label");
        this.subjectId = checkNotNull(subjectId, "subjectId");
        this.expiry = checkNotNull(expiry, "expiry");
    }

    /**
     * Creates a command for activating a token subject.
     *
     * @param policyId the identifier of the Policy.
     * @param label label of the policy entry where the subject should be activated.
     * @param subjectId subject ID to activate.
     * @param expiry when the subject expires.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ActivateSubject of(final PolicyId policyId, final Label label, final SubjectId subjectId,
            final Instant expiry, final DittoHeaders dittoHeaders) {

        return new ActivateSubject(policyId, label, subjectId, expiry, dittoHeaders);
    }

    /**
     * Creates a command for activating a token subject from JSON.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ActivateSubject fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ActivateSubject>(TYPE, jsonObject).deserialize(() -> {
            final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyModifyCommand.JsonFields.JSON_POLICY_ID);
            final PolicyId policyId = PolicyId.of(extractedPolicyId);
            final Label label = Label.of(jsonObject.getValueOrThrow(JsonFields.LABEL));
            final SubjectId subjectId =
                    PoliciesModelFactory.newSubjectId(jsonObject.getValueOrThrow(JsonFields.SUBJECT_ID));
            final Instant expiry = parseExpiry(jsonObject.getValueOrThrow(JsonFields.EXPIRY));
            return new ActivateSubject(policyId, label, subjectId, expiry, dittoHeaders);
        });
    }

    /**
     * Returns the label of the target policy entry.
     *
     * @return the label.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the subject ID introduced by the activation.
     *
     * @return the subject ID.
     */
    public SubjectId getSubjectId() {
        return subjectId;
    }

    /**
     * Returns the expiry of the subject ID.
     *
     * @return the expiry.
     */
    public Instant getExpiry() {
        return expiry;
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return toResourcePath(label, subjectId);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommand.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JsonFields.LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.SUBJECT_ID, subjectId.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.EXPIRY, expiry.toString(), predicate);
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ActivateSubject setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ActivateSubject(policyId, label, subjectId, expiry, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ActivateSubject;
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (null == obj || getClass() != obj.getClass()) {
            return false;
        }
        final ActivateSubject that = (ActivateSubject) obj;
        return Objects.equals(policyId, that.policyId) &&
                Objects.equals(label, that.label) &&
                Objects.equals(subjectId, that.subjectId) &&
                Objects.equals(expiry, that.expiry) &&
                super.equals(obj);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, subjectId, expiry);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", policyId=" + policyId +
                ", label=" + label +
                ", subjectId=" + subjectId +
                ", expiry=" + expiry +
                "]";
    }

    static JsonPointer toResourcePath(final Label label, final SubjectId subjectId) {
        return Policy.JsonFields.ENTRIES.getPointer()
                .addLeaf(JsonKey.of(label))
                .append(PolicyEntry.JsonFields.SUBJECTS.getPointer())
                .addLeaf(JsonKey.of(subjectId));
    }

    static Instant parseExpiry(final String expiryString) {
        try {
            return Instant.parse(expiryString);
        } catch (final DateTimeParseException e) {
            throw JsonParseException.newBuilder()
                    .message(String.format("Expiry timestamp '%s' is not valid. " +
                            "It must be provided as ISO-8601 formatted char sequence.", expiryString))
                    .build();
        }
    }

    static final class JsonFields {

        static final JsonFieldDefinition<String> LABEL =
                JsonFactory.newStringFieldDefinition("label", REGULAR, V_2);

        static final JsonFieldDefinition<String> SUBJECT_ID =
                JsonFactory.newStringFieldDefinition("subjectId", REGULAR, V_2);

        static final JsonFieldDefinition<String> EXPIRY =
                JsonFactory.newStringFieldDefinition("expiry", REGULAR, V_2);
    }

}
