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
package org.eclipse.ditto.internal.utils.persistence.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.BeforeClass;
import org.junit.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Rule 3 of the pluggable-persistence boundary rules: the persistence-API
 * ({@code org.eclipse.ditto.internal.utils.persistence.api..}) is backend-neutral. It must reference NEITHER backend:
 * no Mongo (com.mongodb / org.bson / the legacy {@code pekko.contrib.persistence.mongodb} plugin / MongoReadJournal)
 * AND no Postgres (io.r2dbc / org.postgresql / the {@code ...persistence.postgres} backend package).
 * <p>
 * Hosted in the persistence-api module itself: that module has NO backend dependency on its classpath, which is
 * exactly why it is the cleanest place to assert neutrality.
 * <p>
 * Driven via ArchUnit's core API from JUnit 4 {@code @Test} methods so the module keeps running on its existing
 * JUnit 4 / surefire setup. The imported classes are scoped to MAIN code via
 * {@link com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests}.
 *
 * @since 4.0.0
 */
public final class PersistenceApiNeutralityArchTest {

    private static final String API_PKG = "org.eclipse.ditto.internal.utils.persistence.api..";

    private static final DescribedPredicate<JavaClass> IS_MONGO =
            new DescribedPredicate<>("be a Mongo type (com.mongodb.., org.bson.., "
                    + "pekko.contrib.persistence.mongodb.., or named MongoReadJournal)") {
                @Override
                public boolean test(final JavaClass javaClass) {
                    final String pkg = javaClass.getPackageName();
                    return pkg.equals("com.mongodb") || pkg.startsWith("com.mongodb.")
                            || pkg.equals("org.bson") || pkg.startsWith("org.bson.")
                            || pkg.equals("pekko.contrib.persistence.mongodb")
                            || pkg.startsWith("pekko.contrib.persistence.mongodb.")
                            || javaClass.getSimpleName().equals("MongoReadJournal");
                }
            };

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

    private static JavaClasses persistenceApiMainClasses;

    @BeforeClass
    public static void importMainClasses() {
        persistenceApiMainClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("org.eclipse.ditto.internal.utils.persistence.api");
    }

    /** Rule 3a: persistence-api references no Mongo type. */
    @Test
    public void persistenceApiMustNotDependOnMongo() {
        noClasses()
                .that().resideInAPackage(API_PKG)
                .should().dependOnClassesThat(IS_MONGO)
                .as("persistence-api (" + API_PKG + ") must not depend on any Mongo type")
                .because("the persistence-API is the backend-neutral seam of the pluggable-persistence layer")
                .check(persistenceApiMainClasses);
    }

    /** Rule 3b: persistence-api references no Postgres type. */
    @Test
    public void persistenceApiMustNotDependOnPostgres() {
        noClasses()
                .that().resideInAPackage(API_PKG)
                .should().dependOnClassesThat(IS_POSTGRES)
                .as("persistence-api (" + API_PKG + ") must not depend on any Postgres type")
                .because("the persistence-API is the backend-neutral seam of the pluggable-persistence layer")
                .check(persistenceApiMainClasses);
    }
}
