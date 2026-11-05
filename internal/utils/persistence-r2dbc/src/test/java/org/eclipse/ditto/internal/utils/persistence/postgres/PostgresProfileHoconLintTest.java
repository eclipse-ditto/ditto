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
package org.eclipse.ditto.internal.utils.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresSchemaBootstrapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.ConfigIncludeContext;
import com.typesafe.config.ConfigIncluder;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.ConfigUtil;

/**
 * HOCON lint over the effective Postgres pg-dev profiles (switchability proof, layer 3).
 *
 * <p>Loads each service's pg-dev config ({@code things-pg-dev.conf}, {@code policies-pg-dev.conf},
 * {@code connectivity-pg-dev.conf}) and asserts that the Postgres profile is truly "Mongo-free":
 *
 * <ol>
 *   <li><b>Positive anchor</b>: the config DOES contain the Postgres provider class and the
 *       per-service Postgres auto-start journal/snapshot IDs — proving we loaded the right
 *       (Postgres) profile and that the assertion of absence is non-vacuous.</li>
 *   <li><b>No Mongo plugin class strings</b>: no config value contains
 *       {@code pekko.contrib.persistence.mongodb} (the legacy Mongo plugin class package).</li>
 *   <li><b>No Mongo plugin IDs</b>: no rendered config entry references
 *       {@code pekko-contrib-mongodb-persistence-} (the Mongo plugin-id naming prefix).</li>
 *   <li><b>{@code read-journal.entity} set</b>: the per-service read-journal entity key is
 *       present, non-empty, and set to the expected value in the REAL service base conf
 *       ({@code <svc>.conf}), not a test-injected literal.</li>
 * </ol>
 *
 * <h3>Config loading — real files, not copies</h3>
 * Each pg-dev conf is loaded from the REAL {@code <svc>/service/src/main/resources/<svc>-pg-dev.conf}
 * source file, never from a test-resource copy. A URLClassLoader is constructed for each service,
 * rooted at the real {@code <svc>/service/src/main/resources/} directory (with the test class loader
 * as parent). {@code ConfigFactory.load(classLoader, "<svc>-pg-dev")} then:
 * <ul>
 *   <li>Finds {@code <svc>-pg-dev.conf} in the service resources directory.</li>
 *   <li>Resolves {@code include classpath("<svc>-dev")} from the same service directory.</li>
 *   <li>Resolves {@code include classpath("ditto-postgres-persistence")} from the parent class
 *       loader (this module's main resources) — the REAL shared Postgres profile conf (provider
 *       wiring + client/pool/SSL defaults in one file).</li>
 * </ul>
 * No copies exist in this module's test resources for the pg-dev or *-dev confs.
 *
 * <h3>Absence checks scoped to the pg-dev profile layer</h3>
 * Assertions 1 and 2 (no Mongo class/ID) are checked against the pg-dev profile alone
 * ({@code <svc>-pg-dev.conf} + its includes). The service base conf ({@code <svc>.conf}) defines
 * the Mongo plugin blocks that are active when Postgres is NOT selected — those blocks are not
 * part of the pg-dev override layer and must not be confused with the Postgres profile. The proof
 * is: the pg-dev override layer introduces zero Mongo wiring (the Mongo-free switchability claim
 * applies to the Postgres profile, not to the base service defaults).
 *
 * <h3>read-journal.entity sourced from real service base conf (not test-injected)</h3>
 * The pg-dev conf includes only {@code <svc>-dev.conf} (dev cluster/HTTP settings), not the full
 * {@code <svc>.conf}. The {@code read-journal.entity} key is set in the real {@code <svc>.conf}
 * (not in any pg-dev override). Assertion 3 reads this value from the REAL
 * {@code <svc>/service/src/main/resources/<svc>.conf} file via {@code ConfigFactory.parseFile()}.
 * The value is NOT a hand-typed test constant: if the real conf were missing or wrong, the test
 * would fail, making this a genuine proof of production correctness.
 *
 * <h3>Env-placeholder handling</h3>
 * The Postgres profile uses optional substitutions such as {@code ${?POSTGRES_URI}},
 * {@code ${?POSTGRES_READ_JOURNAL_ENTITY}}, etc. {@code ConfigFactory.load()} fully resolves the
 * config: optional ({@code ${?…}}) substitutions for absent env vars resolve away without error.
 * No {@code setAllowUnresolved} flag is needed.
 *
 * <h3>Comments excluded from absence checks</h3>
 * The rendered config used for absence assertions uses {@link ConfigRenderOptions#concise()} to
 * suppress HOCON comments. This prevents false positives from comments in
 * {@code ditto-postgres-persistence.conf} that describe the Mongo equivalents — documentation,
 * not live config values.
 *
 * @since 4.0.0
 */
