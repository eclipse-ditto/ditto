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
package org.eclipse.ditto.internal.utils.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.concurrent.Immutable;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.atteo.classindex.ClassIndex;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

@Immutable
public abstract class GlobalErrorRegistryTestCases {

    private final List<Class<?>> samples;

    /**
     * Creates a new instance of test cases.
     *
     * @param sample a class that is annotated with {@link JsonParsableException}. This list should contain
     * at least one exception of each package containing an Exception in the classpath of the respective service.
     * @param furtherSamples further classes that are annotated with {@link JsonParsableException}.
     */
    protected GlobalErrorRegistryTestCases(final Class<?> sample, final Class<?>... furtherSamples) {
        samples = new ArrayList<>(1 + furtherSamples.length);
        samples.add(sample);
        Collections.addAll(samples, furtherSamples);
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
        final var packagesOfAnnotated = getPackagesDistinct(ClassIndex.getAnnotated(JsonParsableException.class));
        final var packagesOfSamples = getPackagesDistinct(samples);

        assertThat(packagesOfSamples).containsAll(packagesOfAnnotated);
    }

    private static Set<Package> getPackagesDistinct(final Iterable<Class<?>> classes) {
        return StreamSupport.stream(classes.spliterator(), false)
                .map(Class::getPackage)
                .collect(Collectors.toSet());
    }

    @Test
    public void allExceptionsRegistered() {
        try (final var softly = new AutoCloseableSoftAssertions()) {

            // DittoJsonException is another concept than the rest of DittoRuntimeExceptions
            subclassesOfDittoRuntimeException()
                    .filter(subclass -> DittoJsonException.class != subclass)
                    .filter(subclass -> !Modifier.isAbstract(subclass.getModifiers()))
                    .forEach(subclass -> softly.assertThat(subclass.isAnnotationPresent(JsonParsableException.class))
                            .as("Check that '%s' is annotated with 'JsonParsableException'.", subclass.getName())
                            .isTrue());
        }
    }

    private static Stream<Class<? extends DittoRuntimeException>> subclassesOfDittoRuntimeException() {
        final var subclasses = ClassIndex.getSubclasses(DittoRuntimeException.class);
        return StreamSupport.stream(subclasses.spliterator(), false);
    }

    @Test
    public void allRegisteredExceptionsContainAMethodToParseFromJson() throws NoSuchMethodException {
        final var jsonParsableExceptions = ClassIndex.getAnnotated(JsonParsableException.class);

        try (final var softly = new AutoCloseableSoftAssertions()) {
            for (final var jsonParsableException : jsonParsableExceptions) {
                final var annotation = jsonParsableException.getAnnotation(JsonParsableException.class);
                final var method =
                        jsonParsableException.getMethod(annotation.method(), JsonObject.class, DittoHeaders.class);

                softly.assertThat(method)
                        .as("Check that '%s' has correct methodName.", jsonParsableException.getName())
                        .isNotNull();
            }
        }
    }

}
