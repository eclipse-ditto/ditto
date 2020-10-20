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

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDesiredProperties;

/**
 * An immutable implementation of {@link RetrieveFeatureDesiredPropertiesLiveCommand}.
 *
 * @since 1.4.0
 */
@ParametersAreNonnullByDefault
@Immutable
final class RetrieveFeatureDesiredPropertiesLiveCommandImpl
        extends AbstractQueryLiveCommand<RetrieveFeatureDesiredPropertiesLiveCommand,
        RetrieveFeatureDesiredPropertiesLiveCommandAnswerBuilder>
        implements RetrieveFeatureDesiredPropertiesLiveCommand {

    private final String featureId;

    private RetrieveFeatureDesiredPropertiesLiveCommandImpl(final RetrieveFeatureDesiredProperties command) {
        super(command);
        featureId = command.getFeatureId();
    }

    /**
     * Returns an instance of {@code RetrieveFeatureDesiredPropertiesLiveCommandImpl}.
     *
     * @param command the command to base the result on.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws ClassCastException if {@code command} is not an instance of {@link RetrieveFeatureDesiredProperties}.
     */
    @Nonnull
    public static RetrieveFeatureDesiredPropertiesLiveCommandImpl of(final Command<?> command) {
        return new RetrieveFeatureDesiredPropertiesLiveCommandImpl((RetrieveFeatureDesiredProperties) command);
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Override
    public RetrieveFeatureDesiredPropertiesLiveCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(RetrieveFeatureDesiredProperties.of(getThingEntityId(), getFeatureId(),
                getSelectedFields().orElse(null),
                dittoHeaders));
    }

    @Nonnull
    @Override
    public RetrieveFeatureDesiredPropertiesLiveCommandAnswerBuilder answer() {
        return RetrieveFeatureDesiredPropertiesLiveCommandAnswerBuilderImpl.newInstance(this);
    }

    @Nonnull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
