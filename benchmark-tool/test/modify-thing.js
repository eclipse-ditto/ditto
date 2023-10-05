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
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { sendModifyThing } from './kafka-util.js';
import * as common from './common.js'

export function modifyThing() {
    let thingId = common.GET_THING_ID(randomIntBetween(common.THINGS_START_INDEX, common.THINGS_COUNT - 1));
    sendModifyThing(common.KAFKA_CREATE_UPDATE_PRODUCER, thingId);
}
