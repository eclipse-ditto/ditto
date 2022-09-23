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
import * as Environments from '../environments/environments.js';
/* eslint-disable prefer-const */
/* eslint-disable max-len */
/* eslint-disable no-invalid-this */
/* eslint-disable require-jsdoc */
import * as Utils from '../utils.js';

let dom = {
  ulConnectionTemplates: null,
  inputConnectionTemplate: null,
  inputConnectionId: null,
  tbodyConnections: null,
  tbodyConnectionLogs: null,
  tbodyConnectionMetrics: null,
  buttonLoadConnections: null,
  buttonCreateConnection: null,
  buttonSaveConnection: null,
  buttonDeleteConnection: null,
  buttonRetrieveConnectionStatus: null,
  buttonRetrieveConnectionLogs: null,
  buttonEnableConnectionLogs: null,
  buttonResetConnectionLogs: null,
  buttonRetrieveConnectionMetrics: null,
  buttonResetConnectionMetrics: null,
  tabConnections: null,
  collapseConnections: null,
  editorValidationConnection: null,
  tableValidationConnections: null,
};

let connectionEditor;
let incomingEditor;
let outgoingEditor;
let connectionLogDetail;
let connectionStatusDetail;

let theConnection;
let selectedConnectionId;
let connectionLogs;

let connectionTemplates;

