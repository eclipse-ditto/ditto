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

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.service.signaltransformer.SignalTransformer;
import org.eclipse.ditto.things.model.devops.DynamicValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.commands.CreateWotValidationConfig;
import org.eclipse.ditto.things.model.devops.commands.MergeDynamicConfigSection;
import org.eclipse.ditto.things.model.devops.commands.ModifyWotValidationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

/**
 * Transforms a ModifyWotValidationConfig or ModifyDynamicConfigSection command into a CreateWotValidationConfig
 * if the config does not exist already.
 */
public final class ModifyToCreateWotValidationConfigTransformer implements SignalTransformer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModifyToCreateWotValidationConfigTransformer.class);

    private final WotValidationConfigExistenceChecker existenceChecker;

    public ModifyToCreateWotValidationConfigTransformer(final ActorSystem actorSystem) {
        this(new WotValidationConfigExistenceChecker(actorSystem));
    }

    public ModifyToCreateWotValidationConfigTransformer(final WotValidationConfigExistenceChecker existenceChecker) {
        this.existenceChecker = existenceChecker;
    }

    public ModifyToCreateWotValidationConfigTransformer(final ActorSystem actorSystem, final Config config) {
        this(actorSystem);
    }

    @Override
    public CompletionStage<Signal<?>> apply(final Signal<?> signal, final ActorRef thisRef) {
        if (signal instanceof ModifyWotValidationConfig modifyCmd) {
            return handleModifyWotValidationConfig(modifyCmd);
        } else if (signal instanceof MergeDynamicConfigSection mergeCmd) {
            return handleMergeDynamicConfigSection(mergeCmd);
        }
        return CompletableFuture.completedFuture(signal);
    }

    private CompletionStage<Signal<?>> handleModifyWotValidationConfig(final ModifyWotValidationConfig modifyCmd) {
        final WotValidationConfigId configId = modifyCmd.getEntityId();
        final WotValidationConfig config = modifyCmd.getValidationConfig();

        LOGGER.debug("Checking existence for WoT validation config: {}", configId);
        return existenceChecker.checkExistence(configId)
                .thenApply(exists -> {
                    if (!exists) {
                        LOGGER.debug("Transforming ModifyWotValidationConfig to CreateWotValidationConfig for: {}", configId);
                        return CreateWotValidationConfig.of(configId, config, modifyCmd.getDittoHeaders());
                    }

                    LOGGER.debug("Keeping ModifyWotValidationConfig for existing config: {}", configId);
                    return modifyCmd.setDittoHeaders(modifyCmd.getDittoHeaders());
                });
    }

    private CompletionStage<Signal<?>> handleMergeDynamicConfigSection(final MergeDynamicConfigSection mergeCmd) {
        final WotValidationConfigId configId = mergeCmd.getEntityId();
        final String scopeId = mergeCmd.getScopeId();
        final DynamicValidationConfig dynamicConfig = mergeCmd.getDynamicConfigSection();

        LOGGER.debug("Checking existence for WoT validation config with merge section {}: {}", scopeId, configId);
        return existenceChecker.checkExistence(configId)
                .thenApply(exists -> {
                    if (!exists) {
                        LOGGER.debug("Transforming MergeDynamicConfigSection to CreateWotValidationConfig for: {} with scope: {}", configId, scopeId);
                        final Instant now = Instant.now();
                        final WotValidationConfig newConfig = WotValidationConfig.of(
                                configId,
                                null, // enabled
                                null, // logWarningInsteadOfFailingApiCalls
                                null, // thingConfig
                                null, // featureConfig
                                Collections.singletonList(dynamicConfig), // dynamicConfig
                                null, // revision
                                now, // created
                                now, // modified
                                false, // deleted
                                null // metadata
                        );
                        return CreateWotValidationConfig.of(configId, newConfig, mergeCmd.getDittoHeaders());
                    }

                    LOGGER.debug("Keeping MergeDynamicConfigSection for existing config: {} with scope: {}", configId, scopeId);
                    return mergeCmd.setDittoHeaders(mergeCmd.getDittoHeaders());
                });
    }
} 