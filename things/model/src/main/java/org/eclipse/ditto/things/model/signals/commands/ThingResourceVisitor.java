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

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;

/**
 * Defines methods where implementing classes can handle the different resources in the respective methods.
 *
 * @param <P> the additional parameter that is passed through to the visit method
 * @param <R> the result type of the visit method
 */
public interface ThingResourceVisitor<P, R> {

    /**
     * Invoked for the {@link ThingResource#THING} resource.
     *
     * @param path the original resource path
     * @param param the parameter that is passed through
     * @return the result for the {@link ThingResource#THING} resource.
     */
    R visitThing(JsonPointer path, @Nullable P param);

    /**
     * Invoked for the {@link ThingResource#DEFINITION} resource.
     *
     * @param path the original resource path
     * @param param the parameter that is passed through
     * @return the result for the {@link ThingResource#DEFINITION} resource.
     */
    R visitThingDefinition(JsonPointer path, @Nullable P param);

    /**
     * Invoked for the {@link ThingResource#POLICY_ID} resource.
     *
     * @param path the original resource path
     * @param param the parameter that is passed through
     * @return the result for the {@link ThingResource#POLICY_ID} resource.
     */
    R visitPolicyId(JsonPointer path, @Nullable P param);

    /**
     * Invoked for the {@link ThingResource#ATTRIBUTES} resource.
     *
     * @param path the original resource path
     * @param param the parameter that is passed through
     * @return the result for the {@link ThingResource#ATTRIBUTES} resource.
     */
    R visitAttributes(JsonPointer path, @Nullable P param);

    /**
     * Invoked for the {@link ThingResource#ATTRIBUTE} resource.
     *
     * @param path the original resource path
     * @param param the parameter that is passed through
     * @return the result for the {@link ThingResource#ATTRIBUTE} resource.
     */
    R visitAttribute(JsonPointer path, @Nullable P param);

    /**
     * Invoked for the {@link ThingResource#FEATURE} resource.
     *
     * @param path the original resource path
     * @param param the parameter that is passed through
     * @return the result for the {@link ThingResource#FEATURE} resource.
     */
    R visitFeature(JsonPointer path, @Nullable P param);

    /**
     * Invoked for the {@link ThingResource#FEATURES} resource.
     *
     * @param path the original resource path
     * @param param the parameter that is passed through
     * @return the result for the {@link ThingResource#FEATURES} resource.
     */
    R visitFeatures(JsonPointer path, @Nullable P param);

    /**
     * Invoked for the {@link ThingResource#FEATURE_DEFINITION} resource.
     *
     * @param path the original resource path
     * @param param the parameter that is passed through
     * @return the result for the {@link ThingResource#FEATURE_DEFINITION} resource.
     */
    R visitFeatureDefinition(JsonPointer path, @Nullable P param);

    /**
     * Invoked for the {@link ThingResource#FEATURE_PROPERTIES} resource.
     *
     * @param path the original resource path
     * @param param the parameter that is passed through
     * @return the result for the {@link ThingResource#FEATURE_PROPERTIES} resource.
     */
    R visitFeatureProperties(JsonPointer path, @Nullable P param);

    /**
     * Invoked for the {@link ThingResource#FEATURE_PROPERTY} resource.
     *
     * @param path the original resource path
     * @param param the parameter that is passed through
     * @return the result for the {@link ThingResource#FEATURE_PROPERTY} resource.
     */
    R visitFeatureProperty(JsonPointer path, @Nullable P param);

    /**
     * Invoked for the {@link ThingResource#FEATURE_DESIRED_PROPERTIES} resource.
     *
     * @param path the original resource path
     * @param param the parameter that is passed through
     * @return the result for the {@link ThingResource#FEATURE_DESIRED_PROPERTIES} resource.
     */
    R visitFeatureDesiredProperties(JsonPointer path, @Nullable P param);

    /**
     * Invoked for the {@link ThingResource#FEATURE_DESIRED_PROPERTY} resource.
     *
     * @param path the original resource path
     * @param param the parameter that is passed through
     * @return the result for the {@link ThingResource#FEATURE_DESIRED_PROPERTY} resource.
     */
    R visitFeatureDesiredProperty(JsonPointer path, @Nullable P param);

    /**
     * Returns a {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException} for the case when the given
     * path could not be matched against the valid paths.
     *
     * @param path the path that could not be matched.
     * @return a {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException} indicating that the path was unknown
     */
    DittoRuntimeException getUnknownPathException(JsonPointer path);

    /**
     * Extract the feature property path from the given json pointer.
     *
     * @param path the path from which to extract the feature property path
     * @return the feature property path
     */
    default JsonPointer extractFeaturePropertyPath(final JsonPointer path) {
        return path.getSubPointer(3).orElseThrow(() -> getUnknownPathException(path));
    }

    /**
     * Extract the attribute path from the given json pointer.
     *
     * @param path the path from which to extract the attribute path
     * @return the attribute path
     */
    default JsonPointer extractAttributePath(final JsonPointer path) {
        return path.getSubPointer(1).orElseThrow(() -> getUnknownPathException(path));
    }

    /**
     * Extract the feature id from the given json pointer.
     *
     * @param path the path from which to extract the feature id
     * @return the feature id
     */
    default String extractFeatureId(final JsonPointer path) {
        return path.get(1).map(CharSequence::toString).orElseThrow(() -> getUnknownPathException(path));
    }

    /**
     * Extract the subject id from the given json pointer.
     *
     * @param path the path from which to extract the subject id
     * @return the subject id
     */
    default String extractSubjectId(final JsonPointer path) {
        return path.get(1).map(CharSequence::toString).orElseThrow(() -> getUnknownPathException(path));
    }

}
