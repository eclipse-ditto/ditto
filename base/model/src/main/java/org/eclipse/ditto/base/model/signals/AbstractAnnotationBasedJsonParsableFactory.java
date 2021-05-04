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
package org.eclipse.ditto.base.model.signals;

import java.lang.annotation.Annotation;

/**
 * Responsible to build an {@link AnnotationBasedJsonParsable} from a given annotation.
 *
 * @param <T> The superclass of the class that should be parsed by an annotation based json parsable created by this
 * factory.
 * @param <A> The type of the annotation that holds the information to build an annotation based json parsable.
 */
public abstract class AbstractAnnotationBasedJsonParsableFactory<T, A extends Annotation> {

    /**
     * Builds an {@link AnnotationBasedJsonParsable} from the given annotation.
     *
     * @param annotation the annotation that holds the information to build an annotation based json parsable.
     * @param classToParse the class that should be deserialized.
     * @return the annotation based json parsable.
     */
    AnnotationBasedJsonParsable<T> fromAnnotation(final A annotation, final Class<? extends T> classToParse) {
        final String methodName = getMethodNameFor(annotation);
        final String key = getKeyFor(annotation);

        return new AnnotationBasedJsonParsable<>(key, classToParse, methodName);
    }

    /**
     * The key for v2 deserialization strategies.
     *
     * @param annotation the annotation that holds the information to build an annotation based json parsable.
     * @return the key for v2 deserialization strategies.
     */
    protected abstract String getKeyFor(A annotation);

    /**
     * The name of the method used for deserialization.
     *
     * @param annotation the annotation that holds the information to build an annotation based json parsable.
     * @return the name of the method used for deserialization.
     */
    protected abstract String getMethodNameFor(A annotation);
}
