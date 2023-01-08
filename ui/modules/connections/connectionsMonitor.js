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

import * as API from '../api.js';
import * as Utils from '../utils.js';
import * as Connections from './connections.js';
/* eslint-disable prefer-const */
/* eslint-disable max-len */
/* eslint-disable no-invalid-this */
/* eslint-disable require-jsdoc */

let dom = {
  tbodyConnectionLogs: null,
  tbodyConnectionMetrics: null,
  buttonRetrieveConnectionStatus: null,
  buttonRetrieveConnectionLogs: null,
  buttonEnableConnectionLogs: null,
  buttonResetConnectionLogs: null,
  buttonRetrieveConnectionMetrics: null,
  buttonResetConnectionMetrics: null,
  tableValidationConnections: null,
};

let connectionLogs;
let connectionLogDetail;

let connectionStatusDetail;

let selectedConnectionId;

export function ready() {
  Connections.addChangeListener(onConnectionChange);

  Utils.getAllElementsById(dom);

  connectionLogDetail = Utils.createAceEditor('connectionLogDetail', 'ace/mode/json', true);
  connectionStatusDetail = Utils.createAceEditor('connectionStatusDetail', 'ace/mode/json', true);

  // Status --------------
  dom.buttonRetrieveConnectionStatus.onclick = retrieveConnectionStatus;
  document.querySelector('a[data-bs-target="#tabConnectionStatus"]').onclick = retrieveConnectionStatus;

  // Logs --------------
  dom.buttonEnableConnectionLogs.onclick = onEnableConnectionLogsClick;
  dom.buttonResetConnectionLogs.onclick = onResetConnectionLogsClick;
  dom.buttonRetrieveConnectionLogs.onclick = retrieveConnectionLogs;
  dom.tbodyConnectionLogs.addEventListener('click', onConnectionLogTableClick);

  // Metrics ---------------
  dom.buttonRetrieveConnectionMetrics.onclick = retrieveConnectionMetrics;
  document.querySelector('a[data-bs-target="#tabConnectionMetrics"]').onclick = retrieveConnectionMetrics;
  dom.buttonResetConnectionMetrics.onclick = onResetConnectionMetricsClick;
}

function onResetConnectionMetricsClick() {
  Utils.assert(selectedConnectionId, 'Please select a connection', dom.tableValidationConnections);
  API.callConnectionsAPI('connectionCommand', retrieveConnectionMetrics, selectedConnectionId, null, 'connectivity.commands:resetConnectionMetrics');
}

function onConnectionLogTableClick(event) {
  connectionLogDetail.setValue(JSON.stringify(connectionLogs[event.target.parentNode.rowIndex - 1], null, 2), -1);
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
            Utils.addTableRow(dom.tbodyConnectionMetrics, direction, false, false, type, 'success', entry.success.PT1M, entry.success.PT1H, entry.success.PT24H);
            Utils.addTableRow(dom.tbodyConnectionMetrics, direction, false, false, type, 'failure', entry.failure.PT1M, entry.failure.PT1H, entry.failure.PT24H);
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
    connectionStatusDetail.setValue(JSON.stringify(connectionStatus, null, 2), -1);
  },
  selectedConnectionId);
}

function retrieveConnectionLogs() {
  Utils.assert(selectedConnectionId, 'Please select a connection', dom.tableValidationConnections);
  dom.tbodyConnectionLogs.innerHTML = '';
  connectionLogDetail.setValue('');
  API.callConnectionsAPI('retrieveConnectionLogs', (response) => {
    connectionLogs = response.connectionLogs;
    adjustEnableButton(response);
    response.connectionLogs.forEach((entry) => {
      Utils.addTableRow(dom.tbodyConnectionLogs, Utils.formatDate(entry.timestamp, true), false, false, entry.type, entry.level);
    });
    dom.tbodyConnectionLogs.scrollTop = dom.tbodyConnectionLogs.scrollHeight - dom.tbodyConnectionLogs.clientHeight;
  },
  selectedConnectionId);
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