export function ready() {
  Environments.addChangeListener(onEnvironmentChanged);

  Utils.getAllElementsById(dom);

  connectionEditor = Utils.createAceEditor('connectionEditor', 'ace/mode/json');
  incomingEditor = Utils.createAceEditor('connectionIncomingScript', 'ace/mode/javascript');
  outgoingEditor = Utils.createAceEditor('connectionOutgoingScript', 'ace/mode/javascript');
  connectionLogDetail = Utils.createAceEditor('connectionLogDetail', 'ace/mode/json', true);
  connectionStatusDetail = Utils.createAceEditor('connectionStatusDetail', 'ace/mode/json', true);

  Utils.addValidatorToTable(dom.tbodyConnections, dom.tableValidationConnections);

  loadConnectionTemplates();

  dom.buttonLoadConnections.onclick = loadConnections;
  dom.tabConnections.onclick = onTabActivated;

  dom.tbodyConnections.addEventListener('click', (event) => {
    if (event.target && event.target.tagName === 'TD') {
      if (selectedConnectionId === event.target.parentNode.id) {
        selectedConnectionId = null;
        setConnection(null);
      } else {
        selectedConnectionId = event.target.parentNode.id;
        API.callConnectionsAPI('retrieveConnection', setConnection, selectedConnectionId);
      }
    }
  });

  dom.ulConnectionTemplates.addEventListener('click', (event) => {
    dom.inputConnectionTemplate.value = event.target.textContent;
    const templateConnection = {};
    if (API.env() !== 'things') {
      templateConnection.id = Math.random().toString(36).replace('0.', '');
    }
    const newConnection = JSON.parse(JSON.stringify(
        connectionTemplates[dom.inputConnectionTemplate.value]));

    const mergedConnection = {...templateConnection, ...newConnection};
    setConnection(mergedConnection, true);
    dom.editorValidationConnection.classList.remove('is-invalid');
    connectionEditor.session.getUndoManager().markClean();
  });

  incomingEditor.on('blur', function() {
    initializeMappings(theConnection);
    theConnection.mappingDefinitions.javascript.options.incomingScript = incomingEditor.getValue();
    connectionEditor.setValue(JSON.stringify(theConnection, null, 2), -1);
  });
  outgoingEditor.on('blur', function() {
    initializeMappings(theConnection);
    theConnection.mappingDefinitions.javascript.options.outgoingScript = outgoingEditor.getValue();
    connectionEditor.setValue(JSON.stringify(theConnection, null, 2), -1);
  });
  connectionEditor.on('blur', function() {
    theConnection = JSON.parse(connectionEditor.getValue());
  });

  connectionEditor.on('input', () => {
    if (!connectionEditor.session.getUndoManager().isClean()) {
      dom.inputConnectionTemplate.value = null;
      dom.editorValidationConnection.classList.remove('is-invalid');
    }
  });

  dom.buttonCreateConnection.onclick = () => {
    Utils.assert(theConnection, 'Please enter a connection configuration (select a template as a basis)', dom.editorValidationConnection);
    if (API.env() === 'ditto_2') {
      selectedConnectionId = theConnection.id;
    } else {
      delete theConnection.id;
    }
    API.callConnectionsAPI('createConnection', loadConnections, null, theConnection);
  };

  dom.buttonSaveConnection.onclick = () => {
    Utils.assert(selectedConnectionId, 'Please select a connection', dom.tableValidationConnections);
    API.callConnectionsAPI('modifyConnection', loadConnections, selectedConnectionId, theConnection);
  };

  dom.buttonDeleteConnection.onclick = () => {
    Utils.assert(selectedConnectionId, 'Please select a connection', dom.tableValidationConnections);
    Utils.confirm(`Are you sure you want to delete connection<br>'${theConnection.name}'?`, 'Delete', () => {
      API.callConnectionsAPI('deleteConnection', () => {
        setConnection(null);
        loadConnections();
      },
      selectedConnectionId);
    });
  };

  // Status --------------

  dom.buttonRetrieveConnectionStatus.onclick = retrieveConnectionStatus;
  document.querySelector('a[data-bs-target="#tabConnectionStatus"]').onclick = retrieveConnectionStatus;

  // Logs --------------

  dom.buttonEnableConnectionLogs.onclick = () => {
    Utils.assert(selectedConnectionId, 'Please select a connection', dom.tableValidationConnections);
    API.callConnectionsAPI('connectionCommand', retrieveConnectionLogs, dom.inputConnectionId.value, null, 'connectivity.commands:enableConnectionLogs');
  };

  dom.buttonResetConnectionLogs.onclick = () => {
    Utils.assert(selectedConnectionId, 'Please select a connection', dom.tableValidationConnections);
    API.callConnectionsAPI('connectionCommand', retrieveConnectionLogs, dom.inputConnectionId.value, null, 'connectivity.commands:resetConnectionLogs');
  };

  dom.buttonRetrieveConnectionLogs.onclick = retrieveConnectionLogs;

  dom.tbodyConnectionLogs.addEventListener('click', (event) => {
    connectionLogDetail.setValue(JSON.stringify(connectionLogs[event.target.parentNode.rowIndex - 1], null, 2), -1);
  });

  // Metrics ---------------

  dom.buttonRetrieveConnectionMetrics.onclick = retrieveConnectionMetrics;
  document.querySelector('a[data-bs-target="#tabConnectionMetrics"]').onclick = retrieveConnectionMetrics;

  dom.buttonResetConnectionMetrics.onclick = () => {
    Utils.assert(selectedConnectionId, 'Please select a connection', dom.tableValidationConnections);
    API.callConnectionsAPI('connectionCommand', null, dom.inputConnectionId.value, null, 'connectivity.commands:resetConnectionMetrics');
  };
}

function retrieveConnectionMetrics() {
  Utils.assert(selectedConnectionId, 'Please select a connection', dom.tableValidationConnections);
  dom.tbodyConnectionMetrics.innerHTML = '';
  API.callConnectionsAPI('retrieveConnectionMetrics', (response) => {
    if (response.connectionMetrics) {
      Object.keys(response.connectionMetrics).forEach((direction) => {
        Object.keys(response.connectionMetrics[direction]).forEach((type) => {
          let entry = response.connectionMetrics[direction][type];
          Utils.addTableRow(dom.tbodyConnectionMetrics, direction, false, false, type, 'success', entry.success.PT1M, entry.success.PT1H, entry.success.PT24H);
          Utils.addTableRow(dom.tbodyConnectionMetrics, direction, false, false, type, 'failure', entry.failure.PT1M, entry.failure.PT1H, entry.failure.PT24H);
        });
      });
    }
  },
  dom.inputConnectionId.value);
}

