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
package org.eclipse.ditto.internal.utils.pekko.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Tests {@link DynamicConfigWatcherActor}.
 */
public final class DynamicConfigWatcherActorTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    private ActorSystem system;
    private DynamicConfigWatcherExtension extension;

    @Before
    public void setup() {
        system = ActorSystem.create("DynamicConfigWatcherActorTest",
                ConfigFactory.parseString("ditto.things.thing.activity-check.inactive-interval = 2h")
                        .withFallback(ConfigFactory.empty()));
        extension = DynamicConfigWatcherExtension.get(system);
    }

    @After
    public void tearDown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    @Test
    public void fileNotFoundDoesNotCauseError() {
        new TestKit(system) {{
            final File nonExistentFile = new File(tempFolder.getRoot(), "nonexistent.conf");
            final DynamicConfigWatcherConfig config = createConfig(nonExistentFile, Duration.ofMillis(100));

            system.actorOf(DynamicConfigWatcherActor.props(extension, config,
                    system.settings().config()));

            // Subscribe to config change events
            system.eventStream().subscribe(getRef(), DynamicConfigChanged.class);

            // Wait and verify no event is published
            expectNoMessage(Duration.ofMillis(500));
            assertThat(extension.getVersion()).isEqualTo(0L);
        }};
    }

    @Test
    public void firstDetectionPublishesEvent() throws IOException {
        new TestKit(system) {{
            final File configFile = tempFolder.newFile("dynamic.conf");
            Files.writeString(configFile.toPath(),
                    "ditto.things.thing.activity-check.inactive-interval = 5m");

            final DynamicConfigWatcherConfig config = createConfig(configFile, Duration.ofMillis(100));

            system.eventStream().subscribe(getRef(), DynamicConfigChanged.class);
            system.actorOf(DynamicConfigWatcherActor.props(extension, config,
                    system.settings().config()));

            final DynamicConfigChanged event = expectMsgClass(Duration.ofSeconds(5),
                    DynamicConfigChanged.class);
            assertThat(event.version()).isEqualTo(1L);
            assertThat(event.dittoConfig().hasPath("ditto.things.thing.activity-check.inactive-interval"))
                    .isTrue();
            assertThat(extension.getVersion()).isEqualTo(1L);
        }};
    }

    @Test
    public void unchangedFileDoesNotProduceDuplicateEvent() throws IOException {
        new TestKit(system) {{
            final File configFile = tempFolder.newFile("dynamic.conf");
            Files.writeString(configFile.toPath(),
                    "ditto.things.thing.activity-check.inactive-interval = 5m");

            final DynamicConfigWatcherConfig config = createConfig(configFile, Duration.ofMillis(100));

            system.eventStream().subscribe(getRef(), DynamicConfigChanged.class);
            system.actorOf(DynamicConfigWatcherActor.props(extension, config,
                    system.settings().config()));

            // First event
            expectMsgClass(Duration.ofSeconds(5), DynamicConfigChanged.class);

            // No more events since file hasn't changed
            expectNoMessage(Duration.ofMillis(500));
            assertThat(extension.getVersion()).isEqualTo(1L);
        }};
    }

    @Test
    public void fileChangedProducesNewEvent() throws IOException, InterruptedException {
        new TestKit(system) {{
            final File configFile = tempFolder.newFile("dynamic.conf");
            Files.writeString(configFile.toPath(),
                    "ditto.things.thing.activity-check.inactive-interval = 5m");

            final DynamicConfigWatcherConfig config = createConfig(configFile, Duration.ofMillis(100));

            system.eventStream().subscribe(getRef(), DynamicConfigChanged.class);
            system.actorOf(DynamicConfigWatcherActor.props(extension, config,
                    system.settings().config()));

            // First event
            expectMsgClass(Duration.ofSeconds(5), DynamicConfigChanged.class);

            // Ensure file modification time changes (some filesystems have 1s granularity)
            Thread.sleep(1100);
            Files.writeString(configFile.toPath(),
                    "ditto.things.thing.activity-check.inactive-interval = 10m");

            // Second event
            final DynamicConfigChanged event2 = expectMsgClass(Duration.ofSeconds(5),
                    DynamicConfigChanged.class);
            assertThat(event2.version()).isEqualTo(2L);
            assertThat(extension.getVersion()).isEqualTo(2L);
        }};
    }

    @Test
    public void invalidHoconKeepsOldConfig() throws IOException, InterruptedException {
        new TestKit(system) {{
            final File configFile = tempFolder.newFile("dynamic.conf");
            Files.writeString(configFile.toPath(),
                    "ditto.things.thing.activity-check.inactive-interval = 5m");

            final DynamicConfigWatcherConfig config = createConfig(configFile, Duration.ofMillis(100));

            system.eventStream().subscribe(getRef(), DynamicConfigChanged.class);
            system.actorOf(DynamicConfigWatcherActor.props(extension, config,
                    system.settings().config()));

            // First valid event
            expectMsgClass(Duration.ofSeconds(5), DynamicConfigChanged.class);
            final Config configAfterFirst = extension.getDittoConfig();

            // Write invalid HOCON
            Thread.sleep(1100);
            Files.writeString(configFile.toPath(), "this is { not valid hocon");

            // No new event, config unchanged
            expectNoMessage(Duration.ofMillis(500));
            assertThat(extension.getDittoConfig()).isEqualTo(configAfterFirst);
            assertThat(extension.getVersion()).isEqualTo(1L);
        }};
    }

    @Test
    public void invalidHoconIsRetriedOnNextPollAfterBeingFixed() throws IOException, InterruptedException {
        new TestKit(system) {{
            final File configFile = tempFolder.newFile("dynamic.conf");
            Files.writeString(configFile.toPath(),
                    "ditto.things.thing.activity-check.inactive-interval = 5m");

            final DynamicConfigWatcherConfig config = createConfig(configFile, Duration.ofMillis(100));

            system.eventStream().subscribe(getRef(), DynamicConfigChanged.class);
            system.actorOf(DynamicConfigWatcherActor.props(extension, config,
                    system.settings().config()));

            // First valid event
            expectMsgClass(Duration.ofSeconds(5), DynamicConfigChanged.class);

            // Write invalid HOCON
            Thread.sleep(1100);
            Files.writeString(configFile.toPath(), "this is { not valid hocon");

            // No event for invalid content
            expectNoMessage(Duration.ofMillis(500));

            // Fix the file (without changing modification time — the retry should still work
            // because lastModified was NOT updated after the parse failure)
            Files.writeString(configFile.toPath(),
                    "ditto.things.thing.activity-check.inactive-interval = 10m");

            // The fixed file should be picked up on the next poll
            final DynamicConfigChanged event = expectMsgClass(Duration.ofSeconds(5),
                    DynamicConfigChanged.class);
            assertThat(event.version()).isEqualTo(2L);
        }};
    }

    @Test
    public void fileDeletedAfterBeingPresentDoesNotCrash() throws IOException, InterruptedException {
        new TestKit(system) {{
            final File configFile = tempFolder.newFile("dynamic.conf");
            Files.writeString(configFile.toPath(),
                    "ditto.things.thing.activity-check.inactive-interval = 5m");

            final DynamicConfigWatcherConfig config = createConfig(configFile, Duration.ofMillis(100));

            system.eventStream().subscribe(getRef(), DynamicConfigChanged.class);
            final ActorRef watcher = system.actorOf(DynamicConfigWatcherActor.props(extension, config,
                    system.settings().config()));

            // First event
            expectMsgClass(Duration.ofSeconds(5), DynamicConfigChanged.class);

            // Delete file
            configFile.delete();

            // No crash, no event
            expectNoMessage(Duration.ofMillis(500));

            // Actor is still alive
            assertThat(watcher.isTerminated()).isFalse();
        }};
    }

    private DynamicConfigWatcherConfig createConfig(final File file, final Duration pollInterval) {
        return new DynamicConfigWatcherConfig() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public String getFilePath() {
                return file.getAbsolutePath();
            }

            @Override
            public Duration getPollInterval() {
                return pollInterval;
            }
        };
    }
}
