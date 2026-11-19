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
package org.eclipse.ditto.internal.utils.persistence.mongo.extension.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.persistence.AbstractPersistentActor;
import org.apache.pekko.persistence.RecoveryCompleted;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendBootstrap;
import org.eclipse.ditto.thingsearch.persistence.api.SearchPersistenceProvider;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;

import scala.concurrent.Await;

/**
 * Classloading integration test for the two MongoDB extension JARs.
 * <p>
 * The REAL shaded JARs are copied into {@code target/extensions} by the {@code maven-dependency-plugin} (see this
 * module's pom). Each case builds a child {@link URLClassLoader} over some of them, whose parent is this test's class
 * loader: the things service without its MongoDB modules, standing in for a service allinone without MongoDB.
 * {@link #parentClasspathCarriesNoMongo()} proves that the parent has no MongoDB class, so whatever MongoDB class
 * the other cases load comes from the extension JARs.
 * <p>
 * {@link #journalRoundTripThroughShadedFatJar()} starts a MongoDB container and needs Docker; without Docker it
 * fails.
 */
public final class MongoExtensionClassloadingIT {

    private static final String SEARCH_PROVIDER =
            "org.eclipse.ditto.thingsearch.persistence.mongo.MongoSearchPersistenceProvider";
    private static final String SEARCH_SELF_CHECK =
            "org.eclipse.ditto.thingsearch.persistence.mongo.MongoSearchExtensionSelfCheck";
    private static final String CLIENT_WRAPPER =
            "org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper";
    private static final String REACTIVE_MONGO_CLIENT = "com.mongodb.reactivestreams.client.MongoClient";
    private static final String MONGO_JOURNAL = "pekko.contrib.persistence.mongodb.MongoJournal";
    private static final String MARKER_RESOURCE = "META-INF/ditto-mongo-extension.properties";
    private static final String DRIVER_PATH = "pekko.contrib.persistence.mongodb.mongo.driver";
    private static final String TEST_JOURNAL = "pekko-contrib-mongodb-persistence-classloading-it-journal";
    private static final String MONGO_IMAGE = "mongo:7.0";
    private static final int MONGO_PORT = 27017;
    private static final Duration ASK_TIMEOUT = Duration.ofSeconds(30);

    private static File extensionsDir;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void resolveExtensionJars() {
        final String dir = System.getProperty("ditto.mongo.extensions.dir");
        assertThat(dir).as("system property ditto.mongo.extensions.dir must be set by failsafe").isNotBlank();
        extensionsDir = new File(dir);
        assertThat(fatJar()).as("persistence extension JAR copied").exists();
        assertThat(searchJar()).as("search extension JAR copied").exists();
    }

    @Test
    public void fatPlusSearchResolvesProviderAndPassesSelfCheck() throws Exception {
        try (final URLClassLoader loader = extensionLoader(fatJar(), searchJar())) {
            final Class<?> providerClass = loader.loadClass(SEARCH_PROVIDER);
            assertThat(SearchPersistenceProvider.class.isAssignableFrom(providerClass))
                    .as("MongoSearchPersistenceProvider is a SearchPersistenceProvider").isTrue();
            assertThat(providerClass.getClassLoader()).isSameAs(loader);
            assertThat(loader.loadClass(CLIENT_WRAPPER).getClassLoader()).isSameAs(loader);
            assertThat(loader.loadClass(REACTIVE_MONGO_CLIENT).getClassLoader()).isSameAs(loader);
            loader.loadClass(SEARCH_SELF_CHECK).getMethod("verify").invoke(null);
        }
    }

