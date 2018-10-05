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
package org.eclipse.ditto.services.concierge.enforcement.placeholders.strategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubject;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests {@link SubstitutionStrategyRegistry}.
 */
public class SubstitutionStrategyRegistryTest {

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.empty();

    private SubstitutionStrategyRegistry underTest;

    @Before
    public void init() {
        underTest = SubstitutionStrategyRegistry.newInstance();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(SubstitutionStrategyRegistry.class, areImmutable(),
                provided(SubstitutionStrategy.class).isAlsoImmutable());
    }

    @Test
    public void getMatchingStrategyReturnsEmptyOptionalWhenNoStrategyMatches() {
        final ModifyAttribute nonHandledCommand = ModifyAttribute.of("org.eclipse.ditto:my-thing",
                JsonPointer.of("attributePointer"), JsonValue.of("attributeValue"), DITTO_HEADERS);

        final Optional<SubstitutionStrategy> strategy = underTest.getMatchingStrategy(nonHandledCommand);
        assertThat(strategy).isEmpty();
    }

    @Test
    public void getMatchingStrategyReturnsStrategyWhenStrategyMatches() {
        final ModifySubject commandWithoutPlaceholders = ModifySubject.of("org.eclipse.ditto:my-policy",
                Label.of("my-label"), Subject.newInstance("my-issuer:my-id", SubjectType.GENERATED),
                DITTO_HEADERS);

        final Optional<SubstitutionStrategy> strategy = underTest.getMatchingStrategy(commandWithoutPlaceholders);
        assertThat(strategy).isPresent();
        assertThat(strategy.get()).isInstanceOf(ModifySubjectSubstitutionStrategy.class);
    }

    @Test
    public void allSubstitutionStrategiesHaveBeenConfigured() {
        final List<SubstitutionStrategy> strategies = underTest.getStrategies();
        final List<Class<? extends SubstitutionStrategy>> actualStrategyClasses =
                strategies.stream().map(SubstitutionStrategy::getClass).collect(Collectors.toList());

        final List<Class<? extends SubstitutionStrategy>> expectedStrategyClasses = getAllStrategyClasses();
        assertThat(actualStrategyClasses).hasSameElementsAs(expectedStrategyClasses);
    }
    private static List<Class<? extends SubstitutionStrategy>> getAllStrategyClasses() {
        final String packageName = SubstitutionStrategyRegistry.class.getPackage().getName();
        final List<Class> allClasses = getClasses(packageName);


        return allClasses.stream()
                .filter(SubstitutionStrategy.class::isAssignableFrom)
                .filter(clazz -> !Modifier.isAbstract(clazz.getModifiers()))
                .map(clazz -> {
                    @SuppressWarnings("unchecked")
                    final Class<? extends SubstitutionStrategy> castedClazz =
                            (Class<? extends SubstitutionStrategy>) clazz;
                    return castedClazz;
                })
                .collect(Collectors.toList());
    }

    private static List<Class> getClasses(final String packageName) {
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        final String path = packageName.replace('.', '/');
        final Enumeration<URL> resources;
        try {
            resources = classLoader.getResources(path);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }

        final List<File> dirs = new ArrayList<>();
        while (resources.hasMoreElements()) {
            final URL resource = resources.nextElement();
            dirs.add(new File(resource.getFile()));
        }

        final List<Class> classes = new ArrayList<>();
        for (final File directory : dirs) {
            classes.addAll(findClasses(directory, packageName));
        }
        return classes;
    }


    private static List<Class> findClasses(File directory, String packageName) {
        final List<Class> classes = new ArrayList<>();
        if (!directory.exists()) {
            return classes;
        }

        final File[] files = directory.listFiles();
        for (final File file : files) {
            if (file.isDirectory()) {
                classes.addAll(findClasses(file, packageName + "." + file.getName()));
            } else {
                final String classFileEnding = ".class";
                if (file.getName().endsWith(classFileEnding)) {
                    final String simpleClassName =
                            file.getName().substring(0, file.getName().length() - classFileEnding.length());
                    final String className = packageName + '.' + simpleClassName;
                    try {
                        classes.add(Class.forName(className));
                    } catch (final ClassNotFoundException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }

        return classes;
    }
}
