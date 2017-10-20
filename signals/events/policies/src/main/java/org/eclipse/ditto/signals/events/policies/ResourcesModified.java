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
package org.eclipse.ditto.signals.events.policies;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Resources;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;


@Immutable
public final class ResourcesModified extends AbstractPolicyEvent<ResourcesModified>
        implements PolicyEvent<ResourcesModified> {

    /**
     * Name of this event
     */
    public static final String NAME = "resourcesModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_RESOURCES =
            JsonFactory.newJsonObjectFieldDefinition("resources", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final Label label;
    private final Resources resources;

    private ResourcesModified(final String policyId,
            final Label label,
            final Resources resources,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, checkNotNull(policyId, "Policy identifier"), revision, timestamp, dittoHeaders);
        this.label = checkNotNull(label, "Label");
        this.resources = checkNotNull(resources, "Resources");
    }

    /**
     * Constructs a new {@code ResourcesModified} object.
     *
     * @param policyId the identifier of the Policy to which the modified resources belongs
     * @param label the label of the Policy Entry to which the modified resources belongs
     * @param resources the modified {@link Resources}
     * @param revision the revision of the Policy.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created ResourcesModified.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ResourcesModified of(final String policyId,
            final Label label,
            final Resources resources,
            final long revision,
            final DittoHeaders dittoHeaders) {

        return of(policyId, label, resources, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code ResourcesModified} object.
     *
     * @param policyId the identifier of the Policy to which the modified resources belongs
     * @param label the label of the Policy Entry to which the modified resources belongs
     * @param resources the modified {@link Resources}
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created ResourcesModified.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static ResourcesModified of(final String policyId,
            final Label label,
            final Resources resources,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new ResourcesModified(policyId, label, resources, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code ResourcesModified} from a JSON string.
     *
     * @param jsonString the JSON string from which a new ResourcesModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code ResourcesModified} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected 'ResourcesModified'
     * format.
     */
    public static ResourcesModified fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ResourcesModified} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new ResourcesModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code ResourcesModified} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected 'ResourcesModified'
     * format.
     */
    public static ResourcesModified fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<ResourcesModified>(TYPE, jsonObject)
                .deserialize((revision, timestamp) -> {
                    final String policyId = jsonObject.getValueOrThrow(JsonFields.POLICY_ID);
                    final Label label = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));
                    final JsonObject resourcesJsonObject = jsonObject.getValueOrThrow(JSON_RESOURCES);
                    final Resources extractedModifiedResources = PoliciesModelFactory.newResources(resourcesJsonObject);

                    return of(policyId, label, extractedModifiedResources, revision, timestamp, dittoHeaders);
                });
    }

    /**
     * Returns the label of the Policy Entry to which the modified resources belongs.
     *
     * @return the label
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the modified {@link Resources}.
     *
     * @return the modified {@link Resources}.
     */
    public Resources getResources() {
        return resources;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(resources.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + label + "/resources";
        return JsonPointer.of(path);
    }

    @Override
    public ResourcesModified setRevision(final long revision) {
        return of(getPolicyId(), label, resources, revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public ResourcesModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyId(), label, resources, getRevision(), getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_RESOURCES, resources.toJson(schemaVersion, thePredicate), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(label);
        result = prime * result + Objects.hashCode(resources);
        return result;
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (null == o || getClass() != o.getClass()) {
            return false;
        }
        final ResourcesModified that = (ResourcesModified) o;
        return that.canEqual(this) && Objects.equals(label, that.label) && Objects.equals(resources, that.resources)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ResourcesModified;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", label=" + label + ", resources=" + resources
                + "]";
    }

}
