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
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ImportsAliases;

/**
 * This event is emitted after all {@link ImportsAliases} of a Policy were replaced.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableEvent(name = ImportsAliasesModified.NAME, typePrefix = PolicyEvent.TYPE_PREFIX)
public final class ImportsAliasesModified extends AbstractPolicyEvent<ImportsAliasesModified>
        implements PolicyEvent<ImportsAliasesModified> {

    /**
     * Name of this event.
     */
    public static final String NAME = "importsAliasesModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_IMPORTS_ALIASES =
            JsonFactory.newJsonObjectFieldDefinition("importsAliases", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final ImportsAliases importsAliases;

    private ImportsAliasesModified(final PolicyId policyId,
            final ImportsAliases importsAliases,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders, metadata);
        this.importsAliases = checkNotNull(importsAliases, "importsAliases");
    }

    /**
     * Constructs a new {@code ImportsAliasesModified} object indicating the replacement of all imports aliases.
     *
     * @param policyId the identifier of the Policy whose imports aliases were modified.
     * @param importsAliases the modified {@link ImportsAliases}.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the created ImportsAliasesModified.
     * @throws NullPointerException if any argument but {@code timestamp} and {@code metadata} is {@code null}.
     */
    public static ImportsAliasesModified of(final PolicyId policyId,
            final ImportsAliases importsAliases,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new ImportsAliasesModified(policyId, importsAliases, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code ImportsAliasesModified} from a JSON string.
     *
     * @param jsonString the JSON string from which a new ImportsAliasesModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code ImportsAliasesModified} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'ImportsAliasesModified' format.
     */
    public static ImportsAliasesModified fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ImportsAliasesModified} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new ImportsAliasesModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code ImportsAliasesModified} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'ImportsAliasesModified' format.
     */
    public static ImportsAliasesModified fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<ImportsAliasesModified>(TYPE, jsonObject)
                .deserialize((revision, timestamp, metadata) -> {
                    final String extractedPolicyId =
                            jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID);
                    final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final JsonObject aliasesJsonObject = jsonObject.getValueOrThrow(JSON_IMPORTS_ALIASES);
                    final ImportsAliases importsAliases =
                            PoliciesModelFactory.newImportsAliases(aliasesJsonObject);

                    return of(policyId, importsAliases, revision, timestamp, dittoHeaders, metadata);
                });
    }

    /**
     * Returns the modified {@link ImportsAliases}.
     *
     * @return the modified imports aliases.
     */
    public ImportsAliases getImportsAliases() {
        return importsAliases;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(importsAliases.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public ImportsAliasesModified setEntity(final JsonValue entity) {
        return of(getPolicyEntityId(), PoliciesModelFactory.newImportsAliases(entity.asObject()),
                getRevision(), getTimestamp().orElse(null), getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/importsAliases");
    }

    @Override
    public ImportsAliasesModified setRevision(final long revision) {
        return of(getPolicyEntityId(), importsAliases, revision, getTimestamp().orElse(null),
                getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public ImportsAliasesModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyEntityId(), importsAliases, getRevision(), getTimestamp().orElse(null),
                dittoHeaders, getMetadata().orElse(null));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_IMPORTS_ALIASES,
                importsAliases.toJson(schemaVersion, thePredicate), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(importsAliases);
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
        final ImportsAliasesModified that = (ImportsAliasesModified) o;
        return that.canEqual(this) &&
                Objects.equals(importsAliases, that.importsAliases) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ImportsAliasesModified;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", importsAliases=" + importsAliases +
                "]";
    }

}
