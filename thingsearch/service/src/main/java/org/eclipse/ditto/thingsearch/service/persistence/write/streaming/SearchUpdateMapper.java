/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;
import org.eclipse.ditto.thingsearch.service.common.config.SearchConfig;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.service.updater.actors.MongoWriteModel;
import org.slf4j.Logger;

import akka.NotUsed;
import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.stream.javadsl.Source;

/**
 * Search Update Mapper to be loaded by reflection.
 * Can be used as an extension point to use custom map search updates.
 * Implementations MUST have a public constructor taking an actorSystem as argument.
 *
 * @since 2.1.0
 */
public abstract class SearchUpdateMapper implements DittoExtensionPoint {

    private static final ExtensionId EXTENSION_ID = new ExtensionId();

    protected final ActorSystem actorSystem;

    protected SearchUpdateMapper(final ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Gets the write models of the search updates and processes them.
     *
     * @param writeModels the write models.
     * @return Ditto write models together with their processed MongoDB write models.
     */
    public abstract Source<List<MongoWriteModel>, NotUsed> processWriteModels(List<AbstractWriteModel> writeModels);

    /**
     * Load a {@code SearchUpdateListener} dynamically according to the search configuration.
     *
     * @param actorSystem The actor system in which to load the listener.
     * @return The listener.
     */
    public static SearchUpdateMapper get(final ActorSystem actorSystem) {
        return EXTENSION_ID.get(actorSystem);
    }

    /**
     * Convert a write model to an incremental update model.
     *
     * @param model the write model.
     * @param logger the logger.
     * @return a singleton list of write model together with its update document, or an empty list if there is no
     * change.
     */
    protected static CompletionStage<List<MongoWriteModel>>
    toIncrementalMongo(final AbstractWriteModel model, final Logger logger) {

        return model.toIncrementalMongo()
                .thenApply(mongoWriteModelOpt -> {
                    if (mongoWriteModelOpt.isEmpty()) {
                        logger.debug("Write model is unchanged, skipping update: <{}>", model);
                        model.getMetadata().sendWeakAck(null);
                        model.getMetadata().sendBulkWriteCompleteToOrigin(null);
                        return List.<MongoWriteModel>of();
                    } else {
                        ConsistencyLag.startS5MongoBulkWrite(model.getMetadata());
                        final var result = mongoWriteModelOpt.orElseThrow();
                        logger.debug("MongoWriteModel={}", result);
                        return List.of(result);
                    }
                })
                .handle((result, error) -> {
                    if (result != null) {
                        return result;
                    } else {
                        logger.error("Failed to compute write model " + model, error);
                        try {
                            model.getMetadata().getTimers().forEach(StartedTimer::stop);
                        } catch (final Exception e) {
                            // tolerate stopping stopped timers
                        }
                        return List.of();
                    }
                });
    }

    /**
     * Convert a list of write models to incremental update models.
     *
     * @param models the list of write models.
     * @param logger the logger.
     * @return a list of write models together with their update documents.
     */
    protected static CompletionStage<List<MongoWriteModel>> toIncrementalMongo(
            final Collection<AbstractWriteModel> models, final Logger logger) {

        final var writeModelFutures = models.stream()
                .map(model -> toIncrementalMongo(model, logger))
                .map(CompletionStage::toCompletableFuture)
                .toList();

        final var allFutures = CompletableFuture.allOf(writeModelFutures.toArray(CompletableFuture[]::new));
        return allFutures.thenApply(aVoid ->
                writeModelFutures.stream().flatMap(future -> future.join().stream()).collect(Collectors.toList())
        );
    }

    /**
     * ID of the actor system extension to validate the {@code SearchUpdateListener}.
     */
    private static final class ExtensionId extends AbstractExtensionId<SearchUpdateMapper> {

        @Override
        public SearchUpdateMapper createExtension(final ExtendedActorSystem system) {
            final SearchConfig searchConfig =
                    DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(
                            system.settings().config()));

            return AkkaClassLoader.instantiate(system, SearchUpdateMapper.class,
                    searchConfig.getSearchUpdateMapperImplementation(),
                    List.of(ActorSystem.class),
                    List.of(system));
        }
    }

}
