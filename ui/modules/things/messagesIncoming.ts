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
import messagesIncomingHTML from './messagesIncoming.html';
import * as Things from './things.js';
import * as ThingsSSE from './thingsSSE.js';
/* eslint-disable prefer-const */
/* eslint-disable max-len */
/* eslint-disable no-invalid-this */
/* eslint-disable require-jsdoc */

enum ThingUpdateMessageContent {
  FULL_THING_WITH_CONTEXT="FULL_THING_WITH_CONTEXT",
  FULL_THING="FULL_THING",
  ONLY_CONTEXT_WITH_METADATA="ONLY_CONTEXT_WITH_METADATA",
  ONLY_CONTEXT="ONLY_CONTEXT"
}

let dom = {
  badgeMessageIncomingCount: null,
  buttonResetMessagesIncoming: null,
  selectThingUpdateMessageContent: null,
  tbodyMessagesIncoming: null,
};

let messages = [];
let selectedRow;
let messageDetail;
let currentThingId;
let thingUpdateMessageContent = ThingUpdateMessageContent.FULL_THING_WITH_CONTEXT;

document.getElementById('messagesIncomingHTML').innerHTML = messagesIncomingHTML;

export function ready() {
  ThingsSSE.addChangeListener(onMessage);
  Things.addChangeListener(onThingChanged);

  Utils.getAllElementsById(dom);

  messageDetail = Utils.createAceEditor('messageIncomingDetail', 'ace/mode/json', true);

  dom.buttonResetMessagesIncoming.onclick = onResetMessagesClick;
  dom.selectThingUpdateMessageContent.onchange = onSelectThingUpdateMessageContentSelect;
  dom.tbodyMessagesIncoming.addEventListener('click', onMessageTableClick);
}

function onMessageTableClick(event) {
  selectedRow = event.target.parentNode.rowIndex - 1;
  updateThingUpdateDetail();
  messageDetail.session.getUndoManager().reset();
}

function updateThingUpdateDetail() {
  switch (thingUpdateMessageContent) {
    case ThingUpdateMessageContent.FULL_THING_WITH_CONTEXT: {
      messageDetail.setValue(Utils.stringifyPretty(messages[selectedRow]), -1);
      break;
    }
    case ThingUpdateMessageContent.FULL_THING: {
      let messageParts = Object.entries(messages[selectedRow]);
      let filtered = messageParts.filter(([key, value]) => !key.startsWith("_"));
      const justRegularFields = Object.fromEntries(filtered);
      messageDetail.setValue(Utils.stringifyPretty(justRegularFields), -1);
      break;
    }
    case ThingUpdateMessageContent.ONLY_CONTEXT_WITH_METADATA: {
      let messageParts = Object.entries(messages[selectedRow]);
      let filtered = messageParts.filter(([key, value]) => key.startsWith("_"));
      const justSpecialFields = Object.fromEntries(filtered);
      messageDetail.setValue(Utils.stringifyPretty(justSpecialFields), -1);
      break;
    }
    case ThingUpdateMessageContent.ONLY_CONTEXT: {
      let messageParts = Object.entries(messages[selectedRow]);
      let filtered = messageParts
        .filter(([key, value]) => key.startsWith("_"))
        .filter(([key, value]) => key !== "_metadata");
      const justSpecialFields = Object.fromEntries(filtered);
      messageDetail.setValue(Utils.stringifyPretty(justSpecialFields), -1);
      break;
    }
    default:
      messageDetail.setValue(Utils.stringifyPretty(messages[selectedRow]), -1);
  }
}

function onResetMessagesClick() {
  messages = [];
  dom.badgeMessageIncomingCount.textContent = '';
  dom.tbodyMessagesIncoming.innerHTML = '';
  messageDetail.setValue('');
}

function onSelectThingUpdateMessageContentSelect() {
  thingUpdateMessageContent = dom.selectThingUpdateMessageContent.value as ThingUpdateMessageContent;
  updateThingUpdateDetail();
}

function onMessage(messageData) {
  messages.push(messageData);
  dom.badgeMessageIncomingCount.textContent = messages.length;

  function getColumnValues(action: string): string[] {
    if (action === 'deleted') {
      return []
    } else if (messageData['_context']?.value) {
      return [
        ...messageData['_context'].value.features ? Object.keys(messageData['_context'].value.features) : [],
        ...messageData['_context'].value.attributes ? Object.keys(messageData['_context'].value.attributes) : [],
      ]
    } else {
      return [
        ...messageData['features'] ? Object.keys(messageData.features) : [],
        ...messageData['attributes'] ? Object.keys(messageData.attributes) : [],
      ];
    }
  }

  let action = messageData['_context'].topic.substring(messageData['_context'].topic.lastIndexOf('/') + 1);
  Utils.addTableRow(
      dom.tbodyMessagesIncoming,
      messageData._revision, false, null,
      action,
      messageData['_context'].path,
      getColumnValues(action).join('\n'),
      Utils.formatDate(messageData._modified, true)
  );
}

function onThingChanged(thing) {
  if (!thing || thing.thingId !== currentThingId) {
    currentThingId = thing ? thing.thingId : null;
    onResetMessagesClick();
  }
}
