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
package org.eclipse.ditto.signals.commands.policies.modify;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * This command deletes a {@link org.eclipse.ditto.model.policies.Policy}.
 */
@Immutable
public final class DeletePolicy extends AbstractCommand<DeletePolicy> implements PolicyModifyCommand<DeletePolicy> {

    /**
     * Name of this command.
     */
    public static final String NAME = "deletePolicy";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final String policyId;

    private DeletePolicy(final String policyId, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.policyId = policyId;
    }

    /**
     * Creates a command for deleting a {@code Policy}.
     *
     * @param policyId the identifier of the Policy to delete.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DeletePolicy of(final String policyId, final DittoHeaders dittoHeaders) {
        Objects.requireNonNull(policyId, "The identifier of the Policy must not be null!");
        return new DeletePolicy(policyId, dittoHeaders);
    }

    /**
     * Creates a command for deleting a {@code Policy} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static DeletePolicy fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a command for deleting a {@code Policy} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static DeletePolicy fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<DeletePolicy>(TYPE, jsonObject).deserialize(() -> {
            final String policyId = jsonObject.getValueOrThrow(PolicyModifyCommand.JsonFields.JSON_POLICY_ID);

            return of(policyId, dittoHeaders);
        });
    }

    /**
     * Returns the identifier of the {@code Policy} to delete.
     *
     * @return the identifier of the Policy to delete.
     */
    @Override
    public String getId() {
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
        jsonObjectBuilder.set(PolicyModifyCommand.JsonFields.JSON_POLICY_ID, policyId, predicate);
    }

    @Override
    public Category getCategory() {
        return Category.DELETE;
    }

    @Override
    public DeletePolicy setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof DeletePolicy;
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
        final DeletePolicy that = (DeletePolicy) obj;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId) && super.equals(obj);
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
