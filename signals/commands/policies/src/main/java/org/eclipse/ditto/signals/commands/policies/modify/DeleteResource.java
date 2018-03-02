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
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * This command deletes a {@link org.eclipse.ditto.model.policies.Resource} of a {@link
 * org.eclipse.ditto.model.policies.PolicyEntry}'s {@link org.eclipse.ditto.model.policies.Resources}.
 */
@Immutable
public final class DeleteResource extends AbstractCommand<DeleteResource>
        implements PolicyModifyCommand<DeleteResource> {

    /**
     * Name of this command.
     */
    public static final String NAME = "deleteResource";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_RESOURCE_KEY =
            JsonFactory.newStringFieldDefinition("resourceKey", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final String policyId;
    private final Label label;
    private final ResourceKey resourceKey;

    private DeleteResource(final String policyId, final Label label, final ResourceKey resourceKey,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        this.policyId = policyId;
        this.label = label;
        this.resourceKey = resourceKey;
    }

    /**
     * Creates a command for deleting a {@code Resource} of a {@code Policy}'s {@code PolicyEntry}.
     *
     * @param policyId the identifier of the Policy.
     * @param label the Label of the PolicyEntry.
     * @param resourceKey the ResourceKey of the Resource to delete.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DeleteResource of(final String policyId, final Label label, final ResourceKey resourceKey,
            final DittoHeaders dittoHeaders) {

        Objects.requireNonNull(policyId, "The Policy identifier must not be null!");
        Objects.requireNonNull(label, "The Label must not be null!");
        Objects.requireNonNull(resourceKey, "The ResourceKey must not be null!");
        return new DeleteResource(policyId, label, resourceKey, dittoHeaders);
    }

    /**
     * Creates a command for deleting a {@code Resource} of a {@code Policy}'s {@code PolicyEntry} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static DeleteResource fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a command for deleting a {@code Resource} of a {@code Policy}'s {@code PolicyEntry} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static DeleteResource fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<DeleteResource>(TYPE, jsonObject).deserialize(() -> {
            final String policyId = jsonObject.getValueOrThrow(PolicyModifyCommand.JsonFields.JSON_POLICY_ID);
            final Label label = PoliciesModelFactory.newLabel(jsonObject.getValueOrThrow(JSON_LABEL));
            final String resourceKey = jsonObject.getValueOrThrow(JSON_RESOURCE_KEY);

            return of(policyId, label, ResourceKey.newInstance(resourceKey), dittoHeaders);
        });
    }

    /**
     * Returns the {@code Label} of the {@code PolicyEntry} whose {@code Resource} to delete.
     *
     * @return the Label of the PolicyEntry whose Resource to delete.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the {@code ResourceKey} of the {@code Resource} to delete.
     *
     * @return the ResourceKey of the Resource to delete.
     */
    public ResourceKey getResourceKey() {
        return resourceKey;
    }

    /**
     * Returns the identifier of the {@code Policy} whose {@code PolicyEntry} to delete.
     *
     * @return the identifier of the Policy whose PolicyEntry to delete.
     */
    @Override
    public String getId() {
        return policyId;
    }

    @Override
    public JsonPointer getResourcePath() {
        final String p = "/entries/" + label + "/resources/" + resourceKey.toString();
        return JsonPointer.of(p);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyModifyCommand.JsonFields.JSON_POLICY_ID, policyId, predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_RESOURCE_KEY, resourceKey.toString(), predicate);
    }

    @Override
    public Category getCategory() {
        return Category.DELETE;
    }

    @Override
    public DeleteResource setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, label, resourceKey, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof DeleteResource;
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
        final DeleteResource that = (DeleteResource) obj;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId) && Objects.equals(label, that.label)
                && Objects.equals(resourceKey, that.resourceKey) && super.equals(obj);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, resourceKey);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", label=" + label
                + ", resourceKey=" + resourceKey + "]";
    }

}
