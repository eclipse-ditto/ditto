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
package org.eclipse.ditto.signals.commands.live.query;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty;

/**
 * An immutable implementation of {@link RetrieveFeaturePropertyLiveCommand}.
 */
@ParametersAreNonnullByDefault
@Immutable
final class RetrieveFeaturePropertyLiveCommandImpl extends AbstractQueryLiveCommand<RetrieveFeaturePropertyLiveCommand,
        RetrieveFeaturePropertyLiveCommandAnswerBuilder> implements RetrieveFeaturePropertyLiveCommand {

    private final String featureId;
    private final JsonPointer propertyPointer;

    private RetrieveFeaturePropertyLiveCommandImpl(final RetrieveFeatureProperty command) {
        super(command);
        featureId = command.getFeatureId();
        propertyPointer = command.getPropertyPointer();
    }

    /**
     * Returns an instance of {@code RetrieveFeaturePropertyLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link RetrieveFeatureProperty}.
     */
    @Nonnull
    public static RetrieveFeaturePropertyLiveCommandImpl of(final Command<?> command) {
        return new RetrieveFeaturePropertyLiveCommandImpl((RetrieveFeatureProperty) command);
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Nonnull
    @Override
    public JsonPointer getPropertyPointer() {
        return propertyPointer;
    }

    @Override
    public RetrieveFeaturePropertyLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(RetrieveFeatureProperty.of(getThingEntityId(), getFeatureId(), getPropertyPointer(), dittoHeaders));
    }

    @Nonnull
    @Override
    public RetrieveFeaturePropertyLiveCommandAnswerBuilder answer() {
        return RetrieveFeaturePropertyLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
