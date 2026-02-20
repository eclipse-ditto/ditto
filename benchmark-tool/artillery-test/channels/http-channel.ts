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

import * as httpUtil from '../http-util';
import { ThingReader, ThingSearcher, ThingModifier, LiveMessenger } from '../interfaces';

/**
 * HTTP Channel - Uses Ditto HTTP API
 */
export class HttpChannel implements ThingReader, ThingSearcher, ThingModifier, LiveMessenger {

    /**
     * Get a single thing by ID
     * @param params - { thingId }
     */
    async getThing({ thingId }: { thingId: string }): Promise<any> {
        return await httpUtil.getThing(thingId);
    }

    /**
     * Get multiple things in batch
     * @param params - { thingIds: string[] }
     */
    async getThingsBatch({ thingIds }: { thingIds: string[] }): Promise<any[]> {
        return await httpUtil.getThingsBatch(thingIds);
    }

    /**
     * Search for things using filter
     * @param params - { filter: string }
     */
    async searchThings({ filter }: { filter: string }): Promise<any> {
        return await httpUtil.searchThingById(filter);
    }

    /**
     * Modify a thing (update attributes)
     * @param params - { thingId, updates: { path, value }, timeout }
     */
    async modifyThing({ thingId, updates, timeout }: { thingId: string; updates: { path: string; value: any }; timeout?: number }): Promise<any> {
        return await httpUtil.modifyThingAttribute(thingId, updates.path, updates.value, timeout);
    }

    /**
     * Send live message to a thing
     * @param params - { thingId, subject, payload, timeout }
     */
    async sendLiveMessage({ thingId, subject, payload, timeout }: { thingId: string; subject: string; payload: any; timeout?: number }): Promise<any> {
        return await httpUtil.sendLiveMessageToThing(thingId, subject, payload, timeout);
    }
}