@RunWith(Parameterized.class)
public final class PostgresProfileHoconLintTest {

    /** Mongo plugin CLASS package — must not appear anywhere in a Postgres profile's config values. */
    private static final String MONGO_PLUGIN_CLASS_PACKAGE = "pekko.contrib.persistence.mongodb";

    /** Mongo plugin ID prefix — must not appear in any key or value of a Postgres profile config. */
    private static final String MONGO_PLUGIN_ID_PREFIX = "pekko-contrib-mongodb-persistence-";

    /** Config path for the persistence backend provider extension class. */
    private static final String PROVIDER_CLASS_PATH =
            "ditto.extensions.persistence-backend-provider.extension-class";

    /** Expected Postgres provider class (positive anchor). */
    private static final String POSTGRES_PROVIDER_CLASS =
            "org.eclipse.ditto.internal.utils.persistence.postgres.PostgresPersistenceBackendProvider";

    /** Config path for the Pekko auto-start journals. */
    private static final String PEKKO_AUTO_START_JOURNALS =
            "pekko.persistence.journal.auto-start-journals";

    /** Config path for the Pekko auto-start snapshot stores. */
    private static final String PEKKO_AUTO_START_SNAPSHOTS =
            "pekko.persistence.snapshot-store.auto-start-snapshot-stores";

    /** Config path for read-journal entity (under the provider extension-config). */
    private static final String READ_JOURNAL_ENTITY_PATH =
            "ditto.extensions.persistence-backend-provider.extension-config.read-journal.entity";

    /**
     * Project root, discovered from the Maven module directory ({@code user.dir}).
     * Maven Surefire sets {@code user.dir} to the module base dir:
     * {@code <project-root>/internal/utils/persistence-r2dbc}.  Three parent hops reach the
     * repo root.
     */
    private static final Path PROJECT_ROOT =
            Paths.get(System.getProperty("user.dir")).toAbsolutePath()
                 .getParent()  // persistence-r2dbc → utils
                 .getParent()  // utils → internal
                 .getParent(); // internal → project root (postgres-190626-feat-dev)

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> services() {
        return Arrays.asList(
                new Object[]{
                        "things",
                        "things/service/src/main/resources",
                        "things-pg-dev",
                        "ditto-postgres-things-journal",
                        "ditto-postgres-things-snapshots",
                        "things"     // expected read-journal entity value (verified against real things.conf)
                },
                new Object[]{
                        "policies",
                        "policies/service/src/main/resources",
                        "policies-pg-dev",
                        "ditto-postgres-policies-journal",
                        "ditto-postgres-policies-snapshots",
                        "policies"
                },
                new Object[]{
                        "connectivity",
                        "connectivity/service/src/main/resources",
                        "connectivity-pg-dev",
                        "ditto-postgres-connections-journal",
                        "ditto-postgres-connections-snapshots",
                        "connections"
                }
        );
    }

    @Parameterized.Parameter(0)
    public String serviceName;

    @Parameterized.Parameter(1)
    public String serviceResourcesRelPath;

    @Parameterized.Parameter(2)
    public String pgDevResourceName;

    @Parameterized.Parameter(3)
    public String expectedJournalId;

    @Parameterized.Parameter(4)
    public String expectedSnapshotId;

