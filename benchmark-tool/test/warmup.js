/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
import { getThingsBatch } from './http-util.js';
import * as common from './common.js'

export function warmup() {
    console.log(`WARMING UP ${common.THINGS_COUNT} THINGS IN BATCH BY ${common.BATCH_SIZE}`);

    let thingIds = [];
    for (let i = 0; i < common.THINGS_COUNT; i++) {
        thingIds.push(common.GET_THING_ID(i + common.THINGS_START_INDEX));

        if (thingIds.length === common.BATCH_SIZE) {
            let responses = getThingsBatch(thingIds)
            responses.forEach(response => {
                if (response.status != 200) {
                    console.log(`Failed to warmup thing.`);
                    console.log(response);
                }
            });
            thingIds = [];
        }
    }

    if (thingIds.length > 0) {
        let responses = getThingsBatch(thingIds)
        responses.forEach(response => {
            if (response.status != 200) {
                console.log(`Failed to warmup thing.`);
                console.log(response);
            }
        });
    }

    console.log("WARMED UP THINGS");
}
