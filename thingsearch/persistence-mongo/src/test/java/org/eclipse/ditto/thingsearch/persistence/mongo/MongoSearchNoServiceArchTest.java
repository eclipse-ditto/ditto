/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.persistence.mongo;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.BeforeClass;
import org.junit.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Extension boundary of the MongoDB search backend: no class of
 * {@code org.eclipse.ditto.thingsearch.persistence.mongo..} may depend on the thingsearch service
 * ({@code org.eclipse.ditto.thingsearch.service..}). The backend implements the thing-search persistence-API and
 * must be shippable as an extension JAR without the service.
 * <p>
 * Driven via ArchUnit's core API from JUnit 4 {@code @Test} methods so the module keeps running on its existing
 * JUnit 4 / surefire setup. The imported classes are scoped to MAIN code via
 * {@link com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests}.
 */
public final class MongoSearchNoServiceArchTest {

    private static final String MONGO_SEARCH_PKG = "org.eclipse.ditto.thingsearch.persistence.mongo..";
    private static final String SEARCH_SERVICE_PKG = "org.eclipse.ditto.thingsearch.service..";

    private static JavaClasses mongoSearchMainClasses;

    @BeforeClass
    public static void importMainClasses() {
        mongoSearchMainClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("org.eclipse.ditto.thingsearch.persistence.mongo");
    }

    /** The MongoDB search backend references no class of the thingsearch service. */
    @Test
    public void mongoSearchBackendDoesNotDependOnSearchService() {
        noClasses()
                .that().resideInAPackage(MONGO_SEARCH_PKG)
                .should().dependOnClassesThat().resideInAPackage(SEARCH_SERVICE_PKG)
                .as("the MongoDB search backend (" + MONGO_SEARCH_PKG + ") must not depend on the thingsearch "
                        + "service (" + SEARCH_SERVICE_PKG + ")")
                .because("the backend must ship as an extension JAR that plugs into the search service through "
                        + "the persistence-API, without the service on its classpath")
                .check(mongoSearchMainClasses);
    }
}
