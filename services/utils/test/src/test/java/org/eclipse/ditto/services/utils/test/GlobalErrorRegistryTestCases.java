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
package org.eclipse.ditto.services.utils.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.concurrent.Immutable;

import org.atteo.classindex.ClassIndex;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableException;
import org.junit.Test;

@Immutable
public abstract class GlobalErrorRegistryTestCases {

    private final List<Class<?>> samples;

    /**
     * Creates a new instance of test cases.
     *
     * @param samples a List of classes that are annotated with {@link JsonParsableException}. This list should contain
     * at least one exception of each package containing an Exception in the classpath of the respective service.
     */
    protected GlobalErrorRegistryTestCases(final List<Class<?>> samples) {
        this.samples = samples;
    }

    /**
     * This test should verify that all modules containing exceptions that need to be deserialized
     * in this service are still in the classpath. Therefore one sample of each module is placed in {@code samples}.
     */
    @Test
    public void sampleCheckForExceptionFromEachModule() {
        assertThat(ClassIndex.getAnnotated(JsonParsableException.class)).containsAll(samples);
    }

    /**
     * This test should enforce to keep {@code samples} up to date.
     * If this test fails add an exception of each missing package to samples.
     */
    @Test
    public void ensureSamplesAreComplete() {
        final Set<Package> packagesOfAnnotated =
                getPackagesDistinct(ClassIndex.getAnnotated(JsonParsableException.class));
        final Set<Package> packagesOfSamples = getPackagesDistinct(samples);

        assertThat(packagesOfSamples).containsAll(packagesOfAnnotated);
    }

    private Set<Package> getPackagesDistinct(Iterable<Class<?>> classes) {
        return StreamSupport.stream(classes.spliterator(), false)
                .map(Class::getPackage)
                .collect(Collectors.toSet());
    }

    @Test
    public void allExceptionsRegistered() {
        final Iterable<Class<? extends DittoRuntimeException>> subclassesOfDRE =
                ClassIndex.getSubclasses(DittoRuntimeException.class);

        for (Class<? extends DittoRuntimeException> subclassOfDRE : subclassesOfDRE) {
            if (DittoJsonException.class.equals(subclassOfDRE)) {
                //DittoJsonException is another concept than the rest of DittoRuntimeExceptions
                continue;
            }

            assertThat(subclassOfDRE.isAnnotationPresent(JsonParsableException.class))
                    .as("Check that '%s' is annotated with JsonParsableException.", subclassOfDRE.getName())
                    .isTrue();
        }
    }

    @Test
    public void allRegisteredExceptionsContainAMethodToParseFromJson() throws NoSuchMethodException {
        final Iterable<Class<?>> jsonParsableExceptions = ClassIndex.getAnnotated(JsonParsableException.class);

        for (Class<?> jsonParsableException : jsonParsableExceptions) {
            final JsonParsableException annotation = jsonParsableException.getAnnotation(JsonParsableException.class);
            assertAnnotationIsValid(annotation, jsonParsableException);
        }
    }

    private void assertAnnotationIsValid(JsonParsableException annotation, Class<?> cls) throws NoSuchMethodException {
        assertThat(cls.getMethod(annotation.method(), JsonObject.class, DittoHeaders.class))
                .as("Check that JsonParsableException of '%s' has correct methodName.", cls.getName())
                .isNotNull();
    }
}
