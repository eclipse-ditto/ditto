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
package org.eclipse.ditto.policies.model.signals.events;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyEntryNamespaces;


/**
 * This event is emitted after the namespaces of a {@link org.eclipse.ditto.policies.model.PolicyEntry} were modified.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableEvent(name = PolicyEntryNamespacesModified.NAME, typePrefix = PolicyEvent.TYPE_PREFIX)
public final class PolicyEntryNamespacesModified
        extends AbstractPolicyEvent<PolicyEntryNamespacesModified>
        implements PolicyEvent<PolicyEntryNamespacesModified> {

    /**
     * Name of this event.
     */
    public static final String NAME = "policyEntryNamespacesModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_NAMESPACES =
            JsonFactory.newJsonArrayFieldDefinition("namespaces", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final Label label;
    private final List<String> namespaces;

    private PolicyEntryNamespacesModified(final PolicyId policyId,
            final Label label,
            final List<String> namespaces,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "Policy identifier"), revision, timestamp, dittoHeaders, metadata);
        this.label = checkNotNull(label, "Label");
        checkNotNull(namespaces, "namespaces");
        PolicyEntryNamespaces.validate(namespaces);
        this.namespaces = Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(namespaces)));
    }

    /**
     * Constructs a new {@code PolicyEntryNamespacesModified} object.
     *
     * @param policyId the identifier of the Policy to which the modified namespaces belongs.
     * @param label the label of the Policy Entry to which the modified namespaces belongs.
     * @param namespaces the modified namespace patterns.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the created PolicyEntryNamespacesModified.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static PolicyEntryNamespacesModified of(final PolicyId policyId,
            final Label label,
            final List<String> namespaces,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new PolicyEntryNamespacesModified(policyId, label, namespaces, revision, timestamp, dittoHeaders,
                metadata);
    }

    /**
     * Creates a new {@code PolicyEntryNamespacesModified} from a JSON string.
     *
     * @param jsonString the JSON string from which a new PolicyEntryNamespacesModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyEntryNamespacesModified} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'PolicyEntryNamespacesModified' format.
     */
    public static PolicyEntryNamespacesModified fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyEntryNamespacesModified} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new PolicyEntryNamespacesModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyEntryNamespacesModified} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'PolicyEntryNamespacesModified' format.
     */
    public static PolicyEntryNamespacesModified fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new EventJsonDeserializer<PolicyEntryNamespacesModified>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
                    final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID);
                    final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final Label label = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));
                    final List<String> extractedNamespaces =
                            PolicyEntryNamespaces.fromJsonArray(jsonObject.getValueOrThrow(JSON_NAMESPACES));

                    return of(policyId, label, extractedNamespaces, revision, timestamp, dittoHeaders, metadata);
                });
    }

    /**
     * Returns the label of the Policy Entry to which the modified namespaces belongs.
     *
     * @return the label.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the modified namespace patterns.
     *
     * @return the modified namespace patterns.
     */
    public List<String> getNamespaces() {
        return namespaces;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(namespaces.stream()
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray()));
    }

    @Override
    public PolicyEntryNamespacesModified setEntity(final JsonValue entity) {
        final List<String> newNamespaces = PolicyEntryNamespaces.fromJsonArray(entity.asArray());
        return of(getPolicyEntityId(), label, newNamespaces, getRevision(), getTimestamp().orElse(null),
                getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + label + "/namespaces";
        return JsonPointer.of(path);
    }

    @Override
    public PolicyEntryNamespacesModified setRevision(final long revision) {
        return of(getPolicyEntityId(), label, namespaces, revision, getTimestamp().orElse(null),
                getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public PolicyEntryNamespacesModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyEntityId(), label, namespaces, getRevision(), getTimestamp().orElse(null),
                dittoHeaders, getMetadata().orElse(null));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_NAMESPACES, namespaces.stream()
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray()), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(label);
        result = prime * result + Objects.hashCode(namespaces);
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
        final PolicyEntryNamespacesModified that = (PolicyEntryNamespacesModified) o;
        return that.canEqual(this) && Objects.equals(label, that.label)
                && Objects.equals(namespaces, that.namespaces)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PolicyEntryNamespacesModified;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", label=" + label +
                ", namespaces=" + namespaces + "]";
    }

}