    @Parameterized.Parameter(5)
    public String expectedReadJournalEntity;

    /**
     * Builds a URLClassLoader that sees the REAL service resources directory as its primary
     * source, with the test class loader (which has {@code ditto-postgres-persistence.conf}
     * from this module's main resources) as parent.
     *
     * <p>This means:
     * <ul>
     *   <li>{@code <svc>-pg-dev.conf} and {@code <svc>-dev.conf} resolve from the REAL service
     *       {@code src/main/resources} directory (not from any test-resource copy).</li>
     *   <li>{@code ditto-postgres-persistence.conf} resolves from the parent class loader
     *       (this module's main resources).</li>
     * </ul>
     */
    private ClassLoader buildServiceClassLoader() {
        try {
            final File serviceResourcesDir =
                    PROJECT_ROOT.resolve(serviceResourcesRelPath).toFile();
            assertThat(serviceResourcesDir)
                    .as("Service resources dir must exist: %s", serviceResourcesDir)
                    .isDirectory();
            final URL serviceResourcesUrl = serviceResourcesDir.toURI().toURL();
            // Parent = test class loader (has ditto-postgres-persistence + Pekko reference.conf etc.)
            return new URLClassLoader(
                    new URL[]{serviceResourcesUrl},
                    Thread.currentThread().getContextClassLoader());
        } catch (final Exception e) {
            throw new IllegalStateException(
                    "Failed to build URLClassLoader for service " + serviceName, e);
        }
    }

    /**
     * Loads the Postgres pg-dev config from the REAL service source files.
     *
     * <p>Uses a URLClassLoader rooted at the real service resources directory so that
     * {@code <svc>-pg-dev.conf} and its {@code include classpath("<svc>-dev")} are resolved from
     * the actual source tree, not from any test-resource copies.  The shared Postgres profile
     * conf ({@code ditto-postgres-persistence.conf}) is resolved
     * from the parent class loader (this module's main resources).
     *
     * <p>This config is used for the positive anchor and the Mongo-absence assertions (assertions
     * 1 and 2).  The service base conf ({@code <svc>.conf}) is NOT included here — it contains the
     * Mongo plugin definitions that are active when Postgres is NOT selected and must not be
     * conflated with the Postgres override profile.
     */
    private Config loadPgDevConfig() {
        final ClassLoader cl = buildServiceClassLoader();
        return ConfigFactory.load(cl, pgDevResourceName);
    }

    /**
     * Reads {@code read-journal.entity} directly from the REAL service base conf file
     * ({@code <svc>/service/src/main/resources/<svc>.conf}) using
     * {@code ConfigFactory.parseFile()}.
     *
     * <p>This is used exclusively for assertion 3: the value comes from the real production config
     * file, not a test-injected literal.  Parsing (rather than loading) the service conf avoids
     * pulling its Mongo plugin class definitions into the effective config checked by assertions 1
     * and 2.
     *
     * <p>Any {@code include} targets that are not resolvable in this test context are silently
     * skipped — they are NOT needed to reach the {@code read-journal.entity} key, which is set
     * directly in the body of {@code <svc>.conf}.
     */
    private Config parseServiceBaseConf() {
        final File serviceBaseConf = PROJECT_ROOT
                .resolve(serviceResourcesRelPath)
                .resolve(serviceName + ".conf")
                .toFile();
        assertThat(serviceBaseConf)
                .as("Service base conf must exist: %s", serviceBaseConf)
                .isFile();
        // parseFile with a no-op includer: skips ALL include directives so we get only the keys
        // set directly in the body of <svc>.conf, without pulling in any included files.
        // This avoids self-referential substitution cycles in *-extension.conf includes and avoids
        // transitive Mongo plugin definitions.  The read-journal.entity key is set directly in
        // the body of <svc>.conf (not in any included file), so it is always captured here.
        // setAllowUnresolved(true) tolerates ${?ENV_VAR} optional-substitution patterns that
        // appear alongside the literal defaults we need to read.
        final ConfigIncluder noOpIncluder = new ConfigIncluder() {
            @Override
            public ConfigIncluder withFallback(final ConfigIncluder fallback) {
                return this;
            }

            @Override
            public ConfigObject include(final ConfigIncludeContext context, final String what) {
                return ConfigFactory.empty().root();
            }
        };
        return ConfigFactory.parseFile(serviceBaseConf,
                ConfigParseOptions.defaults().setAllowMissing(true).setIncluder(noOpIncluder))
                .resolve(ConfigResolveOptions.noSystem().setAllowUnresolved(true));
    }

