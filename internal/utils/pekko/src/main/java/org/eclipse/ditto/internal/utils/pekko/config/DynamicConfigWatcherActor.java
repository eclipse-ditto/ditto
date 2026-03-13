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

import java.io.File;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.pekko.actor.AbstractActorWithTimers;
import org.apache.pekko.actor.Props;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

/**
 * Actor that periodically polls a dynamic config file on disk and updates the
 * {@link DynamicConfigWatcherExtension} when changes are detected.
 */
final class DynamicConfigWatcherActor extends AbstractActorWithTimers {

    /**
     * The name of this actor.
     */
    static final String ACTOR_NAME = "dynamicConfigWatcher";

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private final DynamicConfigWatcherExtension extension;
    private final File configFile;
    private final Config staticConfig;

    private long lastModified = 0L;
    private Config previousDynamicConfig;

    @SuppressWarnings("unused")
    private DynamicConfigWatcherActor(final DynamicConfigWatcherExtension extension,
            final DynamicConfigWatcherConfig watcherConfig,
            final Config staticConfig) {
        this.extension = extension;
        this.configFile = new File(watcherConfig.getFilePath());
        this.staticConfig = staticConfig;
        this.previousDynamicConfig = ConfigFactory.empty();

        getTimers().startTimerWithFixedDelay("poll", Poll.INSTANCE, watcherConfig.getPollInterval());
    }

    static Props props(final DynamicConfigWatcherExtension extension,
            final DynamicConfigWatcherConfig watcherConfig,
            final Config staticConfig) {
        return Props.create(DynamicConfigWatcherActor.class, extension, watcherConfig, staticConfig);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals(Poll.INSTANCE, this::onPoll)
                .build();
    }

    private void onPoll(final Poll poll) {
        if (!configFile.exists()) {
            if (lastModified != 0L) {
                log.info("Dynamic config file <{}> no longer exists, keeping current config.", configFile);
            }
            return;
        }

        final long currentModified = configFile.lastModified();
        if (currentModified == lastModified) {
            return;
        }

        final Config dynamicConfig;
        try {
            dynamicConfig = ConfigFactory.parseFile(configFile);
        } catch (final Exception e) {
            log.warning("Failed to parse dynamic config file <{}>: {}. Keeping current config.",
                    configFile, e.getMessage());
            return;
        }

        lastModified = currentModified;

        if (dynamicConfig.equals(previousDynamicConfig)) {
            return;
        }

        // Merge: dynamic overrides static, then scope to ditto.*
        final Config merged = dynamicConfig.withFallback(staticConfig);
        final Config previousMerged = extension.getDittoConfig();

        if (merged.hasPath("ditto")) {
            final Config newDittoConfig = merged.getConfig("ditto").atKey("ditto");

            if (!newDittoConfig.equals(previousMerged)) {
                final long newVersion = extension.updateConfig(newDittoConfig);

                logChangedKeys(previousMerged, newDittoConfig);

                final DynamicConfigChanged event = new DynamicConfigChanged(
                        newDittoConfig, previousMerged, newVersion, Instant.now());
                getContext().getSystem().eventStream().publish(event);

                log.info("Dynamic config updated to version <{}>.", newVersion);
            }
        }

        previousDynamicConfig = dynamicConfig;
    }

    private void logChangedKeys(final Config previousConfig, final Config newConfig) {
        final ConfigObject previousRoot = previousConfig.root();
        final ConfigObject newRoot = newConfig.root();
        final Set<String> changedPaths = new TreeSet<>();
        collectChangedPaths(previousRoot, newRoot, "", changedPaths);

        if (!changedPaths.isEmpty()) {
            log.info("Changed config paths: <{}>", changedPaths);
        }
    }

    private static void collectChangedPaths(final ConfigObject previous, final ConfigObject current,
            final String prefix, final Set<String> changedPaths) {

        final Set<String> allKeys = new HashSet<>();
        allKeys.addAll(previous.keySet());
        allKeys.addAll(current.keySet());

        for (final String key : allKeys) {
            final String fullPath = prefix.isEmpty() ? key : prefix + "." + key;
            final ConfigValue prevValue = previous.get(key);
            final ConfigValue currValue = current.get(key);

            if (prevValue == null) {
                changedPaths.add(fullPath + " (added)");
            } else if (currValue == null) {
                changedPaths.add(fullPath + " (removed)");
            } else if (prevValue.valueType() == ConfigValueType.OBJECT &&
                    currValue.valueType() == ConfigValueType.OBJECT) {
                collectChangedPaths((ConfigObject) prevValue, (ConfigObject) currValue,
                        fullPath, changedPaths);
            } else if (!prevValue.equals(currValue)) {
                changedPaths.add(fullPath);
            }
        }
    }

    private enum Poll {
        INSTANCE
    }
}
