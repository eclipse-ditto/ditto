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
package org.eclipse.ditto.things.model.signals.commands;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonPointer;

/**
 * Maps a JsonPointer to another value by using a
 * {@link ThingResourceVisitor}.
 *
 * @param <P> an extra parameter passed to the visit method
 * @param <R> the result of the mapping
 */
public class ThingResourceMapper<P, R> {

    final ThingResourceVisitor<P, R> visitor;

    private ThingResourceMapper(final ThingResourceVisitor<P, R> visitor) {
        this.visitor = checkNotNull(visitor, "visitor");
    }

    /**
     * @param visitor the visitor used to map the JsonPointer
     * @param <P> an extra parameter passed to the visit method
     * @param <R> the result of the mapping
     * @return new instance of ThingResourceMapper
     */
    public static <P, R> ThingResourceMapper<P, R> from(final ThingResourceVisitor<P, R> visitor) {
        return new ThingResourceMapper<>(visitor);
    }

    /**
     * Maps the given path to another value.
     *
     * @param path the path that is mapped
     * @param param the extra parameter passed through to the visit method
     * @return the mapping result
     */
    public R map(final JsonPointer path, @Nullable final P param) {
        final ThingResource resource =
                ThingResource.from(path).orElseThrow(() -> visitor.getUnknownPathException(path));
        switch (resource) {
            case THING:
                return visitor.visitThing(path, param);
            case POLICY_ID:
                return visitor.visitPolicyId(path, param);
            case ATTRIBUTES:
                return visitor.visitAttributes(path, param);
            case ATTRIBUTE:
                return visitor.visitAttribute(path, param);
            case FEATURES:
                return visitor.visitFeatures(path, param);
            case FEATURE:
                return visitor.visitFeature(path, param);
            case DEFINITION:
                return visitor.visitThingDefinition(path, param);
            case FEATURE_DEFINITION:
                return visitor.visitFeatureDefinition(path, param);
            case FEATURE_PROPERTIES:
                return visitor.visitFeatureProperties(path, param);
            case FEATURE_PROPERTY:
                return visitor.visitFeatureProperty(path, param);
            case FEATURE_DESIRED_PROPERTIES:
                return visitor.visitFeatureDesiredProperties(path, param);
            case FEATURE_DESIRED_PROPERTY:
                return visitor.visitFeatureDesiredProperty(path, param);
            default:
                throw visitor.getUnknownPathException(path);
        }
    }
}