    // --------------------------------------------------
    // Positive anchor
    // --------------------------------------------------

    /**
     * Positive anchor (part 1): the loaded pg-dev config DOES carry the Postgres provider class.
     * This proves we loaded the Postgres profile and the absence assertions below are non-vacuous.
     */
    @Test
    public void postgresProviderClassIsPresentInEffectiveConfig() {
        final Config config = loadPgDevConfig();

        assertThat(config.hasPath(PROVIDER_CLASS_PATH))
                .as("Postgres provider class path (%s) must be present in %s",
                        PROVIDER_CLASS_PATH, pgDevResourceName)
                .isTrue();
        assertThat(config.getString(PROVIDER_CLASS_PATH))
                .as("Postgres provider class must be the PostgresPersistenceBackendProvider")
                .isEqualTo(POSTGRES_PROVIDER_CLASS);
    }

    /**
     * Positive anchor (part 2): the loaded pg-dev config DOES have the expected per-service
     * Postgres auto-start journal and snapshot-store IDs. This further confirms we loaded the
     * right profile.
     */
    @Test
    public void postgresAutoStartIdsArePresentInEffectiveConfig() {
        final Config config = loadPgDevConfig();

        final List<String> journals = config.getStringList(PEKKO_AUTO_START_JOURNALS);
        assertThat(journals)
                .as("[%s] pekko.persistence.journal.auto-start-journals must contain %s",
                        serviceName, expectedJournalId)
                .contains(expectedJournalId);

        final List<String> snapshots = config.getStringList(PEKKO_AUTO_START_SNAPSHOTS);
        assertThat(snapshots)
                .as("[%s] pekko.persistence.snapshot-store.auto-start-snapshot-stores must contain %s",
                        serviceName, expectedSnapshotId)
                .contains(expectedSnapshotId);
    }

    // --------------------------------------------------
    // Assertion 1: no Mongo plugin class strings
    // --------------------------------------------------

    /**
     * Assertion 1: no config VALUE in the Postgres pg-dev profile contains the Mongo plugin
     * class package {@code pekko.contrib.persistence.mongodb}.
     *
     * <p>Walks every resolved config entry by rendering to a concise (comment-free) JSON string
     * and asserts the forbidden substring is absent. Using {@link ConfigRenderOptions#concise()}
     * excludes HOCON comments, which prevents false positives from documentation comments in
     * {@code ditto-postgres-persistence.conf} that mention the Mongo plugin class for reference.
     *
     * <p>Checked against the pg-dev profile only (not the service base conf), since the base conf
     * appropriately retains inactive Mongo plugin definitions for when Postgres is NOT selected.
     * The proof is: the pg-dev override layer itself introduces zero Mongo plugin class wiring.
     */
    @Test
    public void noMongoPluginClassStringsInEffectiveConfig() {
        final Config config = loadPgDevConfig();
        // ConfigRenderOptions.concise() renders without comments — prevents false positives from
        // documentation comments in the Postgres profile confs that reference Mongo for contrast.
        final String rendered = config.root().render(ConfigRenderOptions.concise());

        assertThat(rendered)
                .as("[%s] Postgres pg-dev profile must NOT contain Mongo plugin class package '%s' — "
                                + "a Mongo class would indicate the profile switch is incomplete",
                        serviceName, MONGO_PLUGIN_CLASS_PACKAGE)
                .doesNotContain(MONGO_PLUGIN_CLASS_PACKAGE);
    }

