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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.actor.ActorNotFound;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.health.RetrieveHealth;
import org.eclipse.ditto.internal.utils.health.StatusInfo;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresHealthChecker;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaBootstrapUnavailableException;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.reaper.PostgresDeleteAtReaperActor;
import org.eclipse.ditto.thingsearch.persistence.api.SearchPersistenceProvider;
import org.eclipse.ditto.thingsearch.persistence.api.ThingsSearchUpdaterPersistence;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.r2dbc.spi.R2dbcBadGrammarException;
import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.R2dbcPermissionDeniedException;

/**
 * Unit tests for {@link PostgresSearchPersistenceProvider}: extension resolution via the shipped opt-in profile,
 * fail-fast bootstrap against an unreachable database, the clarity of the not-yet-implemented factory methods, and the
 * ignored-Mongo-knob detection — all WITHOUT a live PostgreSQL.
 */
public final class PostgresSearchPersistenceProviderTest {

    private static Config config;
    private static ActorSystem system;

    @BeforeClass
    public static void setUp() {
        config = ConfigFactory.load("postgres-search-test.conf");
        system = ActorSystem.create(PostgresSearchPersistenceProviderTest.class.getSimpleName(), config);
    }

    @AfterClass
    public static void tearDown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    @Test
    public void optInProfileResolvesThePostgresProvider() {
        final SearchPersistenceProvider provider =
                SearchPersistenceProvider.get(system, ScopedConfig.dittoExtension(config));

        assertThat(provider)
                .as("including ditto-postgres-search.conf must resolve the PostgreSQL search provider")
                .isInstanceOf(PostgresSearchPersistenceProvider.class);
    }

    @Test
    public void resolvedProviderCachedPerActorSystem() {
        final SearchPersistenceProvider first =
                SearchPersistenceProvider.get(system, ScopedConfig.dittoExtension(config));
        final SearchPersistenceProvider second =
                SearchPersistenceProvider.get(system, ScopedConfig.dittoExtension(config));

        assertThat(second).isSameAs(first);
    }

    @Test
    public void healthCheckPropsReturnsThePostgresHealthCheckerPropsDropIn() {
        // Requirement 1 (Phase F): SearchHealthCheckingActorFactory stores this Props verbatim under the
        // "persistence" label (thingsearch/service SearchHealthCheckingActorFactory:62) — exactly the same consumption
        // shape MongoHealthChecker.props() satisfies. PostgresHealthChecker (postgres-client) is reused unmodified,
        // identical to PostgresPersistenceBackendProvider#healthCheckProps()'s own precedent.
        final PostgresSearchPersistenceProvider provider =
                new PostgresSearchPersistenceProvider(system, ConfigFactory.empty());

        assertThat(provider.healthCheckProps().actorClass()).isEqualTo(PostgresHealthChecker.class);
    }

    @Test
    public void healthCheckActorReportsDownWithoutCrashingWhenTheDatabaseIsUnreachable() {
        // Offline complement to the Testcontainers IT's healthy-UP case: against an unreachable host the actor must
        // report DOWN (not crash, not stay UNKNOWN forever) and must keep answering subsequent RetrieveHealth asks.
        // username/password must be non-blank here (unlike the URI-embedded-credentials IT): with no embedded
        // credentials in the URI and a blank username, ConnectionPoolFactory omits the USER option entirely and
        // r2dbc-postgresql's builder throws NoSuchOptionException("No value found for user") — synchronously, INSIDE
        // the actor's constructor. That is a different (and, for this test, uninteresting) failure mode: the actor
        // then fails ActorInitializationException and is stopped by the default guardian strategy, so it never
        // answers RetrieveHealth at all (the message becomes a dead letter) — not the DOWN-without-crashing behavior
        // this test targets.
        final Config unreachable = ConfigFactory.parseString(
                        "ditto.postgresql.uri = \"r2dbc:postgresql://localhost:1/ditto\"\n"
                                + "ditto.postgresql.ssl.mode = \"disable\"\n"
                                + "ditto.postgresql.connect-timeout = 1s\n"
                                + "ditto.postgresql.username = \"test\"\n"
                                + "ditto.postgresql.password = \"test\"\n")
                .withFallback(config);
        final ActorSystem healthSystem = ActorSystem.create("pgSearchHealthCheckUnreachable", unreachable);
        try {
            final PostgresSearchPersistenceProvider provider =
                    new PostgresSearchPersistenceProvider(healthSystem, ConfigFactory.empty());
            final ActorRef healthActor = healthSystem.actorOf(provider.healthCheckProps());
            final TestKit probe = new TestKit(healthSystem);

            healthActor.tell(RetrieveHealth.newInstance(), probe.getRef());
            final StatusInfo first = probe.expectMsgClass(Duration.ofSeconds(10L), StatusInfo.class);
            assertThat(first.getStatus()).isEqualTo(StatusInfo.Status.DOWN);

            // The actor survived the failed probe and keeps answering — no crash, no death.
            healthActor.tell(RetrieveHealth.newInstance(), probe.getRef());
            final StatusInfo second = probe.expectMsgClass(Duration.ofSeconds(10L), StatusInfo.class);
            assertThat(second.getStatus()).isEqualTo(StatusInfo.Status.DOWN);
        } finally {
            TestKit.shutdownActorSystem(healthSystem);
        }
    }

