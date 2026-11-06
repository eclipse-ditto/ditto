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
package org.eclipse.ditto.internal.utils.persistence.postgres.search;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.BeforeClass;
import org.junit.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * ArchUnit closure for the Postgres search backend (boundary rule 3): NO class in
 * {@code search-r2dbc}'s MAIN code may depend on Mongo (com.mongodb / org.bson / the legacy
 * {@code pekko.contrib.persistence.mongodb} plugin / MongoReadJournal). This module IS the Postgres implementation of
 * the neutral {@code SearchPersistenceProvider} SPI (mirrors {@code ThingsearchPersistenceApiNeutralityArchTest} and
 * thingsearch-service's {@code PersistenceBoundaryArchTest}), so unlike those two neutral/mixed modules there is no
 * corresponding "must not depend on Postgres" rule here — depending on Postgres IS this module's entire purpose.
 * <p>
 * <strong>Scoping — MAIN sources only, test sources exempt (documented, not accidental).</strong> The classes are
 * imported via {@link ClassFileImporter} with {@link ImportOption.DoNotIncludeTests}, and only the module's OWN main
 * package tree ({@code org.eclipse.ditto.internal.utils.persistence.postgres.search..}) is imported — never
 * {@code org.eclipse.ditto.thingsearch.service..}. This matters concretely: {@code PostgresCursorResumeIT} (this
 * module's test tree, package {@code org.eclipse.ditto.thingsearch.service.starter.actors}) legitimately depends on
 * real {@code thingsearch-service} classes (a package-private {@code ThingsSearchCursor} round-trip proof against a
 * live Postgres) — that dependency is entirely legitimate FOR A TEST but would be a false positive if this rule ever
 * walked test sources. Excluding tests via {@code DoNotIncludeTests} (rather than, say, excluding that one class by
 * name) keeps the rule simple and automatically correct for any future test that needs the same kind of access.
 * <p>
 * Driven via ArchUnit's core API from JUnit 4 {@code @Test} methods so the module keeps running on its existing
 * JUnit 4 / surefire setup, exactly like the two precedent tests cited above.
 *
 * @since 4.0.0
 */
public final class PostgresSearchNoMongoArchTest {

    private static final String SEARCH_R2DBC_MAIN_PKG = "org.eclipse.ditto.internal.utils.persistence.postgres.search..";

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

    private static JavaClasses searchR2dbcMainClasses;

    @BeforeClass
    public static void importMainClasses() {
        searchR2dbcMainClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("org.eclipse.ditto.internal.utils.persistence.postgres.search");
    }

    /** No search-r2dbc MAIN class depends on any Mongo/BSON type. */
    @Test
    public void searchR2dbcMainCodeMustNotDependOnMongo() {
        noClasses()
                .that().resideInAPackage(SEARCH_R2DBC_MAIN_PKG)
                .should().dependOnClassesThat(IS_MONGO)
                .as("search-r2dbc MAIN code (" + SEARCH_R2DBC_MAIN_PKG + ") must not depend on any Mongo/BSON type")
                .because("search-r2dbc is the Postgres implementation of the neutral SearchPersistenceProvider SPI; "
                        + "it must never couple to the Mongo backend it replaces")
                .check(searchR2dbcMainClasses);
    }

}
