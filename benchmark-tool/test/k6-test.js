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
export { modifyThing } from './modify-thing.js'
export { searchThing, searchThingBatch } from './search-thing.js'
export { readThing } from './read-thing.js'
export { warmup } from './warmup.js'
export { sendDeviceLiveMessage } from './device-live-message.js'
import * as common from './common.js'
import { sendCreateThingsAndPolicies } from './kafka-util.js';

import {
    Reader
} from 'k6/x/kafka';

import {
    createHttpPushConnection,
    createKafkaSourceConnection,
    createKafkaTargetConnection,
    deleteConnection,
    deletePolicy,
    deleteThing,
    waitForConnectionToOpen,
} from './http-util.js';

export const options = {
    setupTimeout: __ENV.SETUP_TIMEOUT,
    teardownTimeout: __ENV.TEARDOWN_TIMEOUT,

    // will set later
    scenarios: {},

    batch: common.BATCH_SIZE,
    batchPerHost: common.BATCH_SIZE,
};

let WARMUP = 'WARMUP';
let MODIFY_THINGS = 'MODIFY_THINGS';
let SEARCH_THINGS = 'SEARCH_THINGS';
let READ_THINGS = 'READ_THINGS';
let DEVICE_LIVE_MESSAGES = 'DEVICE_LIVE_MESSAGES';

let availableScenarios = {};

availableScenarios[WARMUP] = {
    executor: 'per-vu-iterations',
    maxDuration: __ENV.THINGS_WARMUP_MAX_DURATION,
    exec: 'warmup',
    startTime: __ENV.THINGS_WARMUP_START_TIME
};

availableScenarios[MODIFY_THINGS] = {
    executor: 'constant-arrival-rate',
    duration: __ENV.MODIFY_THINGS_DURATION,
    rate: __ENV.MODIFY_THINGS_PER_SECOND,
    timeUnit: '1s',
    preAllocatedVus: __ENV.MODIFY_THINGS_PRE_ALLOCATED_VUS,
    maxVUs: __ENV.MODIFY_THINGS_MAX_VUS,
    exec: 'modifyThing',
    startTime: __ENV.MODIFY_THINGS_START_TIME
};

availableScenarios[SEARCH_THINGS] = {
    executor: 'constant-arrival-rate',
    duration: __ENV.SEARCH_THINGS_DURATION,
    rate: __ENV.SEARCH_THINGS_PER_SECOND,
    timeUnit: '1s',
    preAllocatedVus: __ENV.SEARCH_THINGS_PRE_ALLOCATED_VUS,
    maxVUs: __ENV.SEARCH_THINGS_MAX_VUS,
    exec: 'searchThing',
    startTime: __ENV.SEARCH_THINGS_START_TIME
};

availableScenarios[READ_THINGS] = {
    executor: 'constant-arrival-rate',
    duration: __ENV.READ_THINGS_DURATION,
    rate: __ENV.READ_THINGS_PER_SECOND,
    timeUnit: '1s',
    preAllocatedVus: __ENV.READ_THINGS_PRE_ALLOCATED_VUS,
    maxVUs: __ENV.READ_THINGS_MAX_VUS,
    exec: 'readThing',
    startTime: __ENV.READ_THINGS_START_TIME
};

availableScenarios[DEVICE_LIVE_MESSAGES] = {
    executor: 'constant-arrival-rate',
    duration: __ENV.DEVICE_LIVE_MESSAGES_DURATION,
    rate: __ENV.DEVICE_LIVE_MESSAGES_PER_SECOND,
    timeUnit: '1s',
    preAllocatedVus: __ENV.DEVICE_LIVE_MESSAGES_PRE_ALLOCATED_VUS,
    maxVUs: __ENV.DEVICE_LIVE_MESSAGES_MAX_VUS,
    exec: 'sendDeviceLiveMessage',
    startTime: __ENV.DEVICE_LIVE_MESSAGES_START_TIME
};

