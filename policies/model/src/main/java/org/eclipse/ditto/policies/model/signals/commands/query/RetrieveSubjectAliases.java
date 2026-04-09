/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model.signals.commands.query;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;

/**
 * Command which retrieves the {@link org.eclipse.ditto.policies.model.SubjectAliases} of a Policy based on the
 * passed in Policy ID.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicyCommand.TYPE_PREFIX, name = RetrieveSubjectAliases.NAME)
public final class RetrieveSubjectAliases extends AbstractCommand<RetrieveSubjectAliases>
        implements PolicyQueryCommand<RetrieveSubjectAliases> {

    /**
     * Name of the "Retrieve Subject Aliases" command.
     */
    public static final String NAME = "retrieveSubjectAliases";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final PolicyId policyId;

    private RetrieveSubjectAliases(final PolicyId policyId, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.policyId = checkNotNull(policyId, "Policy identifier");
    }

    /**
     * Returns a command for retrieving the subject aliases of a Policy with the given ID.
     *
     * @param policyId the ID of a single Policy whose subject aliases will be retrieved by this command.
     * @param dittoHeaders the optional command headers of the request.
     * @return a Command for retrieving the subject aliases of the Policy with the {@code policyId} which is readable
     * from the passed authorization context.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveSubjectAliases of(final PolicyId policyId, final DittoHeaders dittoHeaders) {
        return new RetrieveSubjectAliases(policyId, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveSubjectAliases} from a JSON string.
     *
     * @param jsonString the JSON string of which a new RetrieveSubjectAliases instance is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the {@code RetrieveSubjectAliases} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveSubjectAliases fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        final JsonObject jsonObject = JsonFactory.newObject(jsonString);

        return fromJson(jsonObject, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveSubjectAliases} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new RetrieveSubjectAliases instance is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the {@code RetrieveSubjectAliases} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveSubjectAliases fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<RetrieveSubjectAliases>(TYPE, jsonObject)
                .deserialize(() -> {
                    final PolicyId policyId = PolicyId.of(
                            jsonObject.getValueOrThrow(PolicyCommand.JsonFields.JSON_POLICY_ID));

                    return of(policyId, dittoHeaders);
                });
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/subjectAliases");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommand.JsonFields.JSON_POLICY_ID, policyId.toString(), predicate);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public RetrieveSubjectAliases setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, dittoHeaders);
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
        final RetrieveSubjectAliases that = (RetrieveSubjectAliases) obj;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveSubjectAliases;
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", policyId=" + policyId +
                "]";
    }

}