    @Test
    public void fatPlusSearchRegistersOneDistinctMongodbBootstrap() throws Exception {
        try (final URLClassLoader loader = extensionLoader(fatJar(), searchJar())) {
            assertOnlyMongodbBootstraps(loader);
        }
        final File secondFatJar = new File(temporaryFolder.newFolder(), "ditto-mongo-persistence-extension-copy.jar");
        Files.copy(fatJar().toPath(), secondFatJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try (final URLClassLoader loader = extensionLoader(fatJar(), searchJar(), secondFatJar)) {
            assertThat(loader.getResources(
                    "META-INF/services/" + PersistenceBackendBootstrap.class.getName()).asIterator())
                    .toIterable()
                    .as("both copies of the persistence JAR register the bootstrap")
                    .hasSize(2);
            assertOnlyMongodbBootstraps(loader);
        }
    }

    @Test
    public void providerConstructorRefusesMixedReleaseMarkers() throws Exception {
        final String realVersion = fatJarMarkerVersion();
        final File staleMarkerDir = temporaryFolder.newFolder();
        final File staleMarker = new File(staleMarkerDir, MARKER_RESOURCE);
        Files.createDirectories(staleMarker.getParentFile().toPath());
        Files.writeString(staleMarker.toPath(), "component=persistence (stale copy)\nversion=0-OTHER\n",
                StandardCharsets.ISO_8859_1);

        final ActorSystem system = ActorSystem.create("providerConstructor", ConfigFactory.load());
        try {
            try (final URLClassLoader loader = extensionLoader(fatJar(), searchJar(), staleMarkerDir)) {
                final Constructor<?> constructor = providerConstructor(loader);
                assertThatThrownBy(() -> constructor.newInstance(system, ConfigFactory.empty()))
                        .isInstanceOf(InvocationTargetException.class)
                        .cause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(MARKER_RESOURCE)
                        .hasMessageContaining("=" + realVersion)
                        .hasMessageContaining("persistence (stale copy)=0-OTHER");
            }
            try (final URLClassLoader loader = extensionLoader(fatJar(), searchJar())) {
                final Object provider = providerConstructor(loader).newInstance(system, ConfigFactory.empty());
                assertThat(provider).isInstanceOf(SearchPersistenceProvider.class);
            }
        } finally {
            terminate(system);
        }
    }

    @Test
    public void searchJarAloneFailsNamingTheFatJar() throws Exception {
        try (final URLClassLoader loader = extensionLoader(searchJar())) {
            assertThat(loader.loadClass(SEARCH_PROVIDER).getClassLoader()).isSameAs(loader);
            assertThatThrownBy(() -> loader.loadClass(CLIENT_WRAPPER)).isInstanceOf(ClassNotFoundException.class);
            assertThatThrownBy(() -> loader.loadClass(SEARCH_SELF_CHECK).getMethod("verify").invoke(null))
                    .isInstanceOf(InvocationTargetException.class)
                    .cause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ditto-mongo-persistence-extension")
                    .hasMessageContaining("ditto-mongo-search-extension")
                    .hasMessageContaining("/opt/ditto/backends/mongodb/");
        }
    }

    @Test
    public void journalRoundTripThroughShadedFatJar() throws Exception {
        try (final GenericContainer<?> mongo = new GenericContainer<>(MONGO_IMAGE)
                .withExposedPorts(MONGO_PORT)
                .waitingFor(Wait.forListeningPort());
                final URLClassLoader loader = extensionLoader(fatJar())) {
            mongo.start();
            final String mongoUri =
                    "mongodb://" + mongo.getHost() + ":" + mongo.getMappedPort(MONGO_PORT) + "/classloadingIT";
            final Config config = roundTripConfig(loader, mongoUri);
            final String profileDriver = ConfigFactory.parseResources(loader, "ditto-mongo-persistence.conf",
                            ConfigParseOptions.defaults().setAllowMissing(false))
                    .getString(DRIVER_PATH);
            assertThat(config.getString(DRIVER_PATH)).isEqualTo(profileDriver);
            assertThat(loader.loadClass(MONGO_JOURNAL).getClassLoader()).isSameAs(loader);
            assertThat(loader.loadClass(profileDriver).getClassLoader()).isSameAs(loader);

            final String persistenceId = "classloading-it:round-trip";
            final ActorSystem writer = ActorSystem.create("journalWriter", config, loader);
            try {
                final ActorRef actor = writer.actorOf(Props.create(RoundTripActor.class, persistenceId));
                assertThat(ask(actor, RoundTripActor.PERSIST)).isEqualTo(RoundTripActor.PERSISTED);
            } finally {
                terminate(writer);
            }

            final ActorSystem reader = ActorSystem.create("journalReader", config, loader);
            try {
                final ActorRef actor = reader.actorOf(Props.create(RoundTripActor.class, persistenceId));
                assertThat(ask(actor, RoundTripActor.RECOVERED)).isEqualTo(RoundTripActor.EVENTS);
            } finally {
                terminate(reader);
            }
        }
    }

    @Test
    public void parentClasspathCarriesNoMongo() {
        final ClassLoader parent = MongoExtensionClassloadingIT.class.getClassLoader();
        for (final String className : List.of(CLIENT_WRAPPER, "org.bson.BsonValue", "com.mongodb.MongoClientSettings",
                MONGO_JOURNAL, SEARCH_PROVIDER)) {
            assertThatThrownBy(() -> Class.forName(className, false, parent))
                    .as(className)
                    .isInstanceOf(ClassNotFoundException.class);
        }
        assertThat(loadBootstraps(parent)).isEmpty();
    }

    private static File fatJar() {
        return new File(extensionsDir, "ditto-mongo-persistence-extension.jar");
    }

    private static File searchJar() {
        return new File(extensionsDir, "ditto-mongo-search-extension.jar");
    }

    private static URLClassLoader extensionLoader(final File... entries) throws MalformedURLException {
        final URL[] urls = new URL[entries.length];
        for (int i = 0; i < entries.length; i++) {
            urls[i] = entries[i].toURI().toURL();
        }
        return new URLClassLoader(urls, MongoExtensionClassloadingIT.class.getClassLoader());
    }

    private static Constructor<?> providerConstructor(final ClassLoader loader) throws ReflectiveOperationException {
        return loader.loadClass(SEARCH_PROVIDER).getConstructor(ActorSystem.class, Config.class);
    }

    private static String fatJarMarkerVersion() throws IOException {
        final Properties marker = new Properties();
        try (final InputStream in = new URL("jar:" + fatJar().toURI() + "!/" + MARKER_RESOURCE).openStream()) {
            marker.load(in);
        }
        final String version = marker.getProperty("version");
        assertThat(version).as("filtered version of the persistence JAR's marker").isNotBlank().doesNotContain("${");
        return version;
    }

    private static void assertOnlyMongodbBootstraps(final ClassLoader loader) {
        final List<PersistenceBackendBootstrap> bootstraps = loadBootstraps(loader);
        assertThat(bootstraps).isNotEmpty();
        assertThat(bootstraps).allSatisfy(bootstrap -> {
            assertThat(bootstrap.backendName()).isEqualTo("mongodb");
            assertThat(bootstrap.getClass().getClassLoader()).isSameAs(loader);
        });
        assertThat(bootstraps.stream().map(PersistenceBackendBootstrap::backendName).collect(Collectors.toSet()))
                .containsExactly("mongodb");
    }

    private static List<PersistenceBackendBootstrap> loadBootstraps(final ClassLoader loader) {
        return ServiceLoader.load(PersistenceBackendBootstrap.class, loader).stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toList());
    }

