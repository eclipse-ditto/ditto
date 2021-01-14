/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.policies.actions;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;

/**
 * This command activates a token subject in all authorized policy entries.
 *
 * @since 2.0.0
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicyCommand.TYPE_PREFIX, name = ActivatePolicyTokenIntegration.NAME)
public final class ActivatePolicyTokenIntegration extends AbstractCommand<ActivatePolicyTokenIntegration>
        implements PolicyActionCommand<ActivatePolicyTokenIntegration> {

    /**
     * NAME of this command.
     */
    public static final String NAME = "activatePolicyTokenIntegration";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_SUBJECT_ID =
            JsonFactory.newStringFieldDefinition("subjectId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_EXPIRY =
            JsonFactory.newStringFieldDefinition("expiry", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_LABELS =
            JsonFactory.newJsonArrayFieldDefinition("labels", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final SubjectId subjectId;
    private final Instant expiry;
    private final List<Label> labels;

    private ActivatePolicyTokenIntegration(final PolicyId policyId, final SubjectId subjectId,
            final Instant expiry, final List<Label> labels,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.subjectId = checkNotNull(subjectId, "subjectId");
        this.expiry = checkNotNull(expiry, "expiry");
        // Null check and copying in the factory method in order to share known unmodifiable fields between instances.
        this.labels = labels;
    }

    /**
     * Creates a command for activating a token subject in all authorized policy entries.
     *
     * @param policyId the identifier of the Policy.
     * @param subjectId subject ID to activate.
     * @param expiry when the subject expires.
     * @param labels labels of policy entries in which to inject the subject.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ActivatePolicyTokenIntegration of(final PolicyId policyId, final SubjectId subjectId,
            final Instant expiry, final List<Label> labels, final DittoHeaders dittoHeaders) {

        return new ActivatePolicyTokenIntegration(policyId, subjectId, expiry,
                Collections.unmodifiableList(new ArrayList<>(checkNotNull(labels, "labels"))),
                dittoHeaders);
    }

    /**
     * Creates a command for activating a token subject in all authorized policy entries from JSON.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ActivatePolicyTokenIntegration fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ActivatePolicyTokenIntegration>(TYPE, jsonObject).deserialize(() -> {
            final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyCommand.JsonFields.JSON_POLICY_ID);
            final PolicyId policyId = PolicyId.of(extractedPolicyId);
            final SubjectId subjectId =
                    PoliciesModelFactory.newSubjectId(jsonObject.getValueOrThrow(JSON_SUBJECT_ID));
            final Instant expiry =
                    ActivateTokenIntegration.parseExpiry(jsonObject.getValueOrThrow(JSON_EXPIRY));
            final List<Label> labels = Collections.unmodifiableList(
                    jsonObject.getValueOrThrow(JSON_LABELS)
                            .stream()
                            .map(JsonValue::asString)
                            .map(Label::of)
                            .collect(Collectors.toList())
            );
            return new ActivatePolicyTokenIntegration(policyId, subjectId, expiry, labels, dittoHeaders);
        });
    }

    @Override
    public SubjectId getSubjectId() {
        return subjectId;
    }

    @Override
    public ActivatePolicyTokenIntegration setLabel(final Label label) {
        return this;
    }

    @Override
    public boolean isApplicable(final PolicyEntry policyEntry) {
        return false;
    }

    /**
     * Returns the expiry of the subject ID.
     *
     * @return the expiry.
     */
    public Instant getExpiry() {
        return expiry;
    }

    /**
     * Returns the labels of policy entries in which the subject is to be injected.
     *
     * @return the policy entry labels.
     */
    public List<Label> getLabels() {
        return labels;
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public JsonPointer getResourcePath() {
        // actions/activateTokenIntegration
        return RESOURCE_PATH_ACTIONS.addLeaf(JsonKey.of(ActivateTokenIntegration.NAME));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommand.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_SUBJECT_ID, subjectId.toString(), predicate);
        jsonObjectBuilder.set(JSON_EXPIRY, expiry.toString(), predicate);
        jsonObjectBuilder.set(JSON_LABELS,
                labels.stream().map(JsonValue::of).collect(JsonCollectors.valuesToArray()));
    }

    @Override
    public ActivatePolicyTokenIntegration setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ActivatePolicyTokenIntegration(policyId, subjectId, expiry, labels, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ActivatePolicyTokenIntegration;
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (null == obj || getClass() != obj.getClass()) {
            return false;
        }
        final ActivatePolicyTokenIntegration that = (ActivatePolicyTokenIntegration) obj;
        return Objects.equals(policyId, that.policyId) &&
                Objects.equals(subjectId, that.subjectId) &&
                Objects.equals(expiry, that.expiry) &&
                Objects.equals(labels, that.labels) &&
                super.equals(obj);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, subjectId, expiry, labels);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", policyId=" + policyId +
                ", subjectId=" + subjectId +
                ", expiry=" + expiry +
                ", labels=" + labels +
                "]";
    }

}
