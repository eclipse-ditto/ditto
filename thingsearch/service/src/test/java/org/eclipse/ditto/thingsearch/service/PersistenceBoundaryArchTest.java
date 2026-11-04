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
 * Rule 1 (no Mongo outside the Mongo zone) and Rule 2 (no Postgres in service code) of the pluggable-persistence
 * switchability proof (layer 1), applied to the thingsearch service.
 * <p>
 * Per Rule 1, the Mongo zone is {@code ...internal.utils.persistence.mongo..} AND the thingsearch search-index
 * packages: the Ditto search index <em>is</em> MongoDB by design, so the thingsearch read/write/update/config
 * packages that implement it are an explicit, allowed part of the Mongo zone. This test pins down which packages
 * those are and asserts that Mongo usage does NOT leak into any OTHER thingsearch package.
 * <p>
 * Driven via ArchUnit's core API from JUnit 4 {@code @Test} methods so the module keeps running on its existing
 * JUnit 4 / surefire setup; the imported classes are scoped to MAIN code via
 * {@link com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests}.
 *
 * @since 4.0.0
 */
public final class PersistenceBoundaryArchTest {

    /**
     * The thingsearch search-index packages - the part of the Mongo zone that legitimately implements the
     * Mongo-backed search index. Determined from the actual Mongo-coupled MAIN classes of this module.
     */
    private static final String[] SEARCH_INDEX_PACKAGES = {
            "org.eclipse.ditto.thingsearch.service.persistence..",
            "org.eclipse.ditto.thingsearch.service.common.config..",
            "org.eclipse.ditto.thingsearch.service.starter.actors..",
            "org.eclipse.ditto.thingsearch.service.updater.actors..",
    };

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

    private static JavaClasses thingsearchServiceMainClasses;

    @BeforeClass
    public static void importMainClasses() {
        thingsearchServiceMainClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("org.eclipse.ditto.thingsearch.service");
    }

    /**
     * Rule 1: Mongo usage in the thingsearch service must stay WITHIN the search-index packages. Any thingsearch
     * MAIN class outside those packages that depends on a Mongo type is a leak of the Mongo zone.
     */
    @Test
    public void mongoUsageStaysWithinSearchIndexPackages() {
        noClasses()
                .that().resideOutsideOfPackages(SEARCH_INDEX_PACKAGES)
                .should().dependOnClassesThat(IS_MONGO)
                .as("thingsearch-service MAIN code outside the search-index packages "
                        + "must not depend on any Mongo type")
                .because("the Mongo-backed search index is confined to the thingsearch search-index packages; "
                        + "Mongo must not leak elsewhere")
                .check(thingsearchServiceMainClasses);
    }

    /**
     * Rule 2: no thingsearch-service MAIN class may depend on a Postgres type. Thingsearch is "service code"; its
     * backend is Mongo (the search index) - it must never couple to Postgres.
     */
    @Test
    public void thingsearchMustNotDependOnPostgres() {
        noClasses()
                .should().dependOnClassesThat(IS_POSTGRES)
                .as("thingsearch-service MAIN code must not depend on any Postgres type")
                .because("the thingsearch search index is Mongo-only; it must never couple to Postgres")
                .check(thingsearchServiceMainClasses);
    }
}
