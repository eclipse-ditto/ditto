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
import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.atteo.classindex.ClassIndex;

/**
 * Responsible for collecting all {@link AnnotationBasedJsonParsable} for subclasses of T.
 *
 * @param <T> The superclass of all classes that should be deserialized by this registry.
 * @param <A> The type of the annotation that holds the information to build an annotation based json parsable.
 */
public abstract class AbstractGlobalJsonParsableRegistry<T, A extends Annotation>
        extends AbstractJsonParsableRegistry<T> {

    /**
     * Creates a new instance.
     *
     * @param parsedClass the superclass of all classes that should be deserialized by this registry.
     * @param annotationClass the type of the annotation that holds the information to build an annotation based
     * json parsable.
     * @param annotationBasedJsonParsableFactory the factory used to create {@link AnnotationBasedJsonParsable}
     * based on a given annotation.
     */
    protected AbstractGlobalJsonParsableRegistry(
            final Class<?> parsedClass,
            final Class<A> annotationClass,
            final AbstractAnnotationBasedJsonParsableFactory<T, A> annotationBasedJsonParsableFactory) {

        super(initAnnotationBasedParseStrategies(parsedClass, annotationClass, annotationBasedJsonParsableFactory));
    }

    /**
     * Creates a new instance.
     *
     * @param parsedClass the superclass of all classes that should be deserialized by this registry.
     * @param annotationClass the type of the annotation that holds the information to build an annotation based
     * json parsable.
     * @param annotationBasedJsonParsableFactory the factory used to create {@link AnnotationBasedJsonParsable}
     * based on a given annotation.
     * @param parseStrategies individual strategies that should be added to the annotation based strategies.
     * Annotation based strategies will be overridden if they have the same key.
     */
    protected AbstractGlobalJsonParsableRegistry(
            final Class<?> parsedClass,
            final Class<A> annotationClass,
            final AbstractAnnotationBasedJsonParsableFactory<T, A> annotationBasedJsonParsableFactory,
            final Map<String, JsonParsable<T>> parseStrategies) {

        super(mergeParsingStrategies(
                initAnnotationBasedParseStrategies(parsedClass, annotationClass, annotationBasedJsonParsableFactory),
                parseStrategies)
        );
    }

    private static <T> Map<String, JsonParsable<T>> mergeParsingStrategies(
            final Map<String, JsonParsable<T>> annotationBasedStrategies,
            final Map<String, JsonParsable<T>> otherStrategies) {

        final HashMap<String, JsonParsable<T>> mergedStrategies = new HashMap<>();
        mergedStrategies.putAll(annotationBasedStrategies);
        mergedStrategies.putAll(otherStrategies);
        return mergedStrategies;
    }

    @SuppressWarnings("unchecked") //Suppressed because the cast of cls is ensured by the two filters before.
    private static <T, A extends Annotation> Map<String, JsonParsable<T>> initAnnotationBasedParseStrategies(
            final Class<?> baseClass,
            final Class<A> annotationClass,
            final AbstractAnnotationBasedJsonParsableFactory<T, A> annotationBasedJsonParsableFactory) {

        final Map<String, JsonParsable<T>> parseRegistries = new HashMap<>();
        final Iterable<Class<?>> annotatedClasses = ClassIndex.getAnnotated(annotationClass);

        StreamSupport.stream(annotatedClasses.spliterator(), false)
                .filter(baseClass::isAssignableFrom)
                .filter(cls -> !baseClass.equals(cls))
                .map(cls -> (Class<? extends T>) cls)
                .forEach(classToParse -> {
                    final A fromJsonAnnotation = classToParse.getAnnotation(annotationClass);

                    final AnnotationBasedJsonParsable<T> strategy =
                            annotationBasedJsonParsableFactory.fromAnnotation(fromJsonAnnotation, classToParse);
                    parseRegistries.put(strategy.getKey(), strategy);
                });

        return parseRegistries;
    }

}
