/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.base.exceptions.PathUnknownException;
import org.eclipse.ditto.signals.commands.things.ThingResourceVisitor;

/**
 * Calculates the {@code EntityTag} for all supported thing resources.
 */
class EntityTagCalculator implements ThingResourceVisitor<Thing, Optional<EntityTag>> {

    private static final EntityTagCalculator INSTANCE = new EntityTagCalculator();

    private EntityTagCalculator() {
    }

    static EntityTagCalculator getInstance() {
        return INSTANCE;
    }

    @Override
    public Optional<EntityTag> visitThing(final JsonPointer path, @Nullable final Thing thing) {
        return EntityTag.fromEntity(thing);
    }

    @Override
    public Optional<EntityTag> visitThingDefinition(final JsonPointer path, @Nullable final Thing thing) {
        return EntityTag.fromEntity(thing);
    }

    @Override
    public Optional<EntityTag> visitPolicyId(final JsonPointer path, @Nullable final Thing thing) {
        return EntityTag.fromEntity(thing);
    }

    @Override
    @Deprecated
    public Optional<EntityTag> visitAcl(final JsonPointer path, @Nullable final Thing thing) {
        return Optional.ofNullable(thing).flatMap(Thing::getAccessControlList).flatMap(EntityTag::fromEntity);
    }

    @Override
    @Deprecated
    public Optional<EntityTag> visitAclEntry(final JsonPointer path, @Nullable final Thing thing) {
        final AuthorizationSubject authSubject =
                AuthorizationModelFactory.newAuthSubject(extractSubjectId(path));
        return Optional.ofNullable(thing)
                .flatMap(Thing::getAccessControlList)
                .flatMap(acl -> acl.getEntryFor(authSubject))
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> visitAttributes(final JsonPointer path, @Nullable final Thing thing) {
        return Optional.ofNullable(thing).flatMap(Thing::getAttributes).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> visitAttribute(final JsonPointer path, @Nullable final Thing thing) {
        final JsonPointer attributePath = extractAttributePath(path);
        return Optional.ofNullable(thing)
                .flatMap(Thing::getAttributes)
                .flatMap(attr -> attr.getValue(attributePath))
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> visitFeature(final JsonPointer path, @Nullable final Thing thing) {
        final String featureId = extractFeatureId(path);
        return Optional.ofNullable(thing)
                .flatMap(Thing::getFeatures)
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> visitFeatures(final JsonPointer path, @Nullable final Thing thing) {
        return Optional.ofNullable(thing).flatMap(Thing::getFeatures).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> visitFeatureDefinition(final JsonPointer path, @Nullable final Thing thing) {
        final String featureId = extractFeatureId(path);
        return Optional.ofNullable(thing)
                .flatMap(Thing::getFeatures)
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(Feature::getDefinition)
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> visitFeatureProperties(final JsonPointer path, @Nullable final Thing thing) {
        final String featureId = extractFeatureId(path);
        return Optional.ofNullable(thing)
                .flatMap(Thing::getFeatures)
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(Feature::getProperties)
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> visitFeatureProperty(final JsonPointer path, @Nullable final Thing thing) {
        final String featureId = extractFeatureId(path);
        final JsonPointer propertyPath = extractFeaturePropertyPath(path);
        return Optional.ofNullable(thing)
                .flatMap(Thing::getFeatures)
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(Feature::getProperties)
                .flatMap(properties -> properties.getValue(propertyPath))
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> visitFeatureDesiredProperties(final JsonPointer path, @Nullable final Thing thing) {
        final String featureId = extractFeatureId(path);
        return Optional.ofNullable(thing)
                .flatMap(Thing::getFeatures)
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(Feature::getDesiredProperties)
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> visitFeatureDesiredProperty(final JsonPointer path, @Nullable final Thing thing) {
        final String featureId = extractFeatureId(path);
        final JsonPointer propertyPath = extractFeaturePropertyPath(path);
        return Optional.ofNullable(thing)
                .flatMap(Thing::getFeatures)
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(Feature::getDesiredProperties)
                .flatMap(properties -> properties.getValue(propertyPath))
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public DittoRuntimeException getUnknownPathException(final JsonPointer path) {
        return PathUnknownException.newBuilder(path).build();
    }
}
