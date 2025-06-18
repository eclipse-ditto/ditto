/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.enforcement.pre;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.cluster.ddata.ORSet;
import org.apache.pekko.cluster.ddata.ORSetKey;
import org.apache.pekko.cluster.ddata.Replicator;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.service.persistence.actors.strategies.commands.WotValidationConfigDData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks for the existence of WoT validation configurations using DData.
 */
public class WotValidationConfigExistenceChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            WotValidationConfigExistenceChecker.class);


    private final WotValidationConfigDData ddata;
    private final Executor executor;
    private final AtomicReference<Set<WotValidationConfig>> localConfigs;
    private final ActorRef ddataChangeListener;
    private final AtomicReference<Boolean> isInitialized = new AtomicReference<>(false);

    public WotValidationConfigExistenceChecker(final ActorSystem actorSystem) {
        this.ddata = WotValidationConfigDData.of(actorSystem);
        this.executor = actorSystem.dispatcher();
        this.localConfigs = new AtomicReference<>(Collections.emptySet());
        this.ddataChangeListener = actorSystem.actorOf(Props.create(DDataChangeListener.class, this));
        initialize();
    }

    private void initialize() {
        LOGGER.debug("Initializing WotValidationConfigExistenceChecker...");
        ddata.getConfigs()
                .thenAccept(configs -> {
                    final Set<JsonObject> jsonConfigs = configs.getElements();
                    if (!jsonConfigs.isEmpty()) {
                        Set<WotValidationConfig> initialConfigs = jsonConfigs.stream()
                                .map(WotValidationConfig::fromJson)
                                .collect(java.util.stream.Collectors.toSet());
                        localConfigs.set(initialConfigs);
                        LOGGER.debug("WotValidationConfigExistenceChecker initialized with {} configs",
                                initialConfigs.size());
                    } else {
                        localConfigs.set(Collections.emptySet());
                        LOGGER.debug("WotValidationConfigExistenceChecker initialized with 0 configs (empty set)");
                    }
                    isInitialized.set(true);
                })
                .exceptionally(error -> {
                    LOGGER.error("Failed to initialize WotValidationConfigExistenceChecker: {}", error.getMessage(),
                            error);
                    isInitialized.set(true);
                    return null;
                });
    }

    private static class DDataChangeListener extends AbstractActor {

        private final WotValidationConfigExistenceChecker checker;
        private final AtomicReference<WotValidationConfigId> currentCheckId = new AtomicReference<>();
        private final AtomicReference<Boolean> isInitialized = new AtomicReference<>(false);

        DDataChangeListener(WotValidationConfigExistenceChecker checker) {
            this.checker = checker;
        }

        @Override
        public void preStart() {
            checker.ddata.subscribeForChanges(getSelf());
            isInitialized.set(true);
            LOGGER.debug("DDataChangeListener started and subscribed to changes");
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(Replicator.Changed.class, changed -> {
                        if (!isInitialized.get()) {
                            LOGGER.debug("Ignoring DData change before initialization");
                            return;
                        }

                        if (changed.key().equals(ORSetKey.create("WotValidationConfig"))) {
                            final Set<JsonObject> jsonConfigs = ((ORSet<JsonObject>) changed.dataValue()).getElements();
                            Set<WotValidationConfig> newConfigs = jsonConfigs.stream()
                                    .map(WotValidationConfig::fromJson)
                                    .collect(java.util.stream.Collectors.toSet());

                            checker.updateLocalConfigs(newConfigs);
                            LOGGER.debug("Updated local configs with {} configs", newConfigs.size());
                        }
                    })
                    .match(WotValidationConfigId.class, configId -> {
                        currentCheckId.set(configId);
                        LOGGER.debug("Set current check ID to: {}", configId);
                    })
                    .build();
        }
    }

    private void updateLocalConfigs(final Set<WotValidationConfig> newConfigs) {
        localConfigs.set(newConfigs);
        LOGGER.debug("Updated local configs cache with {} configs", newConfigs.size());
    }

    public CompletionStage<Boolean> checkExistence(final WotValidationConfigId configId) {
        if (!isInitialized.get()) {
            LOGGER.warn("WotValidationConfigExistenceChecker not yet initialized, waiting...");
            return CompletableFuture.supplyAsync(() -> {
                        try {
                            for (int i = 0; i < 50 && !isInitialized.get(); i++) {
                                Thread.sleep(100);
                            }
                            if (!isInitialized.get()) {
                                LOGGER.error("WotValidationConfigExistenceChecker initialization timeout");
                                return false;
                            }
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                            LOGGER.error("Interrupted while waiting for initialization", e);
                            return false;
                        }
                        return true;
                    }, executor)
                    .thenCompose(initialized -> {
                        if (!initialized) {
                            return CompletableFuture.completedFuture(false);
                        }
                        return doCheckExistence(configId);
                    });
        }
        return doCheckExistence(configId);
    }

    private CompletionStage<Boolean> doCheckExistence(final WotValidationConfigId configId) {
        final Set<WotValidationConfig> currentConfigs = localConfigs.get();
        if (currentConfigs.isEmpty()) {
            LOGGER.debug("Local cache is empty for config {}, returning false", configId);
            return CompletableFuture.completedFuture(false);
        }

        final boolean exists = currentConfigs.stream()
                .anyMatch(config -> config.getConfigId().equals(configId));
        LOGGER.debug("Local cache check for config {}: {}", configId, exists ? "exists" : "does not exist");
        return CompletableFuture.completedFuture(exists);
    }
}
