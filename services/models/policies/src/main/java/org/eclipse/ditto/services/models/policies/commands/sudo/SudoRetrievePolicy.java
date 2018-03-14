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
package org.eclipse.ditto.services.models.policies.commands.sudo;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Command which retrieves one {@link org.eclipse.ditto.model.policies.Policy} based on the the passed in Policy ID w/o
 * authorization context.
 */
@Immutable
@AllValuesAreNonnullByDefault
public final class SudoRetrievePolicy extends AbstractCommand<SudoRetrievePolicy>
        implements SudoCommand<SudoRetrievePolicy> {

    /**
     * Name of the "Sudo Retrieve Policy" command.
     */
    public static final String NAME = "sudoRetrievePolicy";

    /**
     * Type of this command.
     */
    public static final String TYPE = SudoCommand.TYPE_PREFIX + NAME;

    private final String policyId;

    private SudoRetrievePolicy(final String policyId, final DittoHeaders dittoHeaders) {
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
    public static SudoRetrievePolicy of(final String policyId, final DittoHeaders dittoHeaders) {
        return new SudoRetrievePolicy(policyId, dittoHeaders);
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
    public static SudoRetrievePolicy fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
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
    public static SudoRetrievePolicy fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final String extractedPolicyId = jsonObject.getValueOrThrow(SudoCommand.JsonFields.JSON_POLICY_ID);

        return SudoRetrievePolicy.of(extractedPolicyId, dittoHeaders);
    }

    /**
     * Returns the identifier of the {@code Policy} to retrieve.
     *
     * @return the identifier of the Policy to retrieve.
     */
    @Override
    public String getId() {
        return policyId;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(SudoCommand.JsonFields.JSON_POLICY_ID, policyId, predicate);
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public SudoRetrievePolicy setDittoHeaders(final DittoHeaders dittoHeaders) {
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
        final SudoRetrievePolicy that = (SudoRetrievePolicy) obj;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SudoRetrievePolicy;
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
