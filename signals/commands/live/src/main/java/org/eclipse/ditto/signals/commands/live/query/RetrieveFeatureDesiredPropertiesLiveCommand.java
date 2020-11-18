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

import org.eclipse.ditto.signals.base.WithFeatureId;
import org.eclipse.ditto.signals.commands.live.base.LiveCommand;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDesiredProperties;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;

/**
 * {@link RetrieveFeatureDesiredProperties} live command giving access to the command and all of its special accessors. Also
 * the entry  point for creating a {@link RetrieveFeatureDesiredPropertiesLiveCommandAnswerBuilder} capable of answering
 * incoming commands.
 *
 * @since 1.5.0
 */
public interface RetrieveFeatureDesiredPropertiesLiveCommand
        extends LiveCommand<RetrieveFeatureDesiredPropertiesLiveCommand, RetrieveFeatureDesiredPropertiesLiveCommandAnswerBuilder>,
        ThingQueryCommand<RetrieveFeatureDesiredPropertiesLiveCommand>, WithFeatureId {
}