function retrieveConnectionStatus() {
  Utils.assert(selectedConnectionId, 'Please select a connection', dom.tableValidationConnections);
  API.callConnectionsAPI('retrieveStatus', (connectionStatus) => {
    connectionStatusDetail.setValue(JSON.stringify(connectionStatus, null, 2), -1);
  },
  dom.inputConnectionId.value);
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
  dom.inputConnectionId.value);

  function adjustEnableButton(response) {
    if (response.enabledUntil) {
      dom.buttonEnableConnectionLogs.querySelector('i').classList.replace('bi-toggle-off', 'bi-toggle-on');
      dom.buttonEnableConnectionLogs.setAttribute('title', `Enabled until ${Utils.formatDate(response.enabledUntil)}`);
    } else {
      dom.buttonEnableConnectionLogs.querySelector('i').classList.replace('bi-toggle-on', 'bi-toggle-off');
      dom.buttonEnableConnectionLogs.setAttribute('title', 'Click to enable connection logs for the selected connection');
    }
  }
}

function setConnection(connection, isNewConnection) {
  theConnection = connection;
  incomingEditor.setValue('');
  outgoingEditor.setValue('');
  if (theConnection) {
    dom.inputConnectionId.value = theConnection.id ? theConnection.id : null;
    connectionEditor.setValue(JSON.stringify(theConnection, null, 2), -1);
    if (theConnection.mappingDefinitions && theConnection.mappingDefinitions.javascript) {
      incomingEditor.setValue(theConnection.mappingDefinitions.javascript.options.incomingScript, -1);
      outgoingEditor.setValue(theConnection.mappingDefinitions.javascript.options.outgoingScript, -1);
    }
  } else {
    dom.inputConnectionId.value = null;
    connectionEditor.setValue('');
  }
  connectionStatusDetail.setValue('');
  connectionLogDetail.setValue('');
  dom.tbodyConnectionMetrics.innerHTML = '';
  dom.tbodyConnectionLogs.innerHTML = '';
  if (!isNewConnection && theConnection && theConnection.id) {
    retrieveConnectionLogs();
  }
}

function loadConnections() {
  dom.tbodyConnections.innerHTML = '';
  let connectionSelected = false;
  API.callConnectionsAPI('listConnections', (connections) => {
    connections.forEach((connection) => {
      const id = API.env() === 'ditto_2' ? connection : connection.id;
      const row = dom.tbodyConnections.insertRow();
      row.id = id;
      if (API.env() === 'ditto_2') {
        API.callConnectionsAPI('retrieveConnection', (dittoConnection) => {
         row.insertCell(0).innerHTML = dittoConnection.name;
       },
       id);
      } else {
        row.insertCell(0).innerHTML = connection.name;
      }
      API.callConnectionsAPI('retrieveStatus', (status) => {
        row.insertCell(-1).innerHTML = status.liveStatus;
        row.insertCell(-1).innerHTML = status.recoveryStatus;
      },
      id);
      if (id === selectedConnectionId) {
        row.classList.add('table-active');
        connectionSelected = true;
      }
    });
    if (!connectionSelected) {
      selectedConnectionId = null;
      setConnection(null);
    }
  });
}

function loadConnectionTemplates() {
  fetch('templates/connectionTemplates.json')
      .then((response) => {
        response.json().then((loadedTemplates) => {
          connectionTemplates = loadedTemplates;
          Utils.addDropDownEntries(dom.ulConnectionTemplates, Object.keys(connectionTemplates));
        });
      });
}

let viewDirty = false;

function onTabActivated() {
  if (viewDirty) {
    loadConnections();
    viewDirty = false;
  }
}

function onEnvironmentChanged() {
  if (dom.collapseConnections.classList.contains('show')) {
    loadConnections();
  } else {
    viewDirty = true;
  }
}

function initializeMappings(connection) {
  if (!connection['mappingDefinitions']) {
    connection.mappingDefinitions = {
      javascript: {
        mappingEngine: 'JavaScript',
        options: {
          incomingScript: '',
          outgoingScript: '',
        },
      },
    };
  }
}

