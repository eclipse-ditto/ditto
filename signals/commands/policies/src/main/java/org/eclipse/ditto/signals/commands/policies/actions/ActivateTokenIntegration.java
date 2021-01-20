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
import java.time.format.DateTimeParseException;
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
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;

/**
 * This command injects one or several subjects derived from a provided token including an "expiry" extracted from the
 * token into an existing policy entry.
 *
 * @since 2.0.0
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicyCommand.TYPE_PREFIX, name = ActivateTokenIntegration.NAME)
public final class ActivateTokenIntegration extends AbstractCommand<ActivateTokenIntegration>
        implements PolicyActionCommand<ActivateTokenIntegration> {

    /**
     * NAME of this command.
     */
    public static final String NAME = "activateTokenIntegration";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_SUBJECT_IDS =
            JsonFactory.newJsonArrayFieldDefinition("subjectIds", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_EXPIRY =
            JsonFactory.newStringFieldDefinition("expiry", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final String READ_PERMISSION = "READ";

    private final PolicyId policyId;
    private final Label label;
    private final Set<SubjectId> subjectIds;
    private final Instant expiry;

    private ActivateTokenIntegration(final PolicyId policyId, final Label label, final Collection<SubjectId> subjectIds,
            final Instant expiry, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.label = checkNotNull(label, "label");
        this.subjectIds = Collections.unmodifiableSet(new LinkedHashSet<>(checkNotNull(subjectIds, "subjectIds")));
        this.expiry = checkNotNull(expiry, "expiry");
    }

    /**
     * Creates a command for activating a token subject.
     *
     * @param policyId the identifier of the Policy.
     * @param label label of the policy entry where the subject should be activated.
     * @param subjectIds subject IDs to activate.
     * @param expiry when the subject expires.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ActivateTokenIntegration of(final PolicyId policyId, final Label label,
            final Collection<SubjectId> subjectIds, final Instant expiry, final DittoHeaders dittoHeaders) {

        return new ActivateTokenIntegration(policyId, label, subjectIds, expiry, dittoHeaders);
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
    public static ActivateTokenIntegration fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ActivateTokenIntegration>(TYPE, jsonObject).deserialize(() -> {
            final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyCommand.JsonFields.JSON_POLICY_ID);
            final PolicyId policyId = PolicyId.of(extractedPolicyId);
            final Label label = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));
            final Set<SubjectId> subjectIds = jsonObject.getValueOrThrow(JSON_SUBJECT_IDS).stream()
                    .filter(JsonValue::isString)
                    .map(JsonValue::asString)
                    .map(SubjectId::newInstance)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            final Instant expiry = parseExpiry(jsonObject.getValueOrThrow(JSON_EXPIRY));
            return new ActivateTokenIntegration(policyId, label, subjectIds, expiry, dittoHeaders);
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
    public ActivateTokenIntegration setLabel(final Label label) {
        return new ActivateTokenIntegration(policyId, label, subjectIds, expiry, getDittoHeaders());
    }

    @Override
    public boolean isApplicable(final PolicyEntry policyEntry) {
        // This action is applicable to policy entries containing a READ permission grated to a thing resource.
        return policyEntry.getResources()
                .stream()
                .anyMatch(resource -> {
                    final String resourceType = resource.getResourceKey().getResourceType();
                    final EffectedPermissions permissions = resource.getEffectedPermissions();
                    return PoliciesResourceType.THING.equals(resourceType) &&
                            permissions.getGrantedPermissions().contains(READ_PERMISSION) &&
                            !permissions.getRevokedPermissions().contains(READ_PERMISSION);
                });
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
        jsonObjectBuilder.set(JSON_EXPIRY, expiry.toString(), predicate);
    }

    @Override
    public ActivateTokenIntegration setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ActivateTokenIntegration(policyId, label, subjectIds, expiry, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ActivateTokenIntegration;
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (null == obj || getClass() != obj.getClass()) {
            return false;
        }
        final ActivateTokenIntegration that = (ActivateTokenIntegration) obj;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(label, that.label) &&
                Objects.equals(subjectIds, that.subjectIds) &&
                Objects.equals(expiry, that.expiry) &&
                super.equals(obj);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, subjectIds, expiry);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", policyId=" + policyId +
                ", label=" + label +
                ", subjectIds=" + subjectIds +
                ", expiry=" + expiry +
                "]";
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

}
