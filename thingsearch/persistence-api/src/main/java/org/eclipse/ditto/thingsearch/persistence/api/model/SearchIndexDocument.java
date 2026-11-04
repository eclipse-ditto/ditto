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
package org.eclipse.ditto.thingsearch.persistence.api.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;

/**
 * Backend-neutral representation of a single search-index entry for a thing.
 * <p>
 * This is the storage-agnostic "truth" of what the search index holds for a thing: the thing's raw JSON
 * (keys are <em>not</em> encoder-escaped — key encoding such as Mongo's {@code KeyNameReviser} is a backend
 * concern applied when serializing this document), the policy-derived authorization projections, and the
 * revision/policy bookkeeping needed for consistency checks. A backend serializes this into its own on-disk
 * shape (Mongo: a BSON document with revised keys; PostgreSQL: a JSONB row plus flattened side rows).
 *
 * @since 4.0.0
 */
@Immutable
public final class SearchIndexDocument {

    private final ThingId thingId;
    private final String namespace;
    private final long revision;
    @Nullable private final PolicyId policyId;
    private final long policyRevision;
    private final Set<PolicyTag> referencedPolicies;
    private final List<String> globalRead;
    private final JsonObject thing;
    private final JsonObject policyAuth;
    private final List<FeatureEntry> features;

    private SearchIndexDocument(final Builder builder) {
        this.thingId = Objects.requireNonNull(builder.thingId, "thingId");
        this.namespace = Objects.requireNonNull(builder.namespace, "namespace");
        this.revision = builder.revision;
        this.policyId = builder.policyId;
        this.policyRevision = builder.policyRevision;
        this.referencedPolicies = Collections.unmodifiableSet(new LinkedHashSet<>(builder.referencedPolicies));
        this.globalRead = List.copyOf(builder.globalRead);
        this.thing = Objects.requireNonNull(builder.thing, "thing");
        this.policyAuth = Objects.requireNonNull(builder.policyAuth, "policyAuth");
        this.features = List.copyOf(builder.features);
    }

    /**
     * @param thingId the thing ID this document indexes.
     * @return a new builder for a search-index document.
     */
    public static Builder newBuilder(final ThingId thingId) {
        return new Builder(thingId);
    }

    /**
     * @return the ID of the indexed thing.
     */
    public ThingId thingId() {
        return thingId;
    }

    /**
     * @return the namespace of the indexed thing.
     */
    public String namespace() {
        return namespace;
    }

    /**
     * @return the revision of the indexed thing.
     */
    public long revision() {
        return revision;
    }

    /**
     * @return the ID of the policy enforcing access to the thing, if known.
     */
    public Optional<PolicyId> policyId() {
        return Optional.ofNullable(policyId);
    }

    /**
     * @return the revision of the enforcing policy.
     */
    public long policyRevision() {
        return policyRevision;
    }

    /**
     * @return the tags of all policies (directly and via import) referenced by the thing.
     */
    public Set<PolicyTag> referencedPolicies() {
        return referencedPolicies;
    }

    /**
     * @return the authorization subject IDs allowed to read the whole thing.
     */
    public List<String> globalRead() {
        return globalRead;
    }

    /**
     * @return the raw thing JSON (keys are NOT encoder-escaped).
     */
    public JsonObject thing() {
        return thing;
    }

    /**
     * @return the policy-derived read authorization projection for the thing.
     */
    public JsonObject policyAuth() {
        return policyAuth;
    }

    /**
     * @return the ordered per-feature index entries (feature ID, raw feature content and the policy-derived
     * read authorization projection), in the iteration order of the thing's {@code features} object.
     */
    public List<FeatureEntry> features() {
        return features;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SearchIndexDocument that = (SearchIndexDocument) o;
        return revision == that.revision &&
                policyRevision == that.policyRevision &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(namespace, that.namespace) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(referencedPolicies, that.referencedPolicies) &&
                Objects.equals(globalRead, that.globalRead) &&
                Objects.equals(thing, that.thing) &&
                Objects.equals(policyAuth, that.policyAuth) &&
                Objects.equals(features, that.features);
    }

    @Override
    public int hashCode() {
        return Objects.hash(thingId, namespace, revision, policyId, policyRevision, referencedPolicies, globalRead,
                thing, policyAuth, features);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "thingId=" + thingId +
                ", namespace=" + namespace +
                ", revision=" + revision +
                ", policyId=" + policyId +
                ", policyRevision=" + policyRevision +
                ", referencedPolicies=" + referencedPolicies +
                ", globalRead=" + globalRead +
                ", thing=" + thing +
                ", policyAuth=" + policyAuth +
                ", features=" + features +
                "]";
    }

