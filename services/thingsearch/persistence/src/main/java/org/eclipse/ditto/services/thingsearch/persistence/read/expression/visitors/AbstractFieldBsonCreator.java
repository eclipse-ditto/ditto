/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors;

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_GLOBAL_READ;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_GRANTED;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_REVOKED;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.SLASH;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.base.exceptions.InvalidRqlExpressionException;

import com.mongodb.client.model.Filters;

/**
 * Base class for visitors that create filter Bson from expressions.
 */
public abstract class AbstractFieldBsonCreator {

    @Nullable
    private final List<String> authorizationSubjectIds;

    AbstractFieldBsonCreator(@Nullable final List<String> authorizationSubjectIds) {
        this.authorizationSubjectIds = authorizationSubjectIds;
    }

    /**
     * Create filter BSON for global readability.
     *
     * @param authorizationSubjectIds authorization subject IDs for visibility restriction.
     * @return the BSON filter.
     */
    public static Bson getGlobalReadBson(final Iterable<String> authorizationSubjectIds) {
        return Filters.in(FIELD_GLOBAL_READ, authorizationSubjectIds);
    }

    /**
     * Method inherited in concrete subclasses from different BSON creator interfaces.
     *
     * @param featureId the feature ID of the feature-ID-property RQL expression.
     * @param property property path of the feature-ID-property RQL expression.
     * @return Bson created from this RQL expression.
     */
    public abstract Bson visitFeatureIdProperty(final String featureId, final String property);

    abstract Bson visitPointer(final String pointer);

    abstract Bson visitRootLevelField(final String fieldName);

    Optional<Bson> getAuthorizationBson() {
        return Optional.ofNullable(authorizationSubjectIds)
                .map(subjectIds -> Filters.and(
                        Filters.in(FIELD_GRANTED, authorizationSubjectIds),
                        Filters.nin(FIELD_REVOKED, authorizationSubjectIds)
                ));
    }

    /**
     * Feature ID wildcard '*' will match on the literal feature ID '*' because each feature property is extra
     * indexed under feature ID '*'.
     *
     * @param property property key of unknown feature.
     * @return nothing
     * @throws InvalidRqlExpressionException always
     */
    public final Bson visitFeatureProperty(final String property) {
        return visitFeatureIdProperty("*", property);
    }

    /**
     * Deprecate ACL expressions.
     *
     * @return nothing
     * @throws InvalidRqlExpressionException always
     */
    public final Bson visitAcl() {
        throw invalidRql("Unsupported path 'acl'");
    }

    /**
     * Deprecate global-reads expressions.
     *
     * @return nothing
     * @throws InvalidRqlExpressionException always
     */
    public final Bson visitGlobalReads() {
        throw invalidRql("Unsupported path 'gr'");
    }

    public final Bson visitSimple(final String fieldName) {
        return fieldName.startsWith(SLASH)
                ? visitPointer(fieldName)
                : visitRootLevelField(fieldName);
    }

    private static InvalidRqlExpressionException invalidRql(final String message) {
        return InvalidRqlExpressionException.newBuilder()
                .message(message)
                .build();
    }
}