    // --------------------------------------------------
    // Assertion 2: no Mongo plugin IDs
    // --------------------------------------------------

    /**
     * Assertion 2: no config KEY or VALUE in the Postgres pg-dev profile references
     * {@code pekko-contrib-mongodb-persistence-} (the Mongo plugin-id naming convention).
     *
     * <p>Uses {@code root().render(ConfigRenderOptions.concise())} which renders all keys and
     * values as comment-free JSON, so a Mongo plugin block (e.g.
     * {@code pekko-contrib-mongodb-persistence-things-journal { class = ... }}) or a Mongo plugin
     * ID listed in an auto-start array would be caught, while documentation comments that mention
     * the Mongo IDs for contrast are excluded.
     *
     * <p>Checked against the pg-dev profile only — see {@link #noMongoPluginClassStringsInEffectiveConfig()}
     * for the rationale.
     */
    @Test
    public void noMongoPluginIdsInEffectiveConfig() {
        final Config config = loadPgDevConfig();
        // ConfigRenderOptions.concise() renders without comments — prevents false positives from
        // documentation comments in the Postgres profile confs that reference Mongo plugin IDs.
        final String rendered = config.root().render(ConfigRenderOptions.concise());

        assertThat(rendered)
                .as("[%s] Postgres pg-dev profile must NOT reference Mongo plugin IDs with prefix '%s' — "
                                + "Mongo plugin IDs must not appear in the Postgres profile",
                        serviceName, MONGO_PLUGIN_ID_PREFIX)
                .doesNotContain(MONGO_PLUGIN_ID_PREFIX);
    }

    // --------------------------------------------------
    // Assertion 3: read-journal.entity is set (sourced from real service base conf)
    // --------------------------------------------------

    /**
     * Assertion 3: {@code read-journal.entity} is present, non-empty, and equals the expected
     * per-service value in the REAL service base conf ({@code <svc>.conf}).
     *
     * <p>This key is required by {@code PostgresPersistenceBackendProvider} to bind the shared
     * read-journal to the correct entity table set. A missing or empty value causes a fast-fail
     * at boot ({@code DittoConfigError}). The value is read from the REAL
     * {@code <svc>/service/src/main/resources/<svc>.conf} source file via
     * {@code ConfigFactory.parseFile()} — NOT injected by the test as a string literal.
     * If the real conf were missing or set the wrong value, this test would fail,
     * making it a genuine proof of production correctness.
     */
    @Test
    public void readJournalEntityIsSetInEffectiveConfig() {
        final Config serviceBaseConf = parseServiceBaseConf();

        assertThat(serviceBaseConf.hasPath(READ_JOURNAL_ENTITY_PATH))
                .as("[%s] Config path '%s' must be present in real %s.conf "
                                + "(sourced from %s, not a test-injected value)",
                        serviceName, READ_JOURNAL_ENTITY_PATH, serviceName,
                        PROJECT_ROOT.resolve(serviceResourcesRelPath).resolve(serviceName + ".conf"))
                .isTrue();

        final String entity = serviceBaseConf.getString(READ_JOURNAL_ENTITY_PATH);
        assertThat(entity)
                .as("[%s] read-journal.entity must be non-empty in real %s.conf", serviceName, serviceName)
                .isNotEmpty();

        assertThat(entity)
                .as("[%s] read-journal.entity in real %s.conf must equal expected value '%s'",
                        serviceName, serviceName, expectedReadJournalEntity)
                .isEqualTo(expectedReadJournalEntity);
    }

    // --------------------------------------------------
    // Assertion 4: every Postgres journal block binds the base Event interface (EmptyEvent fallback)
    // --------------------------------------------------

