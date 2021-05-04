/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;

/**
 * Command which retrieves the {@code Resource} based on the passed in Policy ID, Label and Resource resourceKey.
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicyCommand.TYPE_PREFIX, name = RetrieveResource.NAME)
public final class RetrieveResource extends AbstractCommand<RetrieveResource>
        implements PolicyQueryCommand<RetrieveResource> {

    /**
     * Name of the retrieve "Retrieve Resource" command.
     */
    public static final String NAME = "retrieveResource";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_RESOURCE_KEY =
            JsonFactory.newStringFieldDefinition("resourceKey", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final Label label;
    private final ResourceKey resourceKey;

    private RetrieveResource(final PolicyId policyId,
            final Label label,
            final ResourceKey resourceKey,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policy ID");
        this.label = checkNotNull(label, "Label");
        this.resourceKey = checkNotNull(resourceKey, "ResourceKey");
    }

    /**
     * Returns a command for retrieving the Resource with the given Policy ID, Label and Resource resourceKey.
     *
     * @param policyId the ID of the Policy for which to retrieve the Resource for.
     * @param label the specified label of the Policy entry for which to retrieve the Resource for.
     * @param resourceKey the ResourceKey of the Resource to retrieve.
     * @param dittoHeaders the optional command headers of the request.
     * @return a Command for retrieving the Resource with the {@code policyId}, {@code label} and {@code resourcePath}
     * which is readable from the passed authorization context.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveResource of(final PolicyId policyId,
            final Label label,
            final ResourceKey resourceKey,
            final DittoHeaders dittoHeaders) {

        return new RetrieveResource(policyId, label, resourceKey, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveResource} from a JSON string.
     *
     * @param jsonString the JSON string of which a new RetrieveResource instance is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the {@code RetrieveResource} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveResource fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        final JsonObject jsonObject = JsonFactory.newObject(jsonString);

        return fromJson(jsonObject, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveResource} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new RetrieveResource instance is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the {@code RetrieveResources} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveResource fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<RetrieveResource>(TYPE, jsonObject).deserialize(() -> {
            final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyCommand.JsonFields.JSON_POLICY_ID);
            final PolicyId policyId = PolicyId.of(extractedPolicyId);
            final Label extractedLabel = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));
            final String extractedKey = jsonObject.getValueOrThrow(JSON_RESOURCE_KEY);

            return of(policyId, extractedLabel, ResourceKey.newInstance(extractedKey), dittoHeaders);
        });
    }

    /**
     * Returns the {@code Label} of the {@code PolicyEntry} for which to retrieve the Resource for.
     *
     * @return the Label of the PolicyEntry for which to retrieve the Resource for.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the ResourceKey for which to retrieve the Resource for.
     *
     * @return the ResourceKey for which to retrieve the Resource for.
     */
    public ResourceKey getResourceKey() {
        return resourceKey;
    }

    /**
     * Returns the identifier of the {@code Policy} for which to retrieve the Resource for.
     *
     * @return the identifier of the Policy for which to retrieve the Resource for.
     */
    @Override
    public PolicyId getEntityId() {
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
        jsonObjectBuilder.set(PolicyCommand.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_RESOURCE_KEY, resourceKey.toString(), predicate);
    }

    @Override
    public RetrieveResource setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, label, resourceKey, dittoHeaders);
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
        final RetrieveResource that = (RetrieveResource) obj;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId) && Objects.equals(label, that.label)
                && Objects.equals(resourceKey, that.resourceKey) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveResource;
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
