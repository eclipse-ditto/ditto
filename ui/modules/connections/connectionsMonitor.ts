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
import {JSONPath} from 'jsonpath-plus';

import * as API from '../api.js';
import * as Utils from '../utils.js';
import * as Connections from './connections.js';
import { TableFilter } from '../utils/tableFilter.js';
import { FilterType, Term } from '../utils/basicFilters.js';
/* eslint-disable prefer-const */
/* eslint-disable max-len */
/* eslint-disable no-invalid-this */
/* eslint-disable require-jsdoc */

type DomElements = {
    tbodyConnectionLogs: HTMLTableElement,
    tbodyConnectionMetrics: HTMLTableElement,
    buttonRetrieveConnectionStatus: HTMLButtonElement,
    buttonRetrieveConnectionLogs: HTMLButtonElement,
    buttonEnableConnectionLogs: HTMLButtonElement,
    buttonResetConnectionLogs: HTMLButtonElement,
    buttonRetrieveConnectionMetrics: HTMLButtonElement,
    buttonResetConnectionMetrics: HTMLButtonElement,
    tableValidationConnections: HTMLInputElement,
    tableFilterConnectionLogs: TableFilter,
    badgeConnectionLogsCount: HTMLSpanElement,
}

let dom: DomElements = {
  tbodyConnectionLogs: null,
  tbodyConnectionMetrics: null,
  buttonRetrieveConnectionStatus: null,
  buttonRetrieveConnectionLogs: null,
  buttonEnableConnectionLogs: null,
  buttonResetConnectionLogs: null,
  buttonRetrieveConnectionMetrics: null,
  buttonResetConnectionMetrics: null,
  tableValidationConnections: null,
  tableFilterConnectionLogs: null,
  badgeConnectionLogsCount: null,
};

let connectionLogs = [];
let filteredLogs: Array<any>;

let connectionLogDetail;

let connectionStatusDetail;

let selectedConnectionId;

export async function ready() {
  Connections.addChangeListener(onConnectionChange);

  Utils.getAllElementsById(dom);

  connectionLogDetail = Utils.createAceEditor('connectionLogDetail', 'ace/mode/json', true);
  connectionLogDetail.session.setUseWrapMode(true);
  connectionStatusDetail = Utils.createAceEditor('connectionStatusDetail', 'ace/mode/json', true);

  // Status --------------
  dom.buttonRetrieveConnectionStatus.onclick = retrieveConnectionStatus;
  (document.querySelector('a[data-bs-target="#tabConnectionStatus"]') as HTMLElement).onclick = retrieveConnectionStatus;

  // Logs --------------
  dom.buttonEnableConnectionLogs.onclick = onEnableConnectionLogsClick;
  dom.buttonResetConnectionLogs.onclick = onResetConnectionLogsClick;
  dom.buttonRetrieveConnectionLogs.onclick = retrieveConnectionLogs;
  dom.tbodyConnectionLogs.addEventListener('click', onConnectionLogTableClick);
  dom.tableFilterConnectionLogs.addEventListener('filterChange', onConnectionLogFilterChange);
  dom.tableFilterConnectionLogs.filterOptions = createFilterOptions();

  // Metrics ---------------
  dom.buttonRetrieveConnectionMetrics.onclick = retrieveConnectionMetrics;
  (document.querySelector('a[data-bs-target="#tabConnectionMetrics"]') as HTMLElement).onclick = retrieveConnectionMetrics;
  dom.buttonResetConnectionMetrics.onclick = onResetConnectionMetricsClick;
  dom.tbodyConnectionMetrics.addEventListener('click', onConnectionMetricsTableClick)
}

function onResetConnectionMetricsClick() {
  Utils.assert(selectedConnectionId, 'Please select a connection', dom.tableValidationConnections);
  API.callConnectionsAPI('connectionCommand', retrieveConnectionMetrics, selectedConnectionId, null, 'connectivity.commands:resetConnectionMetrics');
}

function onConnectionLogTableClick(event) {
  connectionLogDetail.setValue(Utils.stringifyPretty(filteredLogs[event.target.parentNode.rowIndex - 1]), -1);
  connectionLogDetail.session.getUndoManager().reset();
}

function onConnectionMetricsTableClick(event) {
}

function onResetConnectionLogsClick() {
  Utils.assert(selectedConnectionId, 'Please select a connection', dom.tableValidationConnections);
  API.callConnectionsAPI('connectionCommand', retrieveConnectionLogs, selectedConnectionId, null, 'connectivity.commands:resetConnectionLogs');
}

function onEnableConnectionLogsClick() {
  Utils.assert(selectedConnectionId, 'Please select a connection', dom.tableValidationConnections);
  API.callConnectionsAPI('connectionCommand', retrieveConnectionLogs, selectedConnectionId, null, 'connectivity.commands:enableConnectionLogs');
}