    /**
     * A test journal over its own collections, layered over the MongoDB persistence profile and every
     * {@code reference.conf}, all parsed through the child loader so the profile and the plugin defaults come from the
     * shaded persistence JAR.
     */
    private static Config roundTripConfig(final ClassLoader loader, final String mongoUri) {
        final Config testJournal = ConfigFactory.parseString(String.join("\n",
                "pekko.contrib.persistence.mongodb.mongo.mongouri = \"" + mongoUri + "\"",
                "ditto.mongodb.uri = \"" + mongoUri + "\"",
                "pekko.persistence.journal.plugin = \"" + TEST_JOURNAL + "\"",
                "pekko.persistence.snapshot-store.plugin = \"pekko.persistence.no-snapshot-store\"",
                TEST_JOURNAL + " {",
                "  class = \"" + MONGO_JOURNAL + "\"",
                "  overrides {",
                "    journal-collection = \"classloading_it_journal\"",
                "    journal-index = \"classloading_it_journal_index\"",
                "    realtime-collection = \"classloading_it_realtime\"",
                "    metadata-collection = \"classloading_it_metadata\"",
                "    metadata-index = \"classloading_it_metadata_index\"",
                "  }",
                "}"));
        final Config profile = ConfigFactory.parseResources(loader, "ditto-mongo-persistence.conf",
                ConfigParseOptions.defaults().setAllowMissing(false));
        return testJournal.withFallback(profile)
                .withFallback(ConfigFactory.defaultReference(loader))
                .resolve();
    }

    private static Object ask(final ActorRef actor, final Object message) throws Exception {
        return Patterns.ask(actor, message, ASK_TIMEOUT).toCompletableFuture()
                .get(ASK_TIMEOUT.toSeconds() + 5, TimeUnit.SECONDS);
    }

    private static void terminate(final ActorSystem system) throws Exception {
        system.terminate();
        Await.ready(system.whenTerminated(), scala.concurrent.duration.Duration.apply(30, TimeUnit.SECONDS));
    }

    /**
     * Persists {@link #EVENTS} on {@link #PERSIST}; answers {@link #RECOVERED} with the events it recovered.
     */
    public static final class RoundTripActor extends AbstractPersistentActor {

        static final String PERSIST = "persist";
        static final String PERSISTED = "persisted";
        static final String RECOVERED = "recovered";
        static final List<String> EVENTS = List.of("first-event", "second-event");

        private final String persistenceId;
        private final List<String> recovered = new ArrayList<>();

        public RoundTripActor(final String persistenceId) {
            this.persistenceId = persistenceId;
        }

        @Override
        public String persistenceId() {
            return persistenceId;
        }

        @Override
        public Receive createReceiveRecover() {
            return receiveBuilder()
                    .match(String.class, recovered::add)
                    .match(RecoveryCompleted.class, completed -> {})
                    .build();
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .matchEquals(PERSIST, command -> {
                        final ActorRef sender = getSender();
                        persistAll(EVENTS, event -> {});
                        deferAsync(PERSISTED, done -> sender.tell(PERSISTED, getSelf()));
                    })
                    .matchEquals(RECOVERED, command -> getSender().tell(List.copyOf(recovered), getSelf()))
                    .build();
        }

    }

}
