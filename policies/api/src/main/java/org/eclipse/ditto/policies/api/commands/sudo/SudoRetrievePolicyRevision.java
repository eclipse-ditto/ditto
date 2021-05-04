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
package org.eclipse.ditto.policies.api.commands.sudo;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Command which retrieves one policy revision based on the the passed in Policy ID w/o
 * authorization context.
 */
@Immutable
@AllValuesAreNonnullByDefault
@JsonParsableCommand(typePrefix = SudoCommand.TYPE_PREFIX, name = SudoRetrievePolicyRevision.NAME)
public final class SudoRetrievePolicyRevision extends AbstractCommand<SudoRetrievePolicyRevision>
        implements SudoCommand<SudoRetrievePolicyRevision>, SignalWithEntityId<SudoRetrievePolicyRevision> {

    /**
     * Name of the "Sudo Retrieve Policy" command.
     */
    public static final String NAME = "sudoRetrievePolicyRevision";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final PolicyId policyId;

    private SudoRetrievePolicyRevision(final PolicyId policyId, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);

        this.policyId = checkNotNull(policyId, "Policy identifier");
    }

    /**
     * Returns a Command for retrieving the Policy with the given ID.
     *
     * @param policyId the ID of a single Policy to be retrieved by this command.
     * @param dittoHeaders the optional command headers of the request.
     * @return a Command for retrieving the Policy with the {@code policyId} as its ID.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoRetrievePolicyRevision of(final PolicyId policyId, final DittoHeaders dittoHeaders) {
        return new SudoRetrievePolicyRevision(policyId, dittoHeaders);
    }

    /**
     * Creates a new {@code SudoRetrievePolicy} from a JSON string.
     *
     * @param jsonString the JSON string of which a new SudoRetrievePolicy instance is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the {@code SudoRetrievePolicy} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static SudoRetrievePolicyRevision fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        final JsonObject jsonObject = JsonFactory.newObject(jsonString);

        return fromJson(jsonObject, dittoHeaders);
    }

    /**
     * Creates a new {@code SudoRetrievePolicy} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new SudoRetrievePolicy instance is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the {@code SudoRetrievePolicy} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SudoRetrievePolicyRevision fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final String extractedPolicyId = jsonObject.getValueOrThrow(SudoCommand.JsonFields.JSON_POLICY_ID);
        final PolicyId policyId = PolicyId.of(extractedPolicyId);

        return SudoRetrievePolicyRevision.of(policyId, dittoHeaders);
    }

    /**
     * Returns the identifier of the {@code Policy} to retrieve.
     *
     * @return the identifier of the Policy to retrieve.
     */
    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(SudoCommand.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public SudoRetrievePolicyRevision setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, dittoHeaders);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final SudoRetrievePolicyRevision that = (SudoRetrievePolicyRevision) obj;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SudoRetrievePolicyRevision;
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + "]";
    }

}
