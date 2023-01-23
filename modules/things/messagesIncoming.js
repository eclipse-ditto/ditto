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

import * as Utils from '../utils.js';
import * as Things from './things.js';
import * as ThingsSSE from './thingsSSE.js';
/* eslint-disable prefer-const */
/* eslint-disable max-len */
/* eslint-disable no-invalid-this */
/* eslint-disable require-jsdoc */

let dom = {
  badgeMessageIncomingCount: null,
  buttonResetMessagesIncoming: null,
  tbodyMessagesIncoming: null,
};

let messages = [];
let messageDetail;
let currentThingId;

export function ready() {
  ThingsSSE.addChangeListener(onMessage);
  Things.addChangeListener(onThingChanged);

  Utils.getAllElementsById(dom);

  messageDetail = Utils.createAceEditor('messageIncomingDetail', 'ace/mode/json', true);

  dom.buttonResetMessagesIncoming.onclick = onResetMessagesClick;
  dom.tbodyMessagesIncoming.addEventListener('click', onMessageTableClick);
}

function onMessageTableClick(event) {
  messageDetail.setValue(JSON.stringify(messages[event.target.parentNode.rowIndex - 1], null, 2), -1);
  messageDetail.session.getUndoManager().reset();
}

function onResetMessagesClick() {
  messages = [];
  dom.badgeMessageIncomingCount.textContent = '';
  dom.tbodyMessagesIncoming.innerHTML = '';
  messageDetail.setValue('');
}

function onMessage(messageData) {
  messages.push(messageData);
  dom.badgeMessageIncomingCount.textContent = messages.length;

  Utils.addTableRow(
      dom.tbodyMessagesIncoming,
      messageData._revision, false, false,
      [...messageData['features'] ? Object.keys(messageData.features) : [],
        ...messageData['attributes'] ? Object.keys(messageData.attributes) : []],
      Utils.formatDate(messageData._modified, true),
  );
}

function onThingChanged(thing) {
  if (!thing || thing.thingId !== currentThingId) {
    currentThingId = thing ? thing.thingId : null;
    onResetMessagesClick();
  }
}
