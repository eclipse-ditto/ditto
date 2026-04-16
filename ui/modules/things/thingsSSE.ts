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
import * as ThingsSearch from './thingsSearch.js';

const DEFAULT_SELECTED_FIELDS =
  'fields=thingId,policyId,definition,attributes,features,_revision,_created,_modified,_metadata,_context/topic,_context/path,_context/value' +
  '&extraFields=thingId,policyId,definition,attributes,features,_revision,_created,_modified,_metadata';

let selectedThingEventSource;
let thingsTableEventSource;
let currentSelectedFields = DEFAULT_SELECTED_FIELDS;


/**
 * Initializes components. Should be called after DOMContentLoaded event
 */
export async function ready() {
  Things.addChangeListener(onSelectedThingChanged);
  ThingsSearch.addChangeListener(onThingsTableChanged);
  Environments.addChangeListener(onEnvironmentChanged);
}

const observers = [];
export function addChangeListener(observer) {
  observers.push(observer);
}
function notifyAll(thingJson) {
  observers.forEach(observer => observer.call(null, thingJson));
}

function onThingsTableChanged(thingIds, fieldsQueryParameter) {
  stopSSE(thingsTableEventSource);
  if (thingIds && thingIds.length > 0) {
    // console.log('SSE Start: THINGS TABLE');
    thingsTableEventSource = API.getEventSource(thingIds, fieldsQueryParameter);
    thingsTableEventSource.onmessage = onMessageThingsTable;
  }
}

function onSelectedThingChanged(newThingJson, isNewThingId) {
  if (!newThingJson) {
    stopSSE(selectedThingEventSource);
  } else if (isNewThingId) {
    connectSelectedThing(newThingJson.thingId);
  }
}

function connectSelectedThing(thingId: string) {
  if (selectedThingEventSource) {
    selectedThingEventSource.close();
  }
  selectedThingEventSource = API.getEventSource(thingId, currentSelectedFields);
  selectedThingEventSource.onmessage = onMessageSelectedThing;
}

/**
 * Reconnects the live SSE for the currently selected thing with updated
 * field parameters (e.g. after toggling "Include headers").
 */
export function reconnectSelectedThing(fields: string) {
  currentSelectedFields = fields;
  const thingId = Things.theThing?.thingId;
  if (thingId && selectedThingEventSource) {
    connectSelectedThing(thingId);
  }
}

function stopSSE(eventSource) {
  if (eventSource) {
    eventSource.close();
    // console.log('SSE Stopped: ' + (eventSource === selectedThingEventSource ? 'SELECTED THING' : 'THINGS TABLE'));
  }
}

function onEnvironmentChanged(modifiedField) {
  if (!['pinnedThings', 'filterList', 'messageTemplates', 'recentPolicyIds'].includes(modifiedField)) {
    stopSSE(selectedThingEventSource);
    stopSSE(thingsTableEventSource);
  }
}

function onMessageSelectedThing(event) {
  if (Things.isHistoryModeActive()) return;
  if (event.data && event.data !== '') {
    const completeChangedThing = JSON.parse(event.data);
    Things.setTheThing(completeChangedThing);
    notifyAll(JSON.parse(event.data));
  }
}

function onMessageThingsTable(event) {
  if (event.data && event.data !== '') {
    ThingsSearch.updateTableRow(JSON.parse(event.data));
  }
}

