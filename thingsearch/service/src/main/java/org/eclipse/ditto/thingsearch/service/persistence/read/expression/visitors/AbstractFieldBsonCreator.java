/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.read.expression.visitors;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.bson.conversions.Bson;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;

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
        return Filters.in(PersistenceConstants.FIELD_GLOBAL_READ, authorizationSubjectIds);
    }

    abstract Bson visitPointer(final String pointer);

    abstract Bson visitRootLevelField(final String fieldName);

    Optional<Bson> getAuthorizationBson() {
        return Optional.ofNullable(authorizationSubjectIds)
                .map(subjectIds -> Filters.and(
                        Filters.in(PersistenceConstants.FIELD_GRANTED, authorizationSubjectIds),
                        Filters.nin(PersistenceConstants.FIELD_REVOKED, authorizationSubjectIds)
                ));
    }

    public final Bson visitSimple(final String fieldName) {
        return fieldName.startsWith(PersistenceConstants.SLASH)
                ? visitPointer(fieldName)
                : visitRootLevelField(fieldName);
    }

}
