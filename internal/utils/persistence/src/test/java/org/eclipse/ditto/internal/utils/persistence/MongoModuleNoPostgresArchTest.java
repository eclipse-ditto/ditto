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
package org.eclipse.ditto.internal.utils.persistence;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.BeforeClass;
import org.junit.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Rule 2 of the pluggable-persistence switchability proof (layer 1) applied to the bundled Mongo module
 * ({@code org.eclipse.ditto.internal.utils.persistence..}, which contains the Mongo zone {@code ...mongo..}): the
 * Mongo module must not depend on Postgres (no io.r2dbc / org.postgresql / the {@code ...persistence.postgres}
 * backend package). The two backends are mutually exclusive and pluggable; the Mongo module must never reach into
 * the Postgres backend.
 * <p>
 * Driven via ArchUnit's core API from JUnit 4 {@code @Test} methods so the module keeps running on its existing
 * JUnit 4 / surefire setup; the imported classes are scoped to MAIN code via
 * {@link com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests}.
 *
 * @since 4.0.0
 */
public final class MongoModuleNoPostgresArchTest {

    private static final String MONGO_MODULE_PKG = "org.eclipse.ditto.internal.utils.persistence";

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

    private static JavaClasses mongoModuleMainClasses;

    @BeforeClass
    public static void importMainClasses() {
        mongoModuleMainClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(MONGO_MODULE_PKG);
    }

    /** Rule 2 (Mongo module): the bundled Mongo module must not depend on any Postgres type. */
    @Test
    public void mongoModuleMustNotDependOnPostgres() {
        noClasses()
                .should().dependOnClassesThat(IS_POSTGRES)
                .as("the bundled Mongo module (" + MONGO_MODULE_PKG + "..) must not depend on any Postgres type")
                .because("the Mongo and Postgres backends are mutually exclusive and pluggable")
                .check(mongoModuleMainClasses);
    }
}
