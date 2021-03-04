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

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeature;

/**
 * An immutable implementation of {@link RetrieveFeatureLiveCommand}.
 */
@ParametersAreNonnullByDefault
@Immutable
final class RetrieveFeatureLiveCommandImpl extends AbstractQueryLiveCommand<RetrieveFeatureLiveCommand,
        RetrieveFeatureLiveCommandAnswerBuilder> implements RetrieveFeatureLiveCommand {

    private final String featureId;

    private RetrieveFeatureLiveCommandImpl(final RetrieveFeature command) {
        super(command);
        featureId = command.getFeatureId();
    }

    /**
     * Returns an instance of {@code RetrieveFeatureLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link RetrieveFeature}.
     */
    @Nonnull
    public static RetrieveFeatureLiveCommandImpl of(final Command<?> command) {
        return new RetrieveFeatureLiveCommandImpl((RetrieveFeature) command);
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Override
    public RetrieveFeatureLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(RetrieveFeature.of(getThingEntityId(), getFeatureId(), getSelectedFields().orElse(null), dittoHeaders));
    }

    @Nonnull
    @Override
    public RetrieveFeatureLiveCommandAnswerBuilder answer() {
        return RetrieveFeatureLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
