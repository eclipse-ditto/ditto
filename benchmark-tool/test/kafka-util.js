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
import * as common from './common.js';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

import {
    SCHEMA_TYPE_JSON,
    SCHEMA_TYPE_STRING
} from 'k6/x/kafka';

const DITTO_COMMANDS_MODIFY_TOPIC_PATH = 'things/twin/commands/modify';
const THING_PERSISTED_ACK = 'twin-persisted';

const DEVICE_FEATURE_PATH = `features/${common.DEVICE_FEATURE_NAME}/properties/${common.DEVICE_FEATURE_PROPERTY}`

export function sendCreateThingsAndPolicies(thingIds, producer, consumer) {
    console.log('create ' + common.THINGS_COUNT + ' things')
    let messages = [];
    let batchNum = 0;
    thingIds.forEach(thingId => {
        let headers = {
            [common.DEVICE_ID_HEADER]: thingId,
            [common.CREATE_THING_HEADER]: 1
        };

        messages.push({
            headers: headers,
            key: common.schemaRegistry.serialize({
                data: thingId,
                schemaType: SCHEMA_TYPE_STRING
            }),
            value: null
        });

        if (messages.length === common.PRODUCE_THINGS_BATCH_SIZE) {
            console.log('produce batch of messages..');
            producer.produce({ messages: messages });
            let from = batchNum * common.PRODUCE_THINGS_BATCH_SIZE;
            consumeAndValidateThingsCreated(new Set(thingIds.slice(from, from + common.PRODUCE_THINGS_BATCH_SIZE - 1)), consumer);
            messages = [];
            batchNum++;
        }
    });
    if (messages.length > 0) {
        producer.produce({ messages: messages });
        consumeAndValidateThingsCreated(new Set(thingIds.slice(batchNum * common.PRODUCE_THINGS_BATCH_SIZE)), consumer);
    }
}

export function consumeAndValidateThingsCreated(thingIdsSet, consumer) {
    while (thingIdsSet.size > 0) {
        try {
            let messages = consumer.consume({ limit: 1 });
            messages.forEach(message => {
                let consumedEvent = common.schemaRegistry.deserialize({
                    data: message.value,
                    schemaType: SCHEMA_TYPE_JSON,
                });
                let thingId = consumedEvent.value.thingId;
                thingIdsSet.delete(thingId);
                if (__ENV.LOG_REMAINING == 1) {
                    console.log('things remaining:');
                    console.log([...thingIdsSet]);
                }
            });
        } catch (readTimeoutExc) {
            console.log('timed out waiting for kafka message')
            throw readTimeoutExc;
        }
    }
}

export function sendModifyThing(producer, thingId) {
    producer.produce({
        messages: [{
            headers: {
                [common.DEVICE_ID_HEADER]: thingId,
                [common.DITTO_MESSAGE_HEADER]: 1,
            },
            value: common.schemaRegistry.serialize({
                data: constructModifyThingMessage(common.DEVICE_NAMESPACE, thingId.split(':')[1], DEVICE_FEATURE_PATH, randomIntBetween(1, 1000)),
                schemaType: SCHEMA_TYPE_JSON
            })
        }]
    })
}

export function sendModifyThings(producer, thingIds) {
    let messages = [];
    thingIds.forEach(thingId => {
        let headers = {
            [common.DEVICE_ID_HEADER]: thingId,
            [common.DITTO_MESSAGE_HEADER]: 1
        };

        messages.push({
            headers: headers,
            key: common.schemaRegistry.serialize({
                data: thingId,
                schemaType: SCHEMA_TYPE_STRING
            }),
            value: common.schemaRegistry.serialize({
                data: constructModifyThingMessage(common.DEVICE_NAMESPACE, thingId.split(':')[1], DEVICE_FEATURE_PATH, randomIntBetween(1, 1000)),
                schemaType: SCHEMA_TYPE_JSON
            })
        });
    });
    producer.produce({ messages: messages });
}

function constructModifyThingMessage(namespace, thingId, featurePath, value) {
    return {
        'topic': `${namespace}/${thingId}/${DITTO_COMMANDS_MODIFY_TOPIC_PATH}`,
        'headers': {
            'requested-acks': [THING_PERSISTED_ACK]
        },
        'path': featurePath,
        'value': value
    };
}
