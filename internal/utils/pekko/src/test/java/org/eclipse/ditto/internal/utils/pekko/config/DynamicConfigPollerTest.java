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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Tests {@link DynamicConfigPoller}.
 */
public final class DynamicConfigPollerTest {

    private ActorSystem system;
    private DynamicConfigWatcherExtension extension;

    @Before
    public void setup() {
        system = ActorSystem.create("DynamicConfigPollerTest",
                ConfigFactory.parseString("ditto.things.thing.activity-check.inactive-interval = 2h"));
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
    public void getReturnsInitialValue() {
        final DynamicConfigPoller<String> poller =
                DynamicConfigPoller.of(system, "test", config -> "parsed", "initial");

        assertThat(poller.get()).isEqualTo("initial");
    }

    @Test
    public void getReturnsSameInstanceWhenVersionUnchanged() {
        final var initial = new Object();
        final DynamicConfigPoller<Object> poller =
                DynamicConfigPoller.of(system, "test", config -> new Object(), initial);

        assertThat(poller.get()).isSameAs(initial);
        assertThat(poller.get()).isSameAs(initial);
    }

    @Test
    public void getRefreshesAfterVersionChange() {
        final DynamicConfigPoller<String> poller =
                DynamicConfigPoller.of(system, "testRefresh", config -> "refreshed", "initial");

        assertThat(poller.get()).isEqualTo("initial");

        // trigger version change
        final var newConfig = ConfigFactory.parseString("ditto.things.thing.activity-check.inactive-interval = 5m")
                .withFallback(system.settings().config());
        extension.updateConfig(newConfig.getConfig("ditto").atKey("ditto"));

        assertThat(poller.get()).isEqualTo("refreshed");
    }

    @Test
    public void getKeepsPreviousValueOnParseFailure() {
        final AtomicInteger callCount = new AtomicInteger(0);
        final DynamicConfigPoller<String> poller =
                DynamicConfigPoller.of(system, "testFailure", config -> {
                    if (callCount.incrementAndGet() > 0) {
                        throw new RuntimeException("parse error");
                    }
                    return "should-not-reach";
                }, "initial");

        assertThat(poller.get()).isEqualTo("initial");

        // trigger version change — parser will throw
        final var newConfig = ConfigFactory.parseString("ditto.things.thing.activity-check.inactive-interval = 5m")
                .withFallback(system.settings().config());
        extension.updateConfig(newConfig.getConfig("ditto").atKey("ditto"));

        // should keep initial value
        assertThat(poller.get()).isEqualTo("initial");
    }

    @Test
    public void getDoesNotRetryAfterFailureUntilNextVersionChange() {
        final AtomicInteger callCount = new AtomicInteger(0);
        final DynamicConfigPoller<String> poller =
                DynamicConfigPoller.of(system, "testNoRetry", config -> {
                    callCount.incrementAndGet();
                    throw new RuntimeException("parse error");
                }, "initial");

        // trigger version change
        final var newConfig = ConfigFactory.parseString("ditto.things.thing.activity-check.inactive-interval = 5m")
                .withFallback(system.settings().config());
        extension.updateConfig(newConfig.getConfig("ditto").atKey("ditto"));

        poller.get(); // first call after version change — triggers parse attempt
        final int countAfterFirst = callCount.get();

        poller.get(); // second call — same version, should NOT retry
        assertThat(callCount.get()).isEqualTo(countAfterFirst);
    }

    @Test
    public void multiplePollersWithSameCacheKeyShareParsedResult() {
        final AtomicInteger parseCount = new AtomicInteger(0);
        final var parser = new java.util.function.Function<com.typesafe.config.Config, String>() {
            @Override
            public String apply(final com.typesafe.config.Config config) {
                parseCount.incrementAndGet();
                return "shared-" + extension.getVersion();
            }
        };

        final DynamicConfigPoller<String> poller1 =
                DynamicConfigPoller.of(system, "sharedKey", parser, "initial1");
        final DynamicConfigPoller<String> poller2 =
                DynamicConfigPoller.of(system, "sharedKey", parser, "initial2");

        // trigger version change
        final var newConfig = ConfigFactory.parseString("ditto.things.thing.activity-check.inactive-interval = 5m")
                .withFallback(system.settings().config());
        extension.updateConfig(newConfig.getConfig("ditto").atKey("ditto"));

        final String result1 = poller1.get();
        final String result2 = poller2.get();

        assertThat(result1).isEqualTo("shared-1");
        assertThat(result2).isEqualTo("shared-1");
        // parser should only be called once thanks to the extension's cache
        assertThat(parseCount.get()).isEqualTo(1);
    }
}
