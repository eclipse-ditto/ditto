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
import {
    getConfig,
    OPERATION_GET_THINGS_BATCH
} from '../common';
import { executeWithMetrics } from './scenario-utils';
import { HttpChannel } from '../channels/http-channel';
import { debugLog, parseDuration } from '../utils';
import { CHANNEL_HTTP, getThingId, WARMUP } from '../config';

/**
 * WARMUP scenario - pre-cache all things by reading them in batches
 * Only supports HTTP channel
 */
export const warmupScenario: Scenario = {
    async execute(context: any, events: any): Promise<void> {
        const config = getConfig();
        const channel = context.vars?.channel || CHANNEL_HTTP;
        const channelClient = new HttpChannel();

        debugLog(`=== ${WARMUP} SCENARIO (${channel}) ===`);

        const batchSize = config.warmup.batchSize;
        const maxDuration = parseDuration(config.warmup.maxDuration);
        const startTime = Date.now();
        let totalRequests = 0;
        let errorCount = 0;

        try {
            for (let i = 0; i < config.things.count; i += batchSize) {
                if (Date.now() - startTime > maxDuration) {
                    debugLog('WARMUP max duration reached');
                    break;
                }

                const thingIds: string[] = [];
                for (let j = i; j < Math.min(i + batchSize, config.things.count); j++) {
                    thingIds.push(getThingId(config, j));
                }

                try {
                    await executeWithMetrics(
                        () => channelClient.getThingsBatch({ thingIds }),
                        OPERATION_GET_THINGS_BATCH,
                        events,
                        context
                    );
                    totalRequests += thingIds.length;
                } catch (error: any) {
                    errorCount += thingIds.length;
                    debugLog(`Failed to warmup batch:`, error.message);
                }
                debugLog(`Warmed up ${Math.min(i + batchSize, config.things.count)} / ${config.things.count} things`);
            }

            console.log(`${WARMUP} (${channel}) completed: ${totalRequests} requests, ${errorCount} errors`);
        } catch (error) {
            console.error('WARMUP fail:', error);
        }
    }
};
