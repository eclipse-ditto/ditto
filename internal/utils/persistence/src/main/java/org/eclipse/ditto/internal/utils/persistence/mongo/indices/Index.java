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
package org.eclipse.ditto.internal.utils.persistence.mongo.indices;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.bson.BsonDocument;
import org.bson.Document;
import org.eclipse.ditto.internal.utils.persistence.mongo.BsonUtil;

import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;

/**
 * Defines a MongoDB Index. We do not like to use {@link IndexModel} directly, because it does not provide a proper
 * equals/hashCode implementation.
 */
public final class Index {

    private final BsonDocument keys;
    private final String name;
    private final boolean unique;
    private final boolean sparse;
    private final boolean background;
    private final BsonDocument wildcardProjection;

    @Nullable
    private final Long expireAfterSeconds;

    private Index(final BsonDocument keys, final String name, final boolean unique, final boolean sparse,
            final boolean background, final BsonDocument wildcardProjection,
            @Nullable final Long expireAfterSeconds) {

        this.keys = requireNonNull(keys);
        this.name = requireNonNull(name);
        this.unique = unique;
        this.sparse = sparse;
        this.background = background;
        this.expireAfterSeconds = expireAfterSeconds;
        this.wildcardProjection = wildcardProjection;
    }

    /**
     * Creates a new {@link Index} from the given parameters.
     *
     * @param keys the keys.
     * @param name the name.
     * @param unique whether this index is unique.
     * @param sparse whether this index is sparse.
     * @param background whether this index is to be built in the background.
     * @return the created {@link Index}.
     */
    static Index of(final BsonDocument keys, String name, boolean unique, boolean sparse, boolean background) {

        return new Index(keys, name, unique, sparse, background, new BsonDocument(), null);
    }

    /**
     * Creates new {@link Index} from the given {@code document}.
     *
     * @param document the document
     * @return the created index
     */
    public static Index indexInfoOf(final Document document) {
        requireNonNull(document);
        final BsonDocument keyDbObject = BsonUtil.toBsonDocument((Document) document.get("key"));

        final String name = document.get("name").toString();

        final boolean unique = document.get("unique", false);
        final boolean sparse = document.get("sparse", false);
        final boolean background = document.get("background", false);
        final Object expireAfterSecondsObject = document.get("expireAfterSeconds");
        final Long expireAfterSeconds = expireAfterSecondsObject != null
                ? ((Number) expireAfterSecondsObject).longValue()
                : null;

        final String wildcardProjectionName = "wildcardProjection";
        final BsonDocument wildcardProjection;
        if (document.containsKey(wildcardProjectionName)) {
            wildcardProjection = BsonUtil.toBsonDocument((Document) document.get(wildcardProjectionName));
        } else {
            wildcardProjection = new BsonDocument();
        }

        return new Index(keyDbObject, name, unique, sparse, background, wildcardProjection, expireAfterSeconds);
    }

    /**
     * Create a copy of this object with an expiration. Documents with indexed field smaller than the current epoch
     * second by this much are deleted in background.
     *
     * @param expireAfterSeconds how many seconds the deletion threshold lies before the current epoch second.
     * @return a copy of this object with expiration set.
     */
    public Index withExpireAfterSeconds(final long expireAfterSeconds) {
        return new Index(keys, name, unique, sparse, background, wildcardProjection, expireAfterSeconds);
    }

    /**
     * Create a copy of this object with a wildcard projection.
     *
     * @param wildcardProjection the wildcard projection.
     * @return a copy of this object with wildcard projection.
     */
    public Index withWildcardProjection(final BsonDocument wildcardProjection) {
        return new Index(keys, name, unique, sparse, background, wildcardProjection, expireAfterSeconds);
    }

    /**
     * Creates a new {@link IndexModel}, which can be used for creating indices using MongoDB Java drivers.
     *
     * @return the created {@link IndexModel}
     */
    public IndexModel toIndexModel() {
        final IndexOptions options = new IndexOptions()
                .name(name)
                .unique(unique)
                .sparse(sparse)
                .background(background);

        if (!wildcardProjection.isEmpty()) {
            options.wildcardProjection(wildcardProjection);
        }

        getExpireAfterSeconds().ifPresent(n -> options.expireAfter(n, TimeUnit.SECONDS));

        return new IndexModel(keys, options);
    }

    /**
     * Returns the keys.
     *
     * @return the keys
     */
    public BsonDocument getKeys() {
        return this.keys;
    }

    /**
     * Returns the name.
     *
     * @return the name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns whether this index is unique.
     *
     * @return whether this index is unique.
     */
    public boolean isUnique() {
        return unique;
    }

    /**
     * Returns whether this index is sparse.
     *
     * @return whether this index is sparse.
     */
    public boolean isSparse() {
        return sparse;
    }

    /**
     * Returns whether this index is to be built in the background.
     *
     * @return whether this index is to be built in the background.
     */
    public boolean isBackground() {
        return background;
    }

    /**
     * Returns the expiration threshold offset if it is set, or an empty optional if it is not set.
     *
     * @return the optional expiration threshold offset in seconds.
     */
    public Optional<Long> getExpireAfterSeconds() {
        return Optional.ofNullable(expireAfterSeconds);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Index indexInfo = (Index) o;
        return unique == indexInfo.unique &&
                sparse == indexInfo.sparse &&
                background == indexInfo.background &&
                Objects.equals(keys, indexInfo.keys) &&
                Objects.equals(name, indexInfo.name) &&
                Objects.equals(wildcardProjection, indexInfo.wildcardProjection) &&
                Objects.equals(expireAfterSeconds, indexInfo.expireAfterSeconds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keys, name, unique, sparse, background, wildcardProjection, expireAfterSeconds);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " [" +
                "keys=" + keys +
                ", name='" + name + '\'' +
                ", unique=" + unique +
                ", sparse=" + sparse +
                ", background=" + background +
                ", wildcardProjection=" + wildcardProjection +
                ", expireAfterSeconds=" + expireAfterSeconds +
                ']';
    }

}
