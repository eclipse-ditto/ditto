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
package org.eclipse.ditto.internal.utils.persistentactors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.BeforeClass;
import org.junit.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * ArchUnit boundary rules of the pluggable-persistence switchability proof (layer 1) applied to the
 * {@code persistent-actors} module.
 * <p>
 * This module sits below the service modules and pulls in both the Mongo zone
 * ({@code org.eclipse.ditto.internal.utils.persistence.mongo..}) and the legacy Pekko MongoDB persistence plugin
 * ({@code pekko.contrib.persistence.mongodb..}) on its test classpath via {@code ditto-internal-utils-persistence}.
 * It is therefore the natural host for:
 * <ul>
 *     <li>the <b>dependency rule</b> ({@code persistent-actors} main code must not depend on the Mongo zone), and</li>
 *     <li>the <b>meta-test</b> that proves the legacy {@code pekko.contrib.persistence.mongodb..} matcher actually
 *     matches the real {@code MongoReadJournal} plugin class (so Rule 1 is not silently matching nothing).</li>
 * </ul>
 * <p>
 * The imported classes are scoped to the persistent-actors package and to MAIN classes only
 * ({@link com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests}): the cleanup TEST fixtures in this
 * module still couple to {@code MongoReadJournal} (a known carry-forward from task A3); the production rule is about
 * MAIN code, so test fixtures must not trip it.
 * <p>
 * Driven via ArchUnit's core API from JUnit 4 {@code @Test} methods so the module keeps running on its existing
 * JUnit 4 / surefire setup.
 *
 * @since 4.0.0
 */
public final class PersistenceBoundaryArchTest {

    private static final String PERSISTENT_ACTORS_PKG = "org.eclipse.ditto.internal.utils.persistentactors";

    private static JavaClasses persistentActorsMainClasses;

    @BeforeClass
    public static void importMainClasses() {
        persistentActorsMainClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(PERSISTENT_ACTORS_PKG);
    }

    /**
     * Dependency rule: {@code persistent-actors} MAIN code must not depend on the Mongo zone
     * ({@code org.eclipse.ditto.internal.utils.persistence.mongo..}). This keeps the generic persistent-actor
     * machinery backend-neutral so it can run on either the Mongo or the Postgres backend.
     */
    @Test
    public void persistentActorsMustNotDependOnMongoZone() {
        noClasses()
                .that().resideInAPackage(PERSISTENT_ACTORS_PKG + "..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.eclipse.ditto.internal.utils.persistence.mongo..")
                .as("persistent-actors MAIN code must not depend on the Mongo zone "
                        + "(org.eclipse.ditto.internal.utils.persistence.mongo..)")
                .because("the generic persistent-actor machinery must stay backend-neutral (pluggable persistence)")
                .check(persistentActorsMainClasses);
    }

    /**
     * Rule 1 applied to {@code persistent-actors} MAIN code: it must not depend on Mongo at all
     * (neither the driver, BSON, the legacy plugin, nor the Ditto MongoReadJournal wrapper).
     */
    @Test
    public void persistentActorsMustNotDependOnMongo() {
        noClasses()
                .should().dependOnClassesThat(PersistenceBoundaryMatchers.IS_MONGO)
                .as("persistent-actors MAIN code must not depend on any Mongo type "
                        + "(com.mongodb.., org.bson.., pekko.contrib.persistence.mongodb.., MongoReadJournal)")
                .because("the generic persistent-actor machinery must stay backend-neutral (pluggable persistence)")
                .check(persistentActorsMainClasses);
    }

    /**
     * Rule 2 applied to {@code persistent-actors} MAIN code: it must not depend on Postgres either
     * (no io.r2dbc.., org.postgresql.., or the Postgres backend package).
     */
    @Test
    public void persistentActorsMustNotDependOnPostgres() {
        noClasses()
                .should().dependOnClassesThat(PersistenceBoundaryMatchers.IS_POSTGRES)
                .as("persistent-actors MAIN code must not depend on any Postgres type "
                        + "(io.r2dbc.., org.postgresql.., org.eclipse.ditto.internal.utils.persistence.postgres..)")
                .because("the generic persistent-actor machinery must stay backend-neutral (pluggable persistence)")
                .check(persistentActorsMainClasses);
    }

    /**
     * META-TEST. Proves that the legacy {@code pekko.contrib.persistence.mongodb..} package matcher used by Rule 1
     * actually matches the real {@code MongoReadJournal} plugin class, so the matcher is not silently matching
     * nothing (which would make Rule 1 pass vacuously).
     * <p>
     * Note the LEGACY prefix {@code pekko.} (NOT {@code org.apache.pekko.}): the {@code pekko-persistence-mongodb}
     * plugin still ships its classes under the historical {@code pekko.contrib.persistence.mongodb} namespace.
     */
    @Test
    public void legacyPekkoMongoMatcherMatchesMongoReadJournal() {
        // Import the real plugin class from the test classpath (brought in transitively via the Mongo zone module).
        final JavaClasses pluginClasses = new ClassFileImporter()
                .withImportOption(location -> true)
                .importPackages("pekko.contrib.persistence.mongodb");

        final JavaClass mongoReadJournal = pluginClasses.stream()
                .filter(c -> c.getFullName().equals("pekko.contrib.persistence.mongodb.MongoReadJournal"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected pekko.contrib.persistence.mongodb.MongoReadJournal to be present on the test "
                                + "classpath (pekko-persistence-mongodb plugin). It was not imported - the meta-test "
                                + "cannot prove the matcher hits."));

        // The exact predicate Rule 1 uses for the legacy plugin package must accept this real class.
        assertThat(PersistenceBoundaryMatchers.IS_LEGACY_PEKKO_MONGO.test(mongoReadJournal))
                .as("the 'pekko.contrib.persistence.mongodb..' matcher used by Rule 1 must match the real "
                        + "MongoReadJournal plugin class")
                .isTrue();

        // And the umbrella Mongo predicate (used by Rule 1 / Rule 3) must match it too.
        assertThat(PersistenceBoundaryMatchers.IS_MONGO.test(mongoReadJournal))
                .as("the umbrella Mongo matcher must also match the real MongoReadJournal plugin class")
                .isTrue();
    }
}
