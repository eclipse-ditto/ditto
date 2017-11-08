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
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

/**
 * Base implementation of {@code ResourcePermissions} to be used as delegation target within the more specific
 * ResourcePermissions implementations.
 */
@Immutable
final class BaseResourcePermissions implements ResourcePermissions {

    private final String resource;
    private final Set<String> readGrantedSubjectIds;
    private final Set<String> readRevokedSubjectIds;

    /**
     * Constructs a new {@code BaseResourcePermissions} object.
     *
     * @param resource the resource.
     * @param readGrantedSubjectIds IDs of subjects which are granted READ permission on {@code resource}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    BaseResourcePermissions(@Nonnull final CharSequence resource,
            @Nonnull final Set<String> readGrantedSubjectIds, @Nonnull final Set<String> readRevokedSubjectIds) {
        argumentNotEmpty(resource, "resource");
        checkNotNull(readGrantedSubjectIds, "read granted subjects");
        checkNotNull(readRevokedSubjectIds, "read revoked subjects");

        this.resource = resource.toString();
        this.readGrantedSubjectIds = Collections.unmodifiableSet(new HashSet<>(readGrantedSubjectIds));
        this.readRevokedSubjectIds = Collections.unmodifiableSet(new HashSet<>(readRevokedSubjectIds));
    }

    @Nonnull
    @Override
    public String getResource() {
        return resource;
    }

    @Nonnull
    @Override
    public Set<String> getReadGrantedSubjectIds() {
        return readGrantedSubjectIds;
    }

    @Nonnull
    @Override
    public Set<String> getReadRevokedSubjectIds() {
        return readRevokedSubjectIds;
    }

    @Nonnull
    @Override
    public String createPolicyEntryId(@Nonnull final CharSequence thingId) {
        argumentNotEmpty(thingId, "thing ID");
        return thingId + ":" + resource;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final BaseResourcePermissions that = (BaseResourcePermissions) o;
        return Objects.equals(resource, that.resource) &&
                Objects.equals(readGrantedSubjectIds, that.readGrantedSubjectIds) &&
                Objects.equals(readRevokedSubjectIds, that.readRevokedSubjectIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resource, readGrantedSubjectIds, readRevokedSubjectIds);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "resource=" + resource +
                ", readGrantedSubjects=" + readGrantedSubjectIds +
                ", readRevokedSubjects=" + readRevokedSubjectIds +
                "]";
    }

}
