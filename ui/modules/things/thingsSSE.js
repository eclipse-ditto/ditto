/*
* Copyright (c) 2022 Contributors to the Eclipse Foundation
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
/* eslint-disable require-jsdoc */
/* eslint-disable arrow-parens */
// @ts-check
import * as API from '../api.js';
import * as Environments from '../environments/environments.js';

import * as Things from './things.js';

let thingEventSource;


/**
 * Initializes components. Should be called after DOMContentLoaded event
 */
export async function ready() {
  Things.addChangeListener(onThingChanged);
  Environments.addChangeListener(onEnvironmentChanged);
}

const observers = [];
export function addChangeListener(observer) {
  observers.push(observer);
}
function notifyAll(thingJson) {
  observers.forEach(observer => observer.call(null, thingJson));
}

function onThingChanged(newThingJson, isNewThingId) {
  if (!newThingJson) {
    stopSSE();
  } else if (isNewThingId) {
    thingEventSource && thingEventSource.close();
    console.log('Start SSE: ' + newThingJson.thingId);
    thingEventSource = API.getEventSource(newThingJson.thingId,
        '&fields=thingId,attributes,features,_revision,_modified');
    thingEventSource.onmessage = onMessage;
  }
}

function stopSSE() {
  thingEventSource && thingEventSource.close();
  thingEventSource = null;
  console.log('SSE Stopped');
}

function onEnvironmentChanged(modifiedField) {
  if (!['pinnedThings', 'filterList', 'messageTemplates'].includes(modifiedField)) {
    stopSSE();
  }
}

function onMessage(event) {
  if (event.data && event.data !== '') {
    console.log(event);
    const merged = _.merge(Things.theThing, JSON.parse(event.data));
    Things.setTheThing(merged);
    notifyAll(JSON.parse(event.data));
  }
}