function retrieveConnectionMetrics() {
  Utils.assert(selectedConnectionId, 'Please select a connection', dom.tableValidationConnections);
  dom.tbodyConnectionMetrics.innerHTML = '';
  API.callConnectionsAPI('retrieveConnectionMetrics', (response) => {
    if (response.connectionMetrics) {
      Object.keys(response.connectionMetrics).forEach((direction) => {
        if (response.connectionMetrics[direction]) {
          Object.keys(response.connectionMetrics[direction]).forEach((type) => {
            let entry = response.connectionMetrics[direction][type];
            Utils.addTableRow(dom.tbodyConnectionMetrics, direction, false, null, type, 'success', entry.success.PT1M, entry.success.PT1H, entry.success.PT24H);
            let failureRow = Utils.addTableRow(dom.tbodyConnectionMetrics, direction, false, null, type, 'failure', entry.failure.PT1M, entry.failure.PT1H, entry.failure.PT24H);
            let numberColumn = failureRow.lastElementChild;
            for (let i = 0; i < 3; i++) {
              if (numberColumn.innerHTML !== '0') numberColumn.classList.add('text-danger');
              numberColumn = numberColumn.previousElementSibling;
            }
          });
        };
      });
    }
  },
  selectedConnectionId);
}

function retrieveConnectionStatus() {
  Utils.assert(selectedConnectionId, 'Please select a connection', dom.tableValidationConnections);
  API.callConnectionsAPI('retrieveStatus', (connectionStatus) => {
    connectionStatusDetail.setValue(Utils.stringifyPretty(connectionStatus), -1);
    connectionStatusDetail.session.getUndoManager().reset();
  },
  selectedConnectionId);
}

function retrieveConnectionLogs() {
  Utils.assert(selectedConnectionId, 'Please select a connection', dom.tableValidationConnections);
  API.callConnectionsAPI('retrieveConnectionLogs', (response) => {
    connectionLogs = response.connectionLogs;
    adjustEnableButton(response);
    fillConnectionLogsTable();
  },
  selectedConnectionId);
}

function fillConnectionLogsTable() {
  dom.tbodyConnectionLogs.innerHTML = '';
  connectionLogDetail.setValue('');

  filteredLogs = dom.tableFilterConnectionLogs.filterItems(connectionLogs);

  filteredLogs.forEach((entry) => {
    Utils.addTableRow(
        dom.tbodyConnectionLogs,
        Utils.formatDate(entry.timestamp, true), false, null,
        entry.category,
        entry.type,
        entry.level
    );
  });

  Utils.updateCounterBadge(dom.badgeConnectionLogsCount, connectionLogs, filteredLogs);

  dom.tbodyConnectionLogs.scrollTop = dom.tbodyConnectionLogs.scrollHeight - dom.tbodyConnectionLogs.clientHeight;
}

function adjustEnableButton(response) {
  if (response.enabledUntil) {
    dom.buttonEnableConnectionLogs.querySelector('i').classList.replace('bi-toggle-off', 'bi-toggle-on');
    dom.buttonEnableConnectionLogs.setAttribute('title', `Enabled until ${Utils.formatDate(response.enabledUntil)}`);
  } else {
    dom.buttonEnableConnectionLogs.querySelector('i').classList.replace('bi-toggle-on', 'bi-toggle-off');
    dom.buttonEnableConnectionLogs.setAttribute('title', 'Click to enable connection logs for the selected connection');
  }
}

function onConnectionChange(connection, isNewConnection = true) {
  selectedConnectionId = connection ? connection.id : null;
  connectionStatusDetail.setValue('');
  connectionLogDetail.setValue('');
  dom.tbodyConnectionMetrics.innerHTML = '';
  dom.tbodyConnectionLogs.innerHTML = '';
  if (!isNewConnection && connection && connection.id) {
    retrieveConnectionLogs();
  }
}

function onConnectionLogFilterChange(event: Event) {
  fillConnectionLogsTable();
}

function createFilterOptions(): [Term?] {
  let result: [Term?] = [];

  ['consumed', 'mapped', 'dropped', 'enforced', 'acknowledged', 'throttled', 'dispatched', 'filtered', 'published']
    .forEach((e) => result.push(new Term(FilterType.PROP_EQ, e, 'type')));
  ['source', 'target', 'response']
    .forEach((e) => result.push(new Term(FilterType.PROP_EQ, e, 'category')));
  ['success', 'failure']
    .forEach((e) => result.push(new Term(FilterType.PROP_EQ, e, 'level')));
  ['thing']
    .forEach((e) => result.push(new Term(FilterType.PROP_EQ, e, 'entityType')));

  // ['correlationId', 'entityId']
  //   .forEach((value: string) => result.push(value));

  return result;
}