    @Test
    public void bootstrapSchemaFailsFastAgainstAnUnreachableDatabase() {
        // Point at a closed port with SSL disabled so the connect (not the SSL boot-guard) is what fails: proves
        // bootstrapSchema() is synchronous and throws rather than silently returning against an unusable database.
        // With the retry disabled it makes exactly one attempt. username/password are non-blank so that the connect
        // is reached at all (see healthCheckActorReportsDownWithoutCrashingWhenTheDatabaseIsUnreachable).
        final Config unreachable = ConfigFactory.parseString(
                        "ditto.postgresql.uri = \"r2dbc:postgresql://127.0.0.1:1/ditto\"\n"
                                + "ditto.postgresql.ssl.mode = \"disable\"\n"
                                + "ditto.postgresql.connect-timeout = 1s\n"
                                + "ditto.postgresql.username = \"test\"\n"
                                + "ditto.postgresql.password = \"test\"\n"
                                + "ditto.postgresql.bootstrap.retry.enabled = false\n")
                .withFallback(config);
        final ActorSystem unreachableSystem = ActorSystem.create("unreachablePgSearch", unreachable);
        try {
            final PostgresSearchPersistenceProvider provider =
                    new PostgresSearchPersistenceProvider(unreachableSystem, ConfigFactory.empty());
            assertThatThrownBy(provider::bootstrapSchema)
                    .as("bootstrapSchema must fail fast (throw) when the database is unreachable")
                    .isInstanceOf(R2dbcException.class)
                    .hasRootCauseInstanceOf(ConnectException.class);
        } finally {
            TestKit.shutdownActorSystem(unreachableSystem);
        }
    }

