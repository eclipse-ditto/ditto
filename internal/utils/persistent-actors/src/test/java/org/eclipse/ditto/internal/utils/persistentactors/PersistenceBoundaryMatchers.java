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

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;

/**
 * Shared, precise package/type matchers for the pluggable-persistence ArchUnit boundary rules.
 * <p>
 * Defining the matchers in one place lets the META-TEST assert on the EXACT same predicate that the rules apply,
 * so the rules cannot accidentally diverge from what the meta-test proves.
 *
 * @since 4.0.0
 */
final class PersistenceBoundaryMatchers {

    /** The legacy Pekko MongoDB persistence plugin package - note the LEGACY {@code pekko.} prefix. */
    static final String LEGACY_PEKKO_MONGO_PKG = "pekko.contrib.persistence.mongodb";

    /** Ditto's wrapper around the plugin read-journal; banned by its simple name across the codebase. */
    static final String MONGO_READ_JOURNAL_SIMPLE_NAME = "MongoReadJournal";

    /** Matches the legacy Pekko MongoDB plugin package {@code pekko.contrib.persistence.mongodb..}. */
    static final DescribedPredicate<JavaClass> IS_LEGACY_PEKKO_MONGO =
            new DescribedPredicate<>("reside in '" + LEGACY_PEKKO_MONGO_PKG + "..'") {
                @Override
                public boolean test(final JavaClass javaClass) {
                    final String pkg = javaClass.getPackageName();
                    return pkg.equals(LEGACY_PEKKO_MONGO_PKG) || pkg.startsWith(LEGACY_PEKKO_MONGO_PKG + ".");
                }
            };

    /**
     * Umbrella Mongo matcher used by Rule 1 / Rule 3: the Mongo Java driver ({@code com.mongodb..}), BSON
     * ({@code org.bson..}), the legacy Pekko plugin ({@code pekko.contrib.persistence.mongodb..}), or any class
     * named {@code MongoReadJournal} (the Ditto wrapper - covers it wherever it resides).
     */
    static final DescribedPredicate<JavaClass> IS_MONGO =
            new DescribedPredicate<>("be a Mongo type (com.mongodb.., org.bson.., "
                    + LEGACY_PEKKO_MONGO_PKG + ".., or named " + MONGO_READ_JOURNAL_SIMPLE_NAME + ")") {
                @Override
                public boolean test(final JavaClass javaClass) {
                    final String pkg = javaClass.getPackageName();
                    return pkg.equals("com.mongodb") || pkg.startsWith("com.mongodb.")
                            || pkg.equals("org.bson") || pkg.startsWith("org.bson.")
                            || IS_LEGACY_PEKKO_MONGO.test(javaClass)
                            || javaClass.getSimpleName().equals(MONGO_READ_JOURNAL_SIMPLE_NAME);
                }
            };

    /**
     * Postgres matcher used by Rule 2 / Rule 3: the R2DBC SPI ({@code io.r2dbc..}), the JDBC driver
     * ({@code org.postgresql..}), or the Postgres backend package
     * ({@code org.eclipse.ditto.internal.utils.persistence.postgres..}).
     */
    static final DescribedPredicate<JavaClass> IS_POSTGRES =
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

    private PersistenceBoundaryMatchers() {
        throw new AssertionError();
    }
}
