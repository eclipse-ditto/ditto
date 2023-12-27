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

import { JSONPath } from 'jsonpath-plus';
import * as Utils from '../utils.js';
import { TableFilter } from '../utils/tableFilter.js';
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

type DomElements = {
  badgeMessageIncomingCount: HTMLSpanElement,
  buttonResetMessagesIncoming: HTMLButtonElement,
  selectThingUpdateMessageContent: HTMLSelectElement,
  tbodyMessagesIncoming: HTMLTableElement,
  tableFilterMessagesIncoming: TableFilter,
};

let dom: DomElements = {
  badgeMessageIncomingCount: null,
  buttonResetMessagesIncoming: null,
  selectThingUpdateMessageContent: null,
  tbodyMessagesIncoming: null,
  tableFilterMessagesIncoming: null,
};

let messages = [];
let filteredMessages = [];
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
  dom.tableFilterMessagesIncoming.addEventListener('filterChange', onMessageFilterChange);
  dom.tableFilterMessagesIncoming.filterOptions = createFilterOptions();
}

function onMessageTableClick(event) {
  selectedRow = event.target.parentNode.rowIndex - 1;
  updateThingUpdateDetail();
  messageDetail.session.getUndoManager().reset();
}

function updateThingUpdateDetail() {
  switch (thingUpdateMessageContent) {
    case ThingUpdateMessageContent.FULL_THING_WITH_CONTEXT: {
      messageDetail.setValue(Utils.stringifyPretty(filteredMessages[selectedRow]), -1);
      break;
    }
    case ThingUpdateMessageContent.FULL_THING: {
      let messageParts = Object.entries(filteredMessages[selectedRow]);
      let filtered = messageParts.filter(([key, value]) => !key.startsWith("_"));
      const justRegularFields = Object.fromEntries(filtered);
      messageDetail.setValue(Utils.stringifyPretty(justRegularFields), -1);
      break;
    }
    case ThingUpdateMessageContent.ONLY_CONTEXT_WITH_METADATA: {
      let messageParts = Object.entries(filteredMessages[selectedRow]);
      let filtered = messageParts.filter(([key, value]) => key.startsWith("_"));
      const justSpecialFields = Object.fromEntries(filtered);
      messageDetail.setValue(Utils.stringifyPretty(justSpecialFields), -1);
      break;
    }
    case ThingUpdateMessageContent.ONLY_CONTEXT: {
      let messageParts = Object.entries(filteredMessages[selectedRow]);
      let filtered = messageParts
        .filter(([key, value]) => key.startsWith("_"))
        .filter(([key, value]) => key !== "_metadata");
      const justSpecialFields = Object.fromEntries(filtered);
      messageDetail.setValue(Utils.stringifyPretty(justSpecialFields), -1);
      break;
    }
    default:
      messageDetail.setValue(Utils.stringifyPretty(filteredMessages[selectedRow]), -1);
  }
}

function onResetMessagesClick() {
  messages = [];
  filteredMessages = [];
  dom.badgeMessageIncomingCount.textContent = '';
  dom.tbodyMessagesIncoming.innerHTML = '';
  messageDetail.setValue('');
}

function onSelectThingUpdateMessageContentSelect() {
  thingUpdateMessageContent = dom.selectThingUpdateMessageContent.value as ThingUpdateMessageContent;
  updateThingUpdateDetail();
}

function onMessage(messageData) {
  messageData.action = messageData['_context'].topic.substring(messageData['_context'].topic.lastIndexOf('/') + 1);
  messages.push(messageData);
  
  const filteredMessage = dom.tableFilterMessagesIncoming.filterItems([messageData]);
  
  if (filteredMessage.length > 0) {
    filteredMessages.push(filteredMessage[0]);
    addTableRow(filteredMessage[0]);
  }
  
  updateMessageCounter();
}

function updateMessageCounter() {
  if (filteredMessages.length === messages.length) {
    dom.badgeMessageIncomingCount.textContent = messages.length > 0 ? messages.length.toString() : '';
  } else {
    dom.badgeMessageIncomingCount.textContent = `${filteredMessages.length}/${messages.length}`;
  }
}

function addTableRow(messageData: any) {
  
  Utils.addTableRow(
    dom.tbodyMessagesIncoming,
    messageData._revision, false, null,
    messageData.action,
    messageData['_context'].path,
    getColumnValues().join('\n'),
    Utils.formatDate(messageData._modified, true)
    );
    
    function getColumnValues(): string[] {
      if (messageData['_context'] && messageData['_context'].value) {
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
}

function onThingChanged(thing) {
  if (!thing || thing.thingId !== currentThingId) {
    currentThingId = thing ? thing.thingId : null;
    onResetMessagesClick();
  }
}
function createFilterOptions(): [string?] {
  let result: [string?] = [];
  ['created', 'modified', 'merged', 'deleted'].forEach((e) => result.push(`action:${e}`));
  ['_context/path'].forEach((e) => result.push(e));
  return result;
}

function onMessageFilterChange(event: CustomEvent) {
  dom.tbodyMessagesIncoming.innerHTML = '';
  filteredMessages = dom.tableFilterMessagesIncoming.filterItems(messages);
  filteredMessages.forEach((entry) => addTableRow(entry));
  updateMessageCounter();
}