    /**
     * Mutable builder for {@link SearchIndexDocument}.
     */
    @NotThreadSafe
    public static final class Builder {

        private final ThingId thingId;
        private String namespace;
        private long revision;
        @Nullable private PolicyId policyId;
        private long policyRevision;
        private Set<PolicyTag> referencedPolicies = Set.of();
        private List<String> globalRead = List.of();
        private JsonObject thing = JsonFactory.newObject();
        private JsonObject policyAuth = JsonFactory.newObject();
        private List<FeatureEntry> features = List.of();

        private Builder(final ThingId thingId) {
            this.thingId = Objects.requireNonNull(thingId, "thingId");
            this.namespace = thingId.getNamespace();
        }

        public Builder namespace(final String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder revision(final long revision) {
            this.revision = revision;
            return this;
        }

        public Builder policyId(@Nullable final PolicyId policyId) {
            this.policyId = policyId;
            return this;
        }

        public Builder policyRevision(final long policyRevision) {
            this.policyRevision = policyRevision;
            return this;
        }

        public Builder referencedPolicies(final Set<PolicyTag> referencedPolicies) {
            this.referencedPolicies = referencedPolicies;
            return this;
        }

        public Builder globalRead(final List<String> globalRead) {
            this.globalRead = globalRead;
            return this;
        }

        public Builder thing(final JsonObject thing) {
            this.thing = thing;
            return this;
        }

        public Builder policyAuth(final JsonObject policyAuth) {
            this.policyAuth = policyAuth;
            return this;
        }

        public Builder features(final List<FeatureEntry> features) {
            this.features = features;
            return this;
        }

        public SearchIndexDocument build() {
            return new SearchIndexDocument(this);
        }
    }

    /**
     * A single feature's entry in the search index: the feature ID, the feature's raw content (definition,
     * properties, desiredProperties, ... - keys are NOT encoder-escaped) and the policy-derived read
     * authorization projection for that feature (raw keys).
     * <p>
     * {@link #content()} is <em>not</em> subject to {@code IndexLengthRestrictionEnforcer} truncation and exists
     * solely so the Mongo encoder can reproduce the legacy {@code f} array. Search backends that flatten or index
     * document content MUST derive it from {@link SearchIndexDocument#thing()} — the enforced, length-restricted
     * source of truth — and MUST NOT flatten from {@code features().content()}.
     */
    @Immutable
    public static final class FeatureEntry {

        private final String featureId;
        private final JsonObject content;
        private final JsonObject auth;

        private FeatureEntry(final String featureId, final JsonObject content, final JsonObject auth) {
            this.featureId = Objects.requireNonNull(featureId, "featureId");
            this.content = Objects.requireNonNull(content, "content");
            this.auth = Objects.requireNonNull(auth, "auth");
        }

        /**
         * @param featureId the ID of the feature.
         * @param content the raw feature content (keys not encoder-escaped).
         * @param auth the policy-derived read authorization projection for the feature (raw keys).
         * @return a new feature index entry.
         */
        public static FeatureEntry of(final String featureId, final JsonObject content, final JsonObject auth) {
            return new FeatureEntry(featureId, content, auth);
        }

        /**
         * @return the ID of the feature.
         */
        public String featureId() {
            return featureId;
        }

        /**
         * @return the raw feature content (keys not encoder-escaped). This content is <em>not</em>
         * length-enforced (not truncated by {@code IndexLengthRestrictionEnforcer}) and exists solely so the
         * Mongo encoder can reproduce the legacy {@code f} array. Search backends that flatten or index document
         * content MUST derive it from {@link SearchIndexDocument#thing()} instead — the enforced, truncated
         * source of truth — and MUST NOT flatten from this field.
         */
        public JsonObject content() {
            return content;
        }

        /**
         * @return the policy-derived read authorization projection for the feature (raw keys).
         */
        public JsonObject auth() {
            return auth;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final FeatureEntry that = (FeatureEntry) o;
            return Objects.equals(featureId, that.featureId) &&
                    Objects.equals(content, that.content) &&
                    Objects.equals(auth, that.auth);
        }

        @Override
        public int hashCode() {
            return Objects.hash(featureId, content, auth);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "featureId=" + featureId +
                    ", content=" + content +
                    ", auth=" + auth +
                    "]";
        }
    }
}