    @Test
    public void bootstrapSchemaGivesUpAfterTheRetryBudgetAgainstAnUnreachableDatabase() {
        // The database prerequisites check is the first database contact of every attempt, so it is retried as well.
        final Config unreachable = ConfigFactory.parseString(
                        "ditto.postgresql.uri = \"r2dbc:postgresql://127.0.0.1:1/ditto\"\n"
                                + "ditto.postgresql.ssl.mode = \"disable\"\n"
                                + "ditto.postgresql.connect-timeout = 1s\n"
                                + "ditto.postgresql.username = \"test\"\n"
                                + "ditto.postgresql.password = \"test\"\n"
                                + "ditto.postgresql.bootstrap.retry { max-duration = 1s, min-backoff = 100ms, "
                                + "max-backoff = 200ms }\n")
                .withFallback(config);
        final ActorSystem unreachableSystem = ActorSystem.create("unreachablePgSearchRetried", unreachable);
        try {
            final PostgresSearchPersistenceProvider provider =
                    new PostgresSearchPersistenceProvider(unreachableSystem, ConfigFactory.empty());
            final long start = System.nanoTime();
            assertThatThrownBy(provider::bootstrapSchema).isInstanceOf(SchemaBootstrapUnavailableException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start))
                    .isBetween(Duration.ofSeconds(1), Duration.ofSeconds(5));
        } finally {
            TestKit.shutdownActorSystem(unreachableSystem);
        }
    }

    @Test
    public void detectIgnoredMongoKnobsReturnsEmptyWhenNoneAreSet() {
        final Config noKnobs = ConfigFactory.parseString("ditto.search.index-initialization.custom-indexes = {}");
        assertThat(PostgresSearchPersistenceProvider.detectIgnoredMongoKnobs(noKnobs)).isEmpty();
    }

    @Test
    public void detectIgnoredMongoKnobsReturnsEmptyWhenNoSearchBlockPresent() {
        assertThat(PostgresSearchPersistenceProvider.detectIgnoredMongoKnobs(ConfigFactory.empty())).isEmpty();
    }

    @Test
    public void createUpdaterPersistenceStartsTheDeleteAtReaperActorExactlyOnce() {
        // A dedicated actor system (not the shared class-level one) so this test's reaper actor does not linger
        // alongside the other tests in this class. SSL disabled: this test never opens a real connection (the pool is
        // lazy, initial-size=0), it only proves the actor-start guard is idempotent, so the SSL boot guard (which
        // would otherwise refuse to build the pool without a root-cert) is irrelevant here — same pattern as
        // bootstrapSchemaFailsFastAgainstAnUnreachableDatabase's override below.
        final Config noSslGuard = ConfigFactory.parseString(
                        "ditto.postgresql.ssl.mode = \"disable\"\n"
                                + "ditto.postgresql.username = \"test\"\n"
                                + "ditto.postgresql.password = \"test\"\n")
                .withFallback(config);
        final ActorSystem reaperSystem = ActorSystem.create("pgSearchReaperIdempotency", noSslGuard);
        try {
            final PostgresSearchPersistenceProvider provider =
                    new PostgresSearchPersistenceProvider(reaperSystem, ConfigFactory.empty());

            final ThingsSearchUpdaterPersistence first = provider.createUpdaterPersistence();
            // The second call must be a no-op for the reaper start (idempotent guard) rather than throwing
            // InvalidActorNameException against the already-running actor from the first call.
            final ThingsSearchUpdaterPersistence second = provider.createUpdaterPersistence();
            assertThat(first).isNotNull();
            assertThat(second).isNotNull();

            final ActorRef reaperActor = reaperSystem.actorSelection("/user/" + PostgresDeleteAtReaperActor.ACTOR_NAME)
                    .resolveOne(Duration.ofSeconds(5L))
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            assertThat(reaperActor).as("exactly one reaper actor must be resolvable under its well-known name")
                    .isNotNull();
        } catch (final Exception e) {
            throw new AssertionError("Failed to resolve the delete-at reaper actor", e);
        } finally {
            TestKit.shutdownActorSystem(reaperSystem);
        }
    }

    @Test
    public void otherFactoryMethodsNeverStartTheReaperWithoutCreateUpdaterPersistence() {
        // Reaper-hook pin (Phase F requirement 4, final decision): createUpdaterPersistence() is the ONLY trigger.
        // Calling every OTHER factory method -- including createUpdaterFlow(), which a naive reading might mistake
        // for "the write path materializing" -- must NEVER start the reaper on its own.
        final Config noSslGuard = ConfigFactory.parseString(
                        "ditto.postgresql.ssl.mode = \"disable\"\n"
                                + "ditto.postgresql.username = \"test\"\n"
                                + "ditto.postgresql.password = \"test\"\n")
                .withFallback(config);
        final ActorSystem noReaperSystem = ActorSystem.create("pgSearchReaperNeverStartedWithoutTrigger", noSslGuard);
        try {
            final PostgresSearchPersistenceProvider provider =
                    new PostgresSearchPersistenceProvider(noReaperSystem, ConfigFactory.empty());

            // Every method except createUpdaterPersistence(), called repeatedly, in a mixed order.
            provider.createUpdaterFlow();
            provider.createSearchPersistence();
            provider.createBackgroundSyncBookmarkPersistence();
            provider.createAggregationPersistence();
            provider.healthCheckProps();
            provider.createUpdaterFlow();
            provider.createSearchPersistence();

            assertThatThrownBy(() -> noReaperSystem.actorSelection("/user/" + PostgresDeleteAtReaperActor.ACTOR_NAME)
                    .resolveOne(Duration.ofSeconds(2L))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS))
                    .as("no method other than createUpdaterPersistence() may start the delete-at reaper")
                    .hasCauseInstanceOf(ActorNotFound.class);
        } finally {
            TestKit.shutdownActorSystem(noReaperSystem);
        }
    }

    @Test
    public void createUpdaterPersistenceStartsTheReaperExactlyOnceRegardlessOfSurroundingCallOrder() {
        // Reaper-hook pin (Phase F requirement 4): exactly-once semantics must hold no matter what OTHER provider
        // methods are called before, between, or after createUpdaterPersistence() -- not just under back-to-back
        // repeat calls (see createUpdaterPersistenceStartsTheDeleteAtReaperActorExactlyOnce above).
        final Config noSslGuard = ConfigFactory.parseString(
                        "ditto.postgresql.ssl.mode = \"disable\"\n"
                                + "ditto.postgresql.username = \"test\"\n"
                                + "ditto.postgresql.password = \"test\"\n")
                .withFallback(config);
        final ActorSystem reaperSystem = ActorSystem.create("pgSearchReaperCallOrderPinned", noSslGuard);
        try {
            final PostgresSearchPersistenceProvider provider =
                    new PostgresSearchPersistenceProvider(reaperSystem, ConfigFactory.empty());

            provider.createUpdaterFlow();
            provider.healthCheckProps();
            provider.createUpdaterPersistence();
            provider.createSearchPersistence();
            provider.createUpdaterPersistence(); // second call: must stay a no-op (idempotent guard)
            provider.createAggregationPersistence();
            provider.createBackgroundSyncBookmarkPersistence();

            final ActorRef reaperActor = reaperSystem.actorSelection("/user/" + PostgresDeleteAtReaperActor.ACTOR_NAME)
                    .resolveOne(Duration.ofSeconds(5L))
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            assertThat(reaperActor)
                    .as("exactly one reaper actor must be resolvable, regardless of the call order around it")
                    .isNotNull();
        } catch (final Exception e) {
            throw new AssertionError("Failed to resolve the delete-at reaper actor", e);
        } finally {
            TestKit.shutdownActorSystem(reaperSystem);
        }
    }

    @Test
    public void detectIgnoredMongoKnobsFlagsEverySetMongoOnlyKnob() {
        final Config withKnobs = ConfigFactory.parseString(
                "ditto.search {\n"
                        + "  mongo-hints-by-namespace = { \"org.eclipse.ditto\" = \"v_wildcard\" }\n"
                        + "  mongo-count-hint-index-name = \"v_wildcard\"\n"
                        + "  index-initialization.custom-indexes { my_idx { fields = [] } }\n"
                        + "  operator-metrics.custom-metrics.total_things.index-hint = \"my_index_name\"\n"
                        + "}\n");

        assertThat(PostgresSearchPersistenceProvider.detectIgnoredMongoKnobs(withKnobs))
                .containsExactlyInAnyOrder(
                        "ditto.search.mongo-hints-by-namespace",
                        "ditto.search.mongo-count-hint-index-name",
                        "ditto.search.index-initialization.custom-indexes",
                        "ditto.search.operator-metrics.custom-metrics.total_things.index-hint");
    }

    @Test
    public void classifyTrgmFailureNamesTheMissingDatabasePrivilegeBySqlState() {
        // PostgreSQL's wording for a non-superuser without CREATE on the database
        final R2dbcException denied = new R2dbcPermissionDeniedException(
                "permission denied to create extension \"pg_trgm\"", "42501");

        assertThat(PostgresSearchPersistenceProvider.classifyTrgmFailure(denied))
                .isEqualTo(PostgresSearchPersistenceProvider.TrgmFailure.NOT_PERMITTED);
        assertThat(PostgresSearchPersistenceProvider.TrgmFailure.NOT_PERMITTED.getMessage())
                .contains("GRANT CREATE ON DATABASE")
                .contains("CREATE EXTENSION IF NOT EXISTS pg_trgm SCHEMA ditto;");
    }

    @Test
    public void classifyTrgmFailureNamesTheMissingDatabasePrivilegeByMessageWithoutSqlState() {
        final RuntimeException wrapped = new IllegalStateException("boot failed",
                new RuntimeException("permission denied to create extension \"pg_trgm\""));

        assertThat(PostgresSearchPersistenceProvider.classifyTrgmFailure(wrapped))
                .isEqualTo(PostgresSearchPersistenceProvider.TrgmFailure.NOT_PERMITTED);
    }

    @Test
    public void classifyTrgmFailureNamesTheExtensionOffTheSearchPath() {
        // a superuser's plain CREATE EXTENSION put pg_trgm into public; the roles' search_path is ditto
        final RuntimeException wrapped = new IllegalStateException("boot failed", new R2dbcBadGrammarException(
                "operator class \"gin_trgm_ops\" does not exist for access method \"gin\"", "42704"));

        assertThat(PostgresSearchPersistenceProvider.classifyTrgmFailure(wrapped))
                .isEqualTo(PostgresSearchPersistenceProvider.TrgmFailure.NOT_ON_SEARCH_PATH);
        assertThat(PostgresSearchPersistenceProvider.TrgmFailure.NOT_ON_SEARCH_PATH.getMessage())
                .contains("ALTER EXTENSION pg_trgm SET SCHEMA ditto;");
    }

    @Test
    public void classifyTrgmFailureIgnoresOtherUndefinedObjects() {
        final R2dbcException otherUndefinedObject =
                new R2dbcBadGrammarException("type \"jsonbx\" does not exist", "42704");

        assertThat(PostgresSearchPersistenceProvider.classifyTrgmFailure(otherUndefinedObject))
                .isEqualTo(PostgresSearchPersistenceProvider.TrgmFailure.NONE);
    }

    @Test
    public void classifyTrgmFailureIgnoresUnrelatedFailuresAndNullMessages() {
        assertThat(PostgresSearchPersistenceProvider.classifyTrgmFailure(new RuntimeException((String) null)))
                .isEqualTo(PostgresSearchPersistenceProvider.TrgmFailure.NONE);
        assertThat(PostgresSearchPersistenceProvider.classifyTrgmFailure(
                new IllegalStateException("connection refused", new ConnectException("refused"))))
                .isEqualTo(PostgresSearchPersistenceProvider.TrgmFailure.NONE);
        assertThat(PostgresSearchPersistenceProvider.classifyTrgmFailure(
                new R2dbcBadGrammarException("syntax error at or near \"FROM\"", "42601")))
                .isEqualTo(PostgresSearchPersistenceProvider.TrgmFailure.NONE);
    }

}
