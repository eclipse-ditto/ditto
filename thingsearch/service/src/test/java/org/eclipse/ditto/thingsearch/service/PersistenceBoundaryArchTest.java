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
package org.eclipse.ditto.thingsearch.service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.BeforeClass;
import org.junit.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Persistence boundary of the thingsearch service: its MAIN code depends on no MongoDB type, no Mongo persistence
 * module, no Mongo search backend and no R2DBC or Postgres type. The service reaches its search backend only through
 * the thing-search persistence-API ({@code thingsearch/persistence-api}); the MongoDB search backend lives in
 * {@code thingsearch/persistence-mongo}, which the service only has at {@code runtime} scope.
 * <p>
 * Driven via ArchUnit's core API from JUnit 4 {@code @Test} methods so the module keeps running on its existing
 * JUnit 4 / surefire setup; the imported classes are scoped to MAIN code via
 * {@link com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests}.
 *
 * @since 4.0.0
 */
public final class PersistenceBoundaryArchTest {

    private static final DescribedPredicate<JavaClass> IS_POSTGRES =
            new DescribedPredicate<>("be a Postgres type (io.r2dbc.., org.postgresql.., "
                    + "or org.eclipse.ditto.internal.utils.persistence.postgres..)") {
                @Override
                public boolean test(final JavaClass javaClass) {
                    final String pkg = javaClass.getPackageName();
                    return pkg.equals("io.r2dbc") || pkg.startsWith("io.r2dbc.")
                            || pkg.equals("org.postgresql") || pkg.startsWith("org.postgresql.")
                            || pkg.equals("org.eclipse.ditto.internal.utils.persistence.postgres")
                            || pkg.startsWith("org.eclipse.ditto.internal.utils.persistence.postgres.");
                }
            };

    private static JavaClasses thingsearchServiceMainClasses;

    @BeforeClass
    public static void importMainClasses() {
        thingsearchServiceMainClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("org.eclipse.ditto.thingsearch.service");
    }

    /**
     * Rule 2: no thingsearch-service MAIN class may depend on a Postgres type. Thingsearch is "service code"; it must
     * not couple to a backend, it reaches the backend through the persistence-API.
     */
    @Test
    public void thingsearchMustNotDependOnPostgres() {
        noClasses()
                .should().dependOnClassesThat(IS_POSTGRES)
                .as("thingsearch-service MAIN code must not depend on any Postgres type")
                .because("service code must not couple to a backend; it reaches it through the persistence-API")
                .check(thingsearchServiceMainClasses);
    }

    /**
     * No thingsearch-service MAIN class may depend on a MongoDB or R2DBC type, on the Mongo persistence module or on
     * the Mongo search backend: service code reaches the search backend only through the persistence-API.
     */
    @Test
    public void serviceMainCodeDoesNotDependOnMongo() {
        noClasses()
                .should().dependOnClassesThat().resideInAnyPackage("com.mongodb..", "org.bson..", "org.mongodb..",
                        "pekko.contrib.persistence.mongodb..", "org.eclipse.ditto.internal.utils.persistence.mongo..",
                        "org.eclipse.ditto.thingsearch.persistence.mongo..", "io.r2dbc..")
                .as("thingsearch-service MAIN code must not depend on MongoDB, R2DBC, the Mongo module "
                        + "(org.eclipse.ditto.internal.utils.persistence.mongo..) or the Mongo search backend "
                        + "(org.eclipse.ditto.thingsearch.persistence.mongo..)")
                .because("service code must reach the search backend only through the persistence-API, so that "
                        + "the backend can be switched without touching the service")
                .check(thingsearchServiceMainClasses);
    }
}