let scenariosToRun = __ENV.SCENARIOS_TO_RUN !== undefined ? __ENV.SCENARIOS_TO_RUN.split(/\s*,\s*/) : undefined;
if (scenariosToRun !== undefined) {
    scenariosToRun.forEach(scenario => {
        if (availableScenarios[scenario] !== undefined) {
            options.scenarios[scenario] = availableScenarios[scenario];
        }
    });
}

export function setup() {
    let httpPushConnection, kafkaTargetConnection, kafkaSourceConnection, consumer;

    if (__ENV.CREATE_DITTO_CONNECTIONS == 1) {
        let httpPushConnectionId = undefined;
        if (shouldCreateHttpPushConnection()) {
            httpPushConnection = createHttpPushConnection();
            waitForConnectionToOpen(httpPushConnection.id);
        }

        if (shouldCreateKafkaSourceConnection()) {
            common.createThingsTopicsIfNotAvailable();
            kafkaSourceConnection = createKafkaSourceConnection(httpPushConnectionId);
            waitForConnectionToOpen(kafkaSourceConnection.id);
        }
    }

    if (shouldCreateKafkaTargetConnection()) {
        consumer = new Reader({
            brokers: common.BOOTSTRAP_SERVERS,
            groupID: common.CREATE_UPDATE_THING_CONSUMER_GROUP_ID,
            groupTopics: [common.CREATE_UPDATE_THING_REPLY_TOPIC],
            connectLogger: __ENV.KAFKA_CONSUMER_LOGGER_ENABLED == 1,
            maxWait: parseInt(__ENV.CREATE_UPDATE_THING_CONSUMER_MAX_WAIT_TIME_S) * 1000000000
        });

        kafkaTargetConnection = createKafkaTargetConnection();
        waitForConnectionToOpen(kafkaTargetConnection.id);

        let thingIds = []
        for (let i = 0; i < common.THINGS_COUNT; i++) {
            thingIds.push(common.GET_THING_ID(i))
        }
        sendCreateThingsAndPolicies(thingIds, common.KAFKA_CREATE_UPDATE_PRODUCER, consumer);
        console.log('created things');
    }

    return {
        kafkaSourceConnection: kafkaSourceConnection,
        kafkaTargetConnection: kafkaTargetConnection,
        httpPushConnection: httpPushConnection
    };
}

export default function () {
    console.log('no scenarios to run configured');
}

export function teardown(config) {
    console.log("TEARDOWN EXECUTING...")
    if (__ENV.DELETE_THINGS == 1) {
        for (let i = 0; i < common.THINGS_COUNT; i++) {
            let thingId = common.GET_THING_ID(i);
            deleteThing(thingId);
            deletePolicy(thingId);
        }
    }
    if (__ENV.DELETE_DITTO_CONNECTIONS == 1) {
        if (config.httpPushConnection != undefined) {
            deleteConnection(config.httpPushConnection.id);
        }

        if (config.kafkaTargetConnection != undefined) {
            deleteConnection(config.kafkaTargetConnection.id);
        }

        if (config.kafkaSourceConnection != undefined) {
            deleteConnection(config.kafkaSourceConnection.id);
        }

        if (__ENV.CREATE_THINGS == 1 || options.scenarios[MODIFY_THINGS] !== undefined) {
            common.deleteTopics();
        }
    }
}


function shouldCreateHttpPushConnection() {
    return scenariosToRun.indexOf(DEVICE_LIVE_MESSAGES) !== -1 || scenariosToRun.indexOf(MODIFY_THINGS) !== -1
}

function shouldCreateKafkaSourceConnection() {
    return __ENV.CREATE_THINGS == 1 || scenariosToRun.indexOf(MODIFY_THINGS) !== -1
}

function shouldCreateKafkaTargetConnection() {
    return __ENV.CREATE_THINGS == 1;
}
