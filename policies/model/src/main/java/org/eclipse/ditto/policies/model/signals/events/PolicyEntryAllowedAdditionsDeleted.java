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
 * Emitted after the {@code allowedAdditions} of a
 * {@link org.eclipse.ditto.policies.model.PolicyEntry} were cleared back to absent.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableEvent(name = PolicyEntryAllowedAdditionsDeleted.NAME, typePrefix = PolicyEvent.TYPE_PREFIX)
public final class PolicyEntryAllowedAdditionsDeleted
        extends AbstractPolicyEvent<PolicyEntryAllowedAdditionsDeleted>
        implements PolicyEvent<PolicyEntryAllowedAdditionsDeleted> {

    public static final String NAME = "policyEntryAllowedAdditionsDeleted";

    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final Label label;

    private PolicyEntryAllowedAdditionsDeleted(final PolicyId policyId,
            final Label label,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "Policy identifier"), revision, timestamp, dittoHeaders, metadata);
        this.label = checkNotNull(label, "Label");
    }

    public static PolicyEntryAllowedAdditionsDeleted of(final PolicyId policyId,
            final Label label,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new PolicyEntryAllowedAdditionsDeleted(policyId, label, revision, timestamp, dittoHeaders, metadata);
    }

    public static PolicyEntryAllowedAdditionsDeleted fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    public static PolicyEntryAllowedAdditionsDeleted fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new EventJsonDeserializer<PolicyEntryAllowedAdditionsDeleted>(TYPE, jsonObject)
                .deserialize((revision, timestamp, metadata) -> {
                    final PolicyId policyId =
                            PolicyId.of(jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID));
                    final Label extractedLabel = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));

                    return of(policyId, extractedLabel, revision, timestamp, dittoHeaders, metadata);
                });
    }

    public Label getLabel() {
        return label;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/entries/" + label + "/allowedAdditions");
    }

    @Override
    public PolicyEntryAllowedAdditionsDeleted setRevision(final long revision) {
        return of(getPolicyEntityId(), label, revision, getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }

    @Override
    public PolicyEntryAllowedAdditionsDeleted setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyEntityId(), label, getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    public PolicyEntryAllowedAdditionsDeleted setEntity(final JsonValue entity) {
        return this;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(label);
        return result;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (null == o || getClass() != o.getClass()) {
            return false;
        }
        final PolicyEntryAllowedAdditionsDeleted that = (PolicyEntryAllowedAdditionsDeleted) o;
        return that.canEqual(this) && Objects.equals(label, that.label) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PolicyEntryAllowedAdditionsDeleted;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", label=" + label + "]";
    }

}
