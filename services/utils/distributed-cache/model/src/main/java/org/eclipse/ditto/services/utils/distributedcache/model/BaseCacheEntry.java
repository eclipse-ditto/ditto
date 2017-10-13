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
package org.eclipse.ditto.services.utils.distributedcache.model;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * A basic implementation of {@link CacheEntry}. The purpose of this class is to reduce required effort of
 * implementing a CacheEntry by wrapping an instance of it in the concrete implementation and delegating as much as
 * possible to the wrapped instance.
 */
@Immutable
public final class BaseCacheEntry implements CacheEntry {

    @Nullable private final String policyId;
    private final long revision;
    private final boolean deleted;
    @Nullable private final JsonSchemaVersion jsonSchemaVersion;

    private BaseCacheEntry(@Nullable final String thePolicyId, final long theRevision, final boolean deleted,
            @Nullable final JsonSchemaVersion theJsonSchemaVersion) {

        policyId = thePolicyId;
        revision = theRevision;
        this.deleted = deleted;
        jsonSchemaVersion = theJsonSchemaVersion;
    }

    /**
     * Returns a new instance of {@code BaseCacheEntry}.
     *
     * @param policyId the cached policy ID of the entity or {@code null}.
     * @param revision the cached revision of the entity.
     * @param deleted indicates whether the returned cache entry is marked as deleted.
     * @param jsonSchemaVersion the cached JSON schema version of the entity or {@code null}.
     * @return the instance.
     */
    public static BaseCacheEntry newInstance(@Nullable final CharSequence policyId, final long revision,
            final boolean deleted, @Nullable final JsonSchemaVersion jsonSchemaVersion) {

        return new BaseCacheEntry(null != policyId ? policyId.toString() : null, revision, deleted, jsonSchemaVersion);
    }

    /**
     * Returns a new {@code BaseCacheEntry} object based on the specified JSON object.
     *
     * @param cacheEntryJsonObject the JSON object which provides the values for the returned instance.
     * @return the instance.
     * @throws NullPointerException if {@code cacheEntryJsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code cacheEntryJsonObject} did not contain a field
     * for {@link JsonFields#REVISION} or {@link JsonFields#DELETED}.
     */
    public static BaseCacheEntry fromJson(final JsonObject cacheEntryJsonObject) {
        checkNotNull(cacheEntryJsonObject, "CacheEntry JSON object");

        final String readPolicyId = cacheEntryJsonObject.getValue(JsonFields.POLICY_ID).orElse(null);
        final long readRevision = cacheEntryJsonObject.getValueOrThrow(JsonFields.REVISION);
        final boolean isDeleted = cacheEntryJsonObject.getValueOrThrow(JsonFields.DELETED);
        final JsonSchemaVersion readJsonSchemaVersion = cacheEntryJsonObject.getValue(JsonFields.JSON_SCHEMA_VERSION)
                .flatMap(JsonSchemaVersion::forInt)
                .orElse(null);

        return newInstance(readPolicyId, readRevision, isDeleted, readJsonSchemaVersion);
    }

    @Override
    public Optional<String> getPolicyId() {
        return Optional.ofNullable(policyId);
    }

    @Override
    public long getRevision() {
        return revision;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public CacheEntry asDeleted(final long newRevision) {
        return newInstance(null, newRevision, true, null);
    }

    @Override
    public Optional<JsonSchemaVersion> getJsonSchemaVersion() {
        return Optional.ofNullable(jsonSchemaVersion);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final BaseCacheEntry that = (BaseCacheEntry) o;
        return revision == that.revision &&
                deleted == that.deleted &&
                Objects.equals(policyId, that.policyId) &&
                jsonSchemaVersion == that.jsonSchemaVersion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(policyId, revision, deleted, jsonSchemaVersion);
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        if (null != policyId) {
            jsonObjectBuilder.set(JsonFields.POLICY_ID, policyId);
        }
        jsonObjectBuilder.set(JsonFields.REVISION, revision);
        jsonObjectBuilder.set(JsonFields.DELETED, deleted);
        if (null != jsonSchemaVersion) {
            jsonObjectBuilder.set(JsonFields.JSON_SCHEMA_VERSION, jsonSchemaVersion.toInt());
        }

        return jsonObjectBuilder.build();
    }

    /**
     * Returns the name of the instance variables and their values only. Thus this method can be used by wrapping
     * classes to create their string representation.
     *
     * @return the names and values of the instance variables of this object.
     */
    @Override
    public String toString() {
        return "policyId=" + policyId +
                ", revision=" + revision +
                ", deleted=" + deleted +
                ", jsonSchemaVersion=" + jsonSchemaVersion;
    }

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a CacheEntry.
     */
    @Immutable
    public static final class JsonFields {

        /**
         * JSON field containing the {@link JsonSchemaVersion} as {@code int}.
         */
        public static final JsonFieldDefinition<Integer> JSON_SCHEMA_VERSION =
                JsonFactory.newIntFieldDefinition("schemaVersion");

        /**
         * JSON field containing the ThingCacheEntry's Policy ID as {@code String}.
         */
        public static final JsonFieldDefinition<String> POLICY_ID = JsonFactory.newStringFieldDefinition("policyId");

        /**
         * JSON field indicating whether the ThingCacheEntry is deleted or not.
         */
        public static final JsonFieldDefinition<Boolean> DELETED = JsonFactory.newBooleanFieldDefinition("deleted");

        /**
         * JSON field containing the ThingCacheEntry's revision as {@code long}.
         */
        public static final JsonFieldDefinition<Long> REVISION = JsonFactory.newLongFieldDefinition("revision");

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