    /**
     * Assertion 4: every Postgres journal's {@code event-adapter-bindings} binds the BASE
     * {@code org.eclipse.ditto.base.model.signals.events.Event} interface, not just the per-service concrete event
     * interface. {@code EmptyEvent} (persisted by {@code AbstractPersistenceActor} for the always-alive/priority-update
     * journal-tag path) implements only the base {@code Event} interface; without this fallback binding it falls to
     * Pekko's {@code IdentityEventAdapter} and crashes the JSONB write path. Mongo binds the base interface
     * ({@code connectivity.conf}); the Postgres profile must mirror it in all four journal blocks. This assertion is
     * NOT parameterized per-service on purpose: it walks all four blocks directly out of
     * {@code ditto-postgres-persistence.conf} regardless of which service's pg-dev profile is under test.
     */
    @Test
    public void everyPostgresJournalBindsTheBaseEventInterface() {
        // EmptyEvent (always-alive / priority-update) implements only the base Event interface. Mongo
        // binds the base interface (connectivity.conf); without it Pekko's IdentityEventAdapter passes
        // the raw object to the JSONB write path, which rejects it.
        final Config profile = ConfigFactory.parseResources("ditto-postgres-persistence.conf").resolve();
        for (final String block : List.of("ditto-postgres-things-journal", "ditto-postgres-policies-journal",
                "ditto-postgres-connections-journal", "ditto-postgres-wot-journal")) {
            final Config bindings = profile.getConfig(block).getConfig("event-adapter-bindings");
            assertThat(bindings.hasPath("\"org.eclipse.ditto.base.model.signals.events.Event\""))
                    .as("journal block <%s> must bind the base Event interface", block)
                    .isTrue();
        }
    }

    // --------------------------------------------------
    // Assertion 5: the profile alone (no service overlay) sets the real Pekko auto-start paths
    // --------------------------------------------------

    /**
     * Assertion 5: the profile itself — with NO service overlay involved — redirects Pekko's real
     * auto-start config paths to the all-4-entity default, and the dead {@code ditto.persistence.*}
     * block (a path nothing reads; Pekko reads {@code pekko.persistence.*}) is gone. This is NOT
     * parameterized per-service: it loads {@code ditto-postgres-persistence.conf} directly, proving
     * the header's "include this SINGLE file" contract for everything EXCEPT the one step every
     * single-entity service deployment still owns — narrowing these lists to its own entity (see the
     * header note and the auto-start block comment in the profile, and each service's *-pg-dev.conf).
     */
    @Test
    public void profileAloneRedirectsPekkoAutoStartLists() {
        // The header promises: include this SINGLE file for everything except the per-entity narrowing every
        // single-entity service deployment must still do (see the profile's header + auto-start block comment).
        // Pekko reads pekko.persistence.*, never ditto.persistence.* — the profile must set the real path.
        final Config profile = ConfigFactory.parseResources("ditto-postgres-persistence.conf").resolve();
        assertThat(profile.getStringList("pekko.persistence.journal.auto-start-journals"))
                .contains("ditto-postgres-things-journal", "ditto-postgres-policies-journal",
                        "ditto-postgres-connections-journal", "ditto-postgres-wot-journal");
        assertThat(profile.getStringList("pekko.persistence.snapshot-store.auto-start-snapshot-stores"))
                .contains("ditto-postgres-things-snapshots", "ditto-postgres-policies-snapshots",
                        "ditto-postgres-connections-snapshots", "ditto-postgres-wot-snapshots");
        assertThat(profile.hasPath("ditto.persistence.journal")).as("dead ditto.persistence.* block removed")
                .isFalse();
    }

    // --------------------------------------------------
    // Assertion 6: the remember-entities store stays eventsourced, on plugins of its own
    // --------------------------------------------------

