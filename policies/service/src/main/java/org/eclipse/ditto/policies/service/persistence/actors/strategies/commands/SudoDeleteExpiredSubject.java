/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.service.persistence.actors.strategies.commands;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.WithEntityId;
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
import org.eclipse.ditto.policies.api.commands.sudo.PolicySudoCommand;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;

/**
 * This internal command deletes all occurrences of an expired {@link Subject}.
 * There is no response.
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicySudoCommand.TYPE_PREFIX, name = SudoDeleteExpiredSubject.NAME)
public final class SudoDeleteExpiredSubject extends AbstractCommand<SudoDeleteExpiredSubject>
        implements PolicySudoCommand<SudoDeleteExpiredSubject>, WithEntityId {

    /**
     * Name of this command.
     */
    public static final String NAME = "sudoDeleteExpiredSubject";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_SUBJECT_ID = JsonFactory.newStringFieldDefinition("subjectId");

    static final JsonFieldDefinition<JsonObject> JSON_SUBJECT =
            JsonFactory.newJsonObjectFieldDefinition("subject", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final Subject subject;

    private SudoDeleteExpiredSubject(final PolicyId policyId, final Subject subject, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.policyId = policyId;
        this.subject = subject;
    }

    /**
     * Creates a command for deleting an expired {@code Subject}.
     *
     * @param policyId the identifier of the Policy.
     * @param subject the expired Subject to delete.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoDeleteExpiredSubject of(final PolicyId policyId,
            final Subject subject,
            final DittoHeaders dittoHeaders) {

        Objects.requireNonNull(policyId, "policyId");
        Objects.requireNonNull(subject, "subject");
        return new SudoDeleteExpiredSubject(policyId, subject, dittoHeaders);
    }

    /**
     * Creates a command for deleting an expired {@code Subject} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SudoDeleteExpiredSubject fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<SudoDeleteExpiredSubject>(TYPE, jsonObject).deserialize(() -> {
            final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyCommand.JsonFields.JSON_POLICY_ID);
            final PolicyId policyId = PolicyId.of(extractedPolicyId);
            final SubjectId subjectId = PoliciesModelFactory.newSubjectId(jsonObject.getValueOrThrow(JSON_SUBJECT_ID));
            final Subject subject =
                    PoliciesModelFactory.newSubject(subjectId, jsonObject.getValueOrThrow(JSON_SUBJECT));

            return of(policyId, subject, dittoHeaders);
        });
    }

    /**
     * Returns the expired {@code Subject} to delete.
     *
     * @return the subject.
     */
    public Subject getSubject() {
        return subject;
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommand.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_SUBJECT_ID, subject.getId().toString(), predicate);
        jsonObjectBuilder.set(JSON_SUBJECT, subject.toJson(), predicate);
    }

    @Override
    public Category getCategory() {
        return Category.DELETE;
    }

    @Override
    public SudoDeleteExpiredSubject setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, subject, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SudoDeleteExpiredSubject;
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
        final SudoDeleteExpiredSubject that = (SudoDeleteExpiredSubject) obj;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId) &&
                Objects.equals(subject, that.subject) && super.equals(obj);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, subject);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                " [" + super.toString() +
                ", policyId=" + policyId +
                ", subject=" + subject +
                "]";
    }

}
