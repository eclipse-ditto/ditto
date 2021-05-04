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
package org.eclipse.ditto.internal.utils.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.concurrent.Immutable;

import org.atteo.classindex.ClassIndex;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.signals.JsonParsable;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.junit.Before;
import org.junit.Test;
import org.mutabilitydetector.internal.javassist.Modifier;

@Immutable
public abstract class GlobalCommandRegistryTestCases {

    private final List<Class<?>> samples;
    private List<Class<?>> jsonParsableCommands;

    /**
     * Creates a new instance of test cases.
     *
     * @param samples a List of classes that are annotated with {@link JsonParsableCommand}. This list should contain
     * at least one command of each package containing a Command in the classpath of the respective service.
     */
    protected GlobalCommandRegistryTestCases(final List<Class<?>> samples) {
        this.samples = new ArrayList<>(samples);
    }

    /**
     * Creates a new instance of test cases.
     * The given samples should contain at least one command response of each package containing a CommandResponse in
     * the classpath of the respective service.
     *
     * @param sample a class that is annotated with {@link JsonParsableCommand}.
     * @param furtherSamples further classes that are annotated with {@link JsonParsableCommand}.
     */
    protected GlobalCommandRegistryTestCases(final Class<?> sample, final Class<?>... furtherSamples) {
        samples = new ArrayList<>(1 + furtherSamples.length);
        samples.add(sample);
        Collections.addAll(samples, furtherSamples);
    }

    @Before
    public void setup() {
        jsonParsableCommands =
                StreamSupport.stream(ClassIndex.getAnnotated(JsonParsableCommand.class).spliterator(), true)
                        .filter(c -> !c.getSimpleName().equals("TestCommand"))
                        .collect(Collectors.toList());
    }

    /**
     * Override this method to exclude certain classes.
     * By default, all classes starting with "Dummy" are excluded.
     *
     * @param clazz the class to check.
     * @return whether the class is excluded.
     */
    protected boolean isExcluded(final Class<?> clazz) {
        return clazz.getSimpleName().startsWith("Dummy");
    }

    /**
     * This test should verify that all modules containing commands that need to be deserialized
     * in this service are still in the classpath.
     * Therefore one sample of each module is placed in {@code samples}.
     */
    @Test
    public void sampleCheckForCommandFromEachModule() {
        assertThat(jsonParsableCommands).containsAll(samples);
    }

    /**
     * This test should enforce to keep {@code samples} up to date.
     * If this test fails add a command of each missing package to samples.
     */
    @Test
    public void ensureSamplesAreComplete() {
        final Set<Package> packagesOfAnnotated = getPackagesDistinct(jsonParsableCommands);
        final Set<Package> packagesOfSamples = getPackagesDistinct(samples);

        assertThat(packagesOfSamples).containsAll(packagesOfAnnotated);
    }

    private static Set<Package> getPackagesDistinct(final Iterable<Class<?>> classes) {
        return StreamSupport.stream(classes.spliterator(), false)
                .map(Class::getPackage)
                .collect(Collectors.toSet());
    }

    @Test
    public void allCommandsRegistered() {
        StreamSupport.stream(ClassIndex.getSubclasses(Command.class).spliterator(), true)
                .filter(c -> {
                    final int m = c.getModifiers();
                    return !(Modifier.isAbstract(m) || Modifier.isInterface(m) || isExcluded(c));
                })
                .forEach(c -> assertThat(c.isAnnotationPresent(JsonParsableCommand.class))
                        .as("Check that '%s' is annotated with JsonParsableCommand.", c.getName())
                        .isTrue());
    }

    @Test
    public void allRegisteredCommandsContainAMethodToParseFromJson() throws NoSuchMethodException {
        for (final Class<?> jsonParsableCommand : jsonParsableCommands) {
            final JsonParsableCommand annotation = jsonParsableCommand.getAnnotation(JsonParsableCommand.class);
            assertAnnotationIsValid(annotation, jsonParsableCommand);
        }
    }

    private static void assertAnnotationIsValid(final JsonParsableCommand annotation, final Class<?> cls)
            throws NoSuchMethodException {

        try {
            assertThat(cls.getMethod(annotation.method(), JsonObject.class, DittoHeaders.class,
                    JsonParsable.ParseInnerJson.class))
                    .as("Check that JsonParsableCommand of '%s' has correct method name.", cls.getName())
                    .isNotNull();
        } catch (final NoSuchMethodException e) {
            assertThat(cls.getMethod(annotation.method(), JsonObject.class, DittoHeaders.class))
                    .as("Check that JsonParsableCommand of '%s' has correct method name.", cls.getName())
                    .isNotNull();
        }
    }

}
