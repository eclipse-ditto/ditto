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
package org.eclipse.ditto.connectivity.service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper;
import org.junit.BeforeClass;
import org.junit.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Rule 1 (no <em>switchable</em> Mongo artifact outside the Mongo zone) and Rule 2 (no Postgres in service code) of
 * the pluggable-persistence boundary rules, applied to the connectivity service.
 * <p>
 * Connectivity does its persistence through the backend-neutral persistence-API. The refactor routed the
 * <em>switchable</em> Mongo touchpoints (the read-journal, the {@code pekko-contrib-mongodb-persistence} plugin
 * classes and the raw Mongo driver / BSON) through the provider, so service code must no longer reference them.
 * Rule 1 therefore bans only those switchable artifacts - NOT the whole Mongo zone:
 * <ul>
 *     <li>{@code com.mongodb..} and {@code org.bson..} (the raw Mongo driver and BSON),</li>
 *     <li>{@code pekko.contrib.persistence.mongodb..} (the legacy Mongo persistence plugin classes; note the
 *     {@code pekko.} prefix, NOT {@code org.apache.pekko.}), and</li>
 *     <li>any class named {@code MongoReadJournal} (now obtained via {@code provider.getReadJournal()}).</li>
 * </ul>
 * It deliberately does NOT ban {@code ...internal.utils.persistence.mongo.AbstractMongoEventAdapter} or
 * {@code ...internal.utils.persistence.mongo.config..}: those are the INTENTIONAL bundled-Mongo default (the
 * per-service Mongo event-adapter base and the Mongo connection config the service needs to actually run on
 * MongoDB, which is the bundled default backend) - symmetric to the Postgres event adapters, not a switchable leak.
 * <p>
 * There is no whitelist. The connection encryption migration ({@code EncryptionMigrationActor}), which used to read and
 * rewrite the MongoDB journal and snapshot collections with the raw driver and BSON, now reaches the persisted payloads
 * through the provider's backend-neutral {@code PersistedPayloadStore}; the MongoDB statements live in the Mongo zone
 * ({@code ...internal.utils.persistence.mongo.payload}), and the store owns the Mongo client the migration used to
 * build itself. No connectivity MAIN class may therefore depend on a switchable Mongo artifact (Rule 1) or build the
 * Mongo-zone client (Rule 1b).
 * <p>
 * Driven via ArchUnit's core API from JUnit 4 {@code @Test} methods so the module keeps running on its existing
 * JUnit 4 / surefire setup; the imported classes are scoped to MAIN code via
 * {@link com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests}.
 *
 * @since 4.0.0
 */
public final class PersistenceBoundaryArchTest {

