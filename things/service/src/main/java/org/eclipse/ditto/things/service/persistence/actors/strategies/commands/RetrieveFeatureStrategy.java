/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.signals.FeatureToggle;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeature;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveWotThingDescriptionResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.wot.integration.provider.WotThingDescriptionProvider;

import akka.actor.ActorSystem;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeature} command.
 */
@Immutable
final class RetrieveFeatureStrategy extends AbstractThingCommandStrategy<RetrieveFeature> {

    private final WotThingDescriptionProvider wotThingDescriptionProvider;

    /**
     * Constructs a new {@code RetrieveFeatureStrategy} object.
     *
     * @param actorSystem the actor system to use for loading the WoT extension.
     */
    RetrieveFeatureStrategy(final ActorSystem actorSystem) {
        super(RetrieveFeature.class);
        wotThingDescriptionProvider = WotThingDescriptionProvider.get(actorSystem);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final RetrieveFeature command,
            @Nullable final Metadata metadata) {

        final ThingId thingId = context.getState();

        final boolean wotThingDescriptionRequested = command.getDittoHeaders().getAccept()
                .filter(ContentType.APPLICATION_TD_JSON.getValue()::equals)
                .isPresent();

        if (wotThingDescriptionRequested) {
            FeatureToggle.checkWotIntegrationFeatureEnabled(command.getType(), command.getDittoHeaders());
            try {
                // don't apply preconditions and don't provide etag in response
                final RetrieveFeature commandWithoutPreconditions = command.setDittoHeaders(
                        command.getDittoHeaders().toBuilder().removePreconditionHeaders().build());
                return ResultFactory.newQueryResult(commandWithoutPreconditions,
                        getRetrieveThingDescriptionResponse(thing, commandWithoutPreconditions));
            } catch (final DittoRuntimeException e) {
                return ResultFactory.newErrorResult(e, command);
            }
        } else {
            return extractFeatures(thing)
                    .map(features -> getFeatureResult(features, thingId, command, thing))
                    .orElseGet(() -> ResultFactory.newErrorResult(ExceptionFactory.featureNotFound(thingId,
                            command.getFeatureId(), command.getDittoHeaders()), command));
        }
    }

    private DittoHeadersSettable<?> getRetrieveThingDescriptionResponse(@Nullable final Thing thing,
            final RetrieveFeature command) {
        final String featureId = command.getFeatureId();
        if (thing != null) {
            return thing.getFeatures()
                    .flatMap(f -> f.getFeature(featureId))
                    .map(feature -> wotThingDescriptionProvider
                            .provideFeatureTD(command.getEntityId(), thing, feature, command.getDittoHeaders())
                    )
                    .map(td -> RetrieveWotThingDescriptionResponse.of(command.getEntityId(), td.toJson(),
                            command.getDittoHeaders()
                                    .toBuilder()
                                    .contentType(ContentType.APPLICATION_TD_JSON)
                                    .build())
                    )
                    .map(DittoHeadersSettable.class::cast)
                    .orElseGet(() -> ExceptionFactory.featureNotFound(command.getEntityId(), featureId,
                            command.getDittoHeaders()));
        } else {
            return ExceptionFactory.featureNotFound(command.getEntityId(), featureId,
                    command.getDittoHeaders());
        }
    }

    private Optional<Features> extractFeatures(@Nullable final Thing thing) {
        return getEntityOrThrow(thing).getFeatures();
    }

    private Result<ThingEvent<?>> getFeatureResult(final Features features, final ThingId thingId,
            final RetrieveFeature command, @Nullable final Thing thing) {

        final String featureId = command.getFeatureId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return features.getFeature(featureId)
                .map(feature -> getFeatureJson(feature, command))
                .map(featureJson -> RetrieveFeatureResponse.of(thingId, featureId, featureJson, dittoHeaders))
                .<Result<ThingEvent<?>>>map(response ->
                        ResultFactory.newQueryResult(command, appendETagHeaderIfProvided(command, response, thing)))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(thingId, featureId, dittoHeaders), command));
    }

    private static JsonObject getFeatureJson(final Feature feature, final RetrieveFeature command) {
        return command.getSelectedFields()
                .map(selectedFields -> feature.toJson(command.getImplementedSchemaVersion(), selectedFields))
                .orElseGet(() -> feature.toJson(command.getImplementedSchemaVersion()));
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final RetrieveFeature command, @Nullable final Thing previousEntity) {
        return nextEntityTag(command, previousEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveFeature command, @Nullable final Thing newEntity) {
        return extractFeatures(newEntity)
                .flatMap(features -> features.getFeature(command.getFeatureId()))
                .flatMap(EntityTag::fromEntity);
    }
}
