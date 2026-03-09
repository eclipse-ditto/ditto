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

import { Scenario } from '../interfaces';
import { getConfig, OPERATION_GET_THING } from '../common';
import { executeWithMetrics } from './scenario-utils';
import { HttpChannel } from '../channels/http-channel';
import { getRandomThingId } from '../config';

/**
 * READ_THINGS scenario - read a random thing (HTTP only)
 */
export const readThingsScenario: Scenario = {
    async execute(context: any, events: any): Promise<void> {
        const channelClient = new HttpChannel();
        const thingId = getRandomThingId(getConfig());

        try {
            await executeWithMetrics(
                () => channelClient.getThing({ thingId }),
                OPERATION_GET_THING,
                events,
                context
            );
        } catch (error: any) {
            console.error(`[Scenario: ${context.vars.scenarioName}] Failed to read thing ${thingId}:`, error);
        }
    }
};
