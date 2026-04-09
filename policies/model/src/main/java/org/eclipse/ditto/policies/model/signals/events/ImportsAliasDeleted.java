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
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;

/**
 * This event is emitted after a {@link org.eclipse.ditto.policies.model.ImportsAlias} was deleted.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableEvent(name = ImportsAliasDeleted.NAME, typePrefix = PolicyEvent.TYPE_PREFIX)
public final class ImportsAliasDeleted extends AbstractPolicyEvent<ImportsAliasDeleted>
        implements PolicyEvent<ImportsAliasDeleted> {

    /**
     * Name of this event.
     */
    public static final String NAME = "importsAliasDeleted";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final Label label;

    private ImportsAliasDeleted(final PolicyId policyId,
            final Label label,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders, metadata);
        this.label = checkNotNull(label, "label");
    }

    /**
     * Constructs a new {@code ImportsAliasDeleted} object.
     *
     * @param policyId the identifier of the Policy from which the imports alias was deleted.
     * @param label the label of the deleted {@link org.eclipse.ditto.policies.model.ImportsAlias}.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the created ImportsAliasDeleted.
     * @throws NullPointerException if any argument but {@code timestamp} and {@code metadata} is {@code null}.
     */
    public static ImportsAliasDeleted of(final PolicyId policyId,
            final Label label,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new ImportsAliasDeleted(policyId, label, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code ImportsAliasDeleted} from a JSON string.
     *
     * @param jsonString the JSON string from which a new ImportsAliasDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code ImportsAliasDeleted} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'ImportsAliasDeleted' format.
     */
    public static ImportsAliasDeleted fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ImportsAliasDeleted} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new ImportsAliasDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code ImportsAliasDeleted} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'ImportsAliasDeleted' format.
     */
    public static ImportsAliasDeleted fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<ImportsAliasDeleted>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
                    final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID);
                    final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final Label label = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));

                    return of(policyId, label, revision, timestamp, dittoHeaders, metadata);
                });
    }

    /**
     * Returns the {@code Label} of the deleted {@link org.eclipse.ditto.policies.model.ImportsAlias}.
     *
     * @return the label.
     */
    public Label getLabel() {
        return label;
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/importsAliases/" + label;
        return JsonPointer.of(path);
    }

    @Override
    public ImportsAliasDeleted setRevision(final long revision) {
        return of(getPolicyEntityId(), label, revision, getTimestamp().orElse(null),
                getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public ImportsAliasDeleted setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyEntityId(), label, getRevision(), getTimestamp().orElse(null),
                dittoHeaders, getMetadata().orElse(null));
    }

    @Override
    public ImportsAliasDeleted setEntity(final JsonValue entity) {
        return this;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(label);
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
        final ImportsAliasDeleted that = (ImportsAliasDeleted) o;
        return that.canEqual(this) &&
                Objects.equals(label, that.label) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ImportsAliasDeleted;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", label=" + label + "]";
    }

}