    /**
     * Assertion 6: the profile ships the two {@code connection-remember} plugin blocks (the Postgres peers of
     * {@code pekko-contrib-mongodb-persistence-connection-remember-*}) with the Pekko-serialization envelope codec and
     * leaves {@code pekko.cluster.sharding.remember-entities-store} alone, so connectivity keeps its
     * {@code eventsourced} store on PostgreSQL as on MongoDB. The blocks are not auto-started, as on MongoDB.
     */
    @Test
    public void profileShipsTheRememberPluginsAndLeavesTheRememberEntitiesStoreAlone() {
        final Config profile = ConfigFactory.parseResources("ditto-postgres-persistence.conf").resolve();
        assertThat(profile.hasPath("pekko.cluster.sharding.remember-entities-store"))
                .as("no JVM-wide ddata override").isFalse();
        assertThat(profile.getString("ditto-postgres-connection-remember-journal.class"))
                .isEqualTo("org.eclipse.ditto.internal.utils.persistence.postgres.journal.PostgresJournal");
        assertThat(profile.getString("ditto-postgres-connection-remember-snapshots.class"))
                .isEqualTo("org.eclipse.ditto.internal.utils.persistence.postgres.snapshot.PostgresSnapshotStore");
        for (final String block : List.of("ditto-postgres-connection-remember-journal",
                "ditto-postgres-connection-remember-snapshots")) {
            assertThat(profile.getString(block + ".entity")).as(block).isEqualTo("connection_remember");
            assertThat(profile.getString(block + ".payload-codec")).as(block).isEqualTo("pekko-serialization");
            assertThat(profile.hasPath(block + ".event-adapters")).as(block + ": the codec needs no adapter")
                    .isFalse();
            assertThat(profile.hasPath(block + ".plugin-dispatcher"))
                    .as(block + ": Pekko's default plugin dispatcher, as the MongoDB remember plugins").isFalse();
        }
        assertThat(profile.getStringList("pekko.persistence.journal.auto-start-journals"))
                .doesNotContain("ditto-postgres-connection-remember-journal");
        assertThat(profile.getStringList("pekko.persistence.snapshot-store.auto-start-snapshot-stores"))
                .doesNotContain("ditto-postgres-connection-remember-snapshots");
    }

    /**
     * Assertion 6, per service: the pg-dev override layer ({@code <svc>-pg-dev.conf} with its includes, parsed WITHOUT
     * reference.conf and the service base conf) sets no {@code remember-entities-store}, so connectivity keeps the
     * {@code eventsourced} store its base conf declares — no layer switches it to {@code ddata} any more.
     */
    @Test
    public void pgDevLayerDoesNotOverrideTheRememberEntitiesStore() {
        final Config pgDevLayer = ConfigFactory.parseResources(buildServiceClassLoader(), pgDevResourceName)
                .resolve(ConfigResolveOptions.defaults().setAllowUnresolved(true));
        assertThat(pgDevLayer.hasPath("pekko.cluster.sharding.remember-entities-store"))
                .as("%s: the pg-dev layer must leave remember-entities-store to the service conf", serviceName)
                .isFalse();
    }

    // --------------------------------------------------
    // Assertion 7: the profile carries the shared schema-bootstrap block
    // --------------------------------------------------

    /**
     * Assertion 7: the profile carries the shared {@code ditto.postgresql.bootstrap} block of the client conf (wait
     * for an unreachable PostgreSQL at start, then stop the service) and the exit code 1 of its shutdown reason.
     */
    @Test
    public void profileCarriesTheSharedSchemaBootstrapBlock() {
        final Config profile = ConfigFactory.parseResources("ditto-postgres-persistence.conf").resolve();
        assertThat(profile.getDuration("ditto.postgresql.bootstrap.attempt-timeout")).isEqualTo(Duration.ofSeconds(60));
        assertThat(profile.getBoolean("ditto.postgresql.bootstrap.retry.enabled")).isTrue();
        assertThat(profile.getDuration("ditto.postgresql.bootstrap.retry.max-duration"))
                .isEqualTo(Duration.ofSeconds(60));
        assertThat(profile.getBoolean("ditto.postgresql.bootstrap.exit-on-failure")).isTrue();
        assertThat(profile.getInt(ConfigUtil.joinPath("pekko", "coordinated-shutdown", "reason-overrides",
                PostgresSchemaBootstrapper.BootstrapFailed.class.getName(), "exit-code"))).isEqualTo(1);
    }
}
