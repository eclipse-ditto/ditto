/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDesiredProperty;

/**
 * An immutable implementation of {@link RetrieveFeatureDesiredPropertyLiveCommand}.
 *
 * @since 1.4.0
 */
@ParametersAreNonnullByDefault
@Immutable
final class RetrieveFeatureDesiredPropertyLiveCommandImpl extends AbstractQueryLiveCommand<RetrieveFeatureDesiredPropertyLiveCommand,
        RetrieveFeatureDesiredPropertyLiveCommandAnswerBuilder> implements RetrieveFeatureDesiredPropertyLiveCommand {

    private final String featureId;
    private final JsonPointer desiredPropertyPointer;

    private RetrieveFeatureDesiredPropertyLiveCommandImpl(final RetrieveFeatureDesiredProperty command) {
        super(command);
        featureId = command.getFeatureId();
        desiredPropertyPointer = command.getDesiredPropertyPointer();
    }

    /**
     * Returns an instance of {@code RetrieveFeatureDesiredPropertyLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link RetrieveFeatureDesiredProperty}.
     */
    @Nonnull
    public static RetrieveFeatureDesiredPropertyLiveCommandImpl of(final Command<?> command) {
        return new RetrieveFeatureDesiredPropertyLiveCommandImpl((RetrieveFeatureDesiredProperty) command);
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Nonnull
    @Override
    public JsonPointer getDesiredPropertyPointer() {
        return desiredPropertyPointer;
    }

    @Override
    public RetrieveFeatureDesiredPropertyLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(RetrieveFeatureDesiredProperty.of(getThingEntityId(), getFeatureId(), getDesiredPropertyPointer(), dittoHeaders));
    }

    @Nonnull
    @Override
    public RetrieveFeatureDesiredPropertyLiveCommandAnswerBuilder answer() {
        return RetrieveFeatureDesiredPropertyLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
