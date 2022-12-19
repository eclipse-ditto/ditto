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
// @ts-check
import * as API from '../api.js';

import * as Things from './things.js';

let thingEventSource;


/**
 * Initializes components. Should be called after DOMContentLoaded event
 */
export async function ready() {
  Things.addChangeListener(onThingChanged);
}

function onThingChanged(newThingJson, isNewThingId) {
  if (!newThingJson) {
    thingEventSource && thingEventSource.close();
    thingEventSource = null;
  } else if (isNewThingId) {
    thingEventSource && thingEventSource.close();
    console.log('Start SSE: ' + newThingJson.thingId);
    thingEventSource = API.getEventSource(newThingJson.thingId, '&extraFields=_revision,_modified');
    thingEventSource.onmessage = onMessage;
  }
}

function onMessage(event) {
  if (event.data && event.data !== '') {
    console.log(event);
    const merged = _.merge(Things.theThing, JSON.parse(event.data));
    Things.setTheThing(merged);
  }
}

