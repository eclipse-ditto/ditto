/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.service.signaltransformation.placeholdersubstitution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.service.signaltransformer.placeholdersubstitution.SubstitutionStrategy;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyResource;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubject;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.edge.service.dispatching.signaltransformer.placeholdersubstitution.policies.PolicySubstitutionStrategyRegistry}.
 */
public class PolicySubstitutionStrategyRegistryTest {

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.empty();

    private PolicySubstitutionStrategyRegistry underTest;

    @Before
    public void init() {
        underTest = PolicySubstitutionStrategyRegistry.newInstance();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(PolicySubstitutionStrategyRegistry.class, areImmutable(),
                provided(SubstitutionStrategy.class).isAlsoImmutable(),
                assumingFields("strategies").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void getMatchingStrategyReturnsEmptyOptionalWhenNoStrategyMatches() {
        final ModifyResource nonHandledCommand = ModifyResource.of(PolicyId.of("org.eclipse.ditto:my-thing"),
                Label.of("foo"), Resource.newInstance(ResourceKey.newInstance("policy:/"),
                        EffectedPermissions.newInstance(null, null)), DITTO_HEADERS);

        final Optional<SubstitutionStrategy<? extends Signal<?>>> strategy =
                underTest.getMatchingStrategy(nonHandledCommand);
        assertThat(strategy).isEmpty();
    }

    @Test
    public void getMatchingStrategyReturnsStrategyWhenStrategyMatches() {
        final ModifySubject commandWithoutPlaceholders = ModifySubject.of(PolicyId.of("org.eclipse.ditto:my-policy"),
                Label.of("my-label"), Subject.newInstance("my-issuer:my-id", SubjectType.GENERATED),
                DITTO_HEADERS);

        final Optional<SubstitutionStrategy<? extends Signal<?>>> strategy =
                underTest.getMatchingStrategy(commandWithoutPlaceholders);
        assertThat(strategy).isPresent();
        assertThat(strategy.get()).isInstanceOf(ModifySubjectSubstitutionStrategy.class);
    }

    @Test
    public void allSubstitutionStrategiesHaveBeenConfigured() throws UnsupportedEncodingException {
        final List<SubstitutionStrategy<?>> strategies = underTest.getStrategies();
        final List<Class<? extends SubstitutionStrategy>> actualStrategyClasses =
                strategies.stream().map(SubstitutionStrategy::getClass).collect(Collectors.toList());

        final List<Class<? extends SubstitutionStrategy>> expectedStrategyClasses = getAllStrategyClasses();
        assertThat(actualStrategyClasses).hasSameElementsAs(expectedStrategyClasses);
    }

    private static List<Class<? extends SubstitutionStrategy>> getAllStrategyClasses()
            throws UnsupportedEncodingException {
        final String packageName = PolicySubstitutionStrategyRegistry.class.getPackage().getName();
        final List<Class> allClasses = getClasses(packageName);


        return allClasses.stream()
                .filter(SubstitutionStrategy.class::isAssignableFrom)
                .filter(clazz -> !Modifier.isAbstract(clazz.getModifiers()))
                .map(clazz -> {
                    @SuppressWarnings("unchecked") final Class<? extends SubstitutionStrategy> castedClazz =
                            (Class<? extends SubstitutionStrategy>) clazz;
                    return castedClazz;
                })
                .collect(Collectors.toList());
    }

    private static List<Class> getClasses(final String packageName) throws UnsupportedEncodingException {
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
            final String decodedResource = URLDecoder.decode(resource.getFile(), StandardCharsets.UTF_8.name());
            dirs.add(new File(decodedResource));
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
