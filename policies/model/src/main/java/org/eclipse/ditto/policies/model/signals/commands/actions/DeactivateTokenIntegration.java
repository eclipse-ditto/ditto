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
package org.eclipse.ditto.policies.model.signals.commands.actions;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
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
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyActionFailedException;

/**
 * This command deactivates one or several subjects derived from a provided token from an existing policy entry.
 *
 * @since 2.0.0
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicyCommand.TYPE_PREFIX, name = DeactivateTokenIntegration.NAME)
public final class DeactivateTokenIntegration extends AbstractCommand<DeactivateTokenIntegration>
        implements PolicyActionCommand<DeactivateTokenIntegration> {

    /**
     * NAME of this command.
     */
    public static final String NAME = "deactivateTokenIntegration";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_SUBJECT_IDS =
            JsonFactory.newJsonArrayFieldDefinition("subjectIds", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final Label label;
    private final Set<SubjectId> subjectIds;

    private DeactivateTokenIntegration(final PolicyId policyId, final Label label,
            final Collection<SubjectId> subjectIds, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.label = checkNotNull(label, "label");
        this.subjectIds = Collections.unmodifiableSet(new LinkedHashSet<>(checkNotNull(subjectIds, "subjectIds")));
    }

    /**
     * Creates a command for deactivating a token subject.
     *
     * @param policyId the identifier of the Policy.
     * @param label label of the policy entry where the subject should be deactivated.
     * @param subjectIds subject IDs to activate.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DeactivateTokenIntegration of(final PolicyId policyId, final Label label,
            final Collection<SubjectId> subjectIds, final DittoHeaders dittoHeaders) {

        return new DeactivateTokenIntegration(policyId, label, subjectIds, dittoHeaders);
    }

    /**
     * Creates a command for deactivating a token subject from JSON.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static DeactivateTokenIntegration fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<DeactivateTokenIntegration>(TYPE, jsonObject).deserialize(() -> {
            final String extractedPolicyId =
                    jsonObject.getValueOrThrow(PolicyCommandResponse.JsonFields.JSON_POLICY_ID);
            final PolicyId policyId = PolicyId.of(extractedPolicyId);
            final Label label = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));
            final Set<SubjectId> subjectIds = jsonObject.getValueOrThrow(JSON_SUBJECT_IDS).stream()
                    .filter(JsonValue::isString)
                    .map(JsonValue::asString)
                    .map(SubjectId::newInstance)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return new DeactivateTokenIntegration(policyId, label, subjectIds, dittoHeaders);
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

    @Override
    public Set<SubjectId> getSubjectIds() {
        return subjectIds;
    }

    @Override
    public DeactivateTokenIntegration setLabel(final Label label) {
        return new DeactivateTokenIntegration(policyId, label, subjectIds, getDittoHeaders());
    }

    @Override
    public boolean isApplicable(final PolicyEntry policyEntry, final AuthorizationContext authorizationContext) {
        // This action is applicable to policy entries
        //  containing the authenticated subject from the passed command's authorizationContext
        return policyEntry.getSubjects().stream()
                .anyMatch(subject -> authorizationContext.getAuthorizationSubjectIds()
                        .contains(subject.getId().toString())
                );
    }

    @Override
    public PolicyActionFailedException getNotApplicableException(final DittoHeaders dittoHeaders) {
        return PolicyActionFailedException.newBuilderForDeactivateTokenIntegration()
                .status(HttpStatus.NOT_FOUND)
                .description("No policy entry found containing one of the authorized subjects.")
                .dittoHeaders(dittoHeaders)
                .build();
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return Policy.JsonFields.ENTRIES.getPointer()
                .addLeaf(JsonKey.of(label))
                .append(RESOURCE_PATH_ACTIONS)
                .addLeaf(JsonKey.of(NAME));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommand.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_SUBJECT_IDS, subjectIds.stream()
                .map(SubjectId::toString)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray()), predicate);
    }

    @Override
    public DeactivateTokenIntegration setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new DeactivateTokenIntegration(policyId, label, subjectIds, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof DeactivateTokenIntegration;
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (null == obj || getClass() != obj.getClass()) {
            return false;
        }
        final DeactivateTokenIntegration that = (DeactivateTokenIntegration) obj;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(label, that.label) &&
                Objects.equals(subjectIds, that.subjectIds) &&
                super.equals(obj);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, subjectIds);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", policyId=" + policyId +
                ", label=" + label +
                ", subjectIds=" + subjectIds +
                "]";
    }

}