    /**
     * The SWITCHABLE Mongo artifacts the pluggable-persistence refactor routed through the provider. Service code
     * must not reference these directly. Deliberately does NOT match {@code AbstractMongoEventAdapter} or the
     * {@code ...persistence.mongo.config..} types - those are the intentional bundled-Mongo default.
     */
    private static final DescribedPredicate<JavaClass> IS_SWITCHABLE_MONGO =
            new DescribedPredicate<>("be a switchable Mongo artifact (com.mongodb.., org.bson.., "
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

    private static final String MONGO_CLIENT_WRAPPER =
            "org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper";
    private static final String DITTO_MONGO_CLIENT =
            "org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClient";

    /**
     * The Mongo-zone client. It is not a switchable artifact (Rule 1 does not match it), but building one opens a
     * MongoDB connection pool, which only the Mongo zone itself does - behind the provider's accessors.
     */
    private static final DescribedPredicate<JavaClass> IS_MONGO_CLIENT =
            new DescribedPredicate<>("be the Mongo-zone client (MongoClientWrapper or DittoMongoClient)") {
                @Override
                public boolean test(final JavaClass javaClass) {
                    final String name = javaClass.getFullName();
                    return name.equals(MONGO_CLIENT_WRAPPER) || name.equals(DITTO_MONGO_CLIENT);
                }
            };

    private static JavaClasses connectivityServiceMainClasses;

    @BeforeClass
    public static void importMainClasses() {
        connectivityServiceMainClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("org.eclipse.ditto.connectivity.service");
    }

    /**
     * Rule 1: no connectivity-service MAIN class may depend on a SWITCHABLE Mongo artifact. The encryption migration
     * included: it rewrites the persisted payloads through the provider's {@code PersistedPayloadStore}.
     */
    @Test
    public void connectivityMustNotDependOnSwitchableMongo() {
        noClasses()
                .should().dependOnClassesThat(IS_SWITCHABLE_MONGO)
                .as("connectivity-service MAIN code must not depend on any switchable Mongo artifact "
                        + "(com.mongodb.., org.bson.., pekko.contrib.persistence.mongodb.., MongoReadJournal)")
                .because("the switchable Mongo touchpoints are routed through the backend-neutral persistence-API; "
                        + "the encryption migration reaches MongoDB through the provider's PersistedPayloadStore")
                .check(connectivityServiceMainClasses);
    }

    /**
     * Rule 2: no connectivity-service MAIN class may depend on a Postgres type. Connectivity is part of the
     * "service code" that the brief forbids from coupling to Postgres directly.
     */
    @Test
    public void connectivityMustNotDependOnPostgres() {
        noClasses()
                .should().dependOnClassesThat(IS_POSTGRES)
                .as("connectivity-service MAIN code must not depend on any Postgres type")
                .because("service code must couple only to the backend-neutral persistence-API, "
                        + "never to a concrete backend")
                .check(connectivityServiceMainClasses);
    }

    /**
     * Rule 1b: no connectivity-service MAIN class may use the Mongo-zone client. The MongoDB payload store, which the
     * provider hands to the encryption migration, builds and closes its own client; {@link ConnectivityRootActor} and
     * the migration actor build none, so no connectivity code opens a MongoDB connection pool on its own.
     */
    @Test
    public void connectivityMustNotUseTheMongoClient() {
        noClasses()
                .should().dependOnClassesThat(IS_MONGO_CLIENT)
                .as("connectivity-service MAIN code must not use the Mongo-zone client "
                        + "(MongoClientWrapper, DittoMongoClient)")
                .because("the backend's provider builds and owns the clients behind its accessors; no connectivity "
                        + "code may open a MongoDB connection pool")
                .check(connectivityServiceMainClasses);
    }

    /**
     * Meta-test: proves Rule 1b is NON-VACUOUS, i.e. its matcher matches the real client class.
     */
    @Test
    public void mongoClientMatcherActuallyMatchesMongoClientWrapper() {
        final JavaClass mongoClientWrapper = new ClassFileImporter().importClass(MongoClientWrapper.class);

        assertThat(IS_MONGO_CLIENT.test(mongoClientWrapper))
                .as("the Mongo-client matcher must match MongoClientWrapper, otherwise Rule 1b would be vacuous")
                .isTrue();
    }

    /**
     * Meta-test: proves Rule 1 is NON-VACUOUS. The real legacy Mongo persistence plugin class
     * {@code pekko.contrib.persistence.mongodb.MongoReadJournal} (from {@code pekko-persistence-mongodb}, on this
     * module's test classpath) is the canonical switchable artifact the refactor moved behind
     * {@code provider.getReadJournal()}. It is matched by BOTH the {@code pekko.contrib.persistence.mongodb..}
     * package branch AND the {@code MongoReadJournal} simple-name branch of {@link #IS_SWITCHABLE_MONGO}. If the
     * matcher did not actually match it, Rule 1 would silently pass on everything - so this guards against the
     * predicate being accidentally narrowed to nothing.
     */
    @Test
    public void switchableMongoMatcherActuallyMatchesPekkoMongoReadJournal() {
        final JavaClasses pluginClasses = new ClassFileImporter()
                .importPackages("pekko.contrib.persistence.mongodb");
        final JavaClass mongoReadJournal = pluginClasses.stream()
                .filter(c -> c.getFullName().equals("pekko.contrib.persistence.mongodb.MongoReadJournal"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected pekko.contrib.persistence.mongodb.MongoReadJournal to be present on the test "
                                + "classpath (the pekko-persistence-mongodb plugin). It was not imported - the "
                                + "meta-test can no longer prove Rule 1 is non-vacuous."));

        assertThat(IS_SWITCHABLE_MONGO.test(mongoReadJournal))
                .as("the switchable-Mongo matcher must match the real pekko.contrib.persistence.mongodb."
                        + "MongoReadJournal plugin class, otherwise Rule 1 would be vacuous")
                .isTrue();
    }
}
