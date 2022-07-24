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

/* eslint-disable prefer-const */
/* eslint-disable max-len */
/* eslint-disable no-invalid-this */
/* eslint-disable require-jsdoc */
import * as Utils from '../utils.js';
import * as API from '../api.js';
import * as Environments from '../environments/environments.js';

let dom = {
  connectionTemplateRadios: null,
  selectConnectionTemplate: null,
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
};

let connectionEditor;
let incomingEditor;
let outgoingEditor;
let connectionLogDetail;
let connectionStatusDetail;

let theConnection;
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

  loadConnectionTemplates();

  dom.buttonLoadConnections.onclick = loadConnections;
  dom.tabConnections.onclick = onTabActivated;

  dom.buttonCreateConnection.onclick = () => {
    const templateConnection = {};
    if (API.env() !== 'things') {
      templateConnection.id = Math.random().toString(36).replace('0.', '');
    }
    const newConnection = JSON.parse(JSON.stringify(
        connectionTemplates[document.querySelector('input[name=connectionTemplate]:checked').value]));

    const mergedConnection = {...templateConnection, ...newConnection};
    setConnection(mergedConnection);
    API.callConnectionsAPI('createConnection', loadConnections, null, mergedConnection);
  };

  dom.selectConnectionTemplate.onchange = () => {
    const templateConnection = {};
    if (API.env() !== 'things') {
      templateConnection.id = Math.random().toString(36).replace('0.', '');
    }
    const newConnection = JSON.parse(JSON.stringify(
        connectionTemplates[dom.selectConnectionTemplate.value]));

    const mergedConnection = {...templateConnection, ...newConnection};
    setConnection(mergedConnection);
    connectionEditor.session.getUndoManager().markClean();
  };

  connectionEditor.on('input', () => {
    if (!connectionEditor.session.getUndoManager().isClean()) {
      dom.selectConnectionTemplate.value = null;
    }
  });

  dom.tbodyConnections.addEventListener('click', (event) => {
    if (event.target && event.target.tagName === 'TD') {
      API.callConnectionsAPI('retrieveConnection', setConnection, event.target.parentNode.id);
    }
  });

  dom.tbodyConnectionLogs.addEventListener('click', (event) => {
    connectionLogDetail.setValue(JSON.stringify(connectionLogs[event.target.parentNode.rowIndex - 1], null, 2), -1);
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

  dom.buttonSaveConnection.onclick = () => {
    if (dom.inputConnectionId.value) {
      API.callConnectionsAPI('modifyConnection', loadConnections, dom.inputConnectionId.value, theConnection);
    } else {
      if (API.env() === 'things') {
        delete theConnection.id;
      }
      API.callConnectionsAPI('createConnection', loadConnections, null, theConnection);
    }
  };

  dom.buttonDeleteConnection.onclick = () => {
    Utils.assert(dom.inputConnectionId.value, 'Please select a connection');
    API.callConnectionsAPI('deleteConnection', () => {
      setConnection(null);
      loadConnections();
    },
    dom.inputConnectionId.value);
  };

  dom.buttonRetrieveConnectionStatus.onclick = () => {
    Utils.assert(dom.inputConnectionId.value, 'Please select a connection');
    API.callConnectionsAPI('retrieveStatus', (connectionStatus) => {
      connectionStatusDetail.setValue(JSON.stringify(connectionStatus, null, 2), -1);
    },
    dom.inputConnectionId.value);
  };

  dom.buttonEnableConnectionLogs.onclick = () => {
    Utils.assert(dom.inputConnectionId.value, 'Please select a connection');
    API.callConnectionsAPI('connectionCommand', null, dom.inputConnectionId.value, null, 'connectivity.commands:enableConnectionLogs');
  };

  dom.buttonResetConnectionLogs.onclick = () => {
    Utils.assert(dom.inputConnectionId.value, 'Please select a connection');
    API.callConnectionsAPI('connectionCommand', retrieveConnectionLogs, dom.inputConnectionId.value, null, 'connectivity.commands:resetConnectionLogs');
  };

  dom.buttonRetrieveConnectionLogs.onclick = retrieveConnectionLogs;

  dom.buttonRetrieveConnectionMetrics.onclick = () => {
    Utils.assert(dom.inputConnectionId.value, 'Please select a connection');
    dom.tbodyConnectionMetrics.innerHTML = '';
    API.callConnectionsAPI('retrieveConnectionMetrics', (response) => {
      if (response.connectionMetrics.outbound) {
        Object.keys(response.connectionMetrics.outbound).forEach((type) => {
          let entry = response.connectionMetrics.outbound[type];
          Utils.addTableRow(dom.tbodyConnectionMetrics, type, false, false, 'success', entry.success.PT1M, entry.success.PT1H, entry.success.PT24H);
          Utils.addTableRow(dom.tbodyConnectionMetrics, type, false, false, 'failure', entry.failure.PT1M, entry.failure.PT1H, entry.failure.PT24H);
        });
      }
    },
    dom.inputConnectionId.value);
  };

  dom.buttonResetConnectionMetrics.onclick = () => {
    Utils.assert(dom.inputConnectionId.value, 'Please select a connection');
    API.callConnectionsAPI('connectionCommand', null, dom.inputConnectionId.value, null, 'connectivity.commands:resetConnectionMetrics');
  };
}

function retrieveConnectionLogs() {
  Utils.assert(dom.inputConnectionId.value, 'Please select a connection');
  dom.tbodyConnectionLogs.innerHTML = '';
  connectionLogDetail.setValue('');
  API.callConnectionsAPI('retrieveConnectionLogs', (response) => {
    connectionLogs = response.connectionLogs;
    response.connectionLogs.forEach((entry) => {
      const timestampDisplay = entry.timestamp.replace('T', ' ').replace('Z', '').replace('.', ' ');
      Utils.addTableRow(dom.tbodyConnectionLogs, timestampDisplay, false, false, entry.type, entry.level);
    });
    dom.tbodyConnectionLogs.scrollTop = dom.tbodyConnectionLogs.scrollHeight - dom.tbodyConnectionLogs.clientHeight;
  },
  dom.inputConnectionId.value);
}

function setConnection(connection) {
  theConnection = connection;
  dom.inputConnectionId.value = (theConnection && theConnection.id) ? theConnection.id : null;
  connectionEditor.setValue(theConnection ? JSON.stringify(theConnection, null, 2) : '', -1);
  const withJavaScript = theConnection && theConnection.mappingDefinitions && theConnection.mappingDefinitions.javascript;
  incomingEditor.setValue(withJavaScript ?
    theConnection.mappingDefinitions.javascript.options.incomingScript :
    '', -1);
  outgoingEditor.setValue(withJavaScript ?
    theConnection.mappingDefinitions.javascript.options.outgoingScript :
    '', -1);
  connectionStatusDetail.setValue('');
  connectionLogDetail.setValue('');
  dom.tbodyConnectionMetrics.innerHTML = '';
  dom.tbodyConnectionLogs.innerHTML = '';
}

function loadConnections() {
  dom.tbodyConnections.innerHTML = '';
  let connectionSelected = false;
  API.callConnectionsAPI('listConnections', (connections) => {
    connections.forEach((connection) => {
      const id = API.env() === 'things' ? connection.id : connection;
      const row = dom.tbodyConnections.insertRow();
      row.id = id;
      if (API.env() === 'things') {
        row.insertCell(0).innerHTML = connection.name;
      } else {
        API.callConnectionsAPI('retrieveConnection', (dittoConnection) => {
          row.insertCell(0).innerHTML = dittoConnection.name;
        },
        id);
      }
      API.callConnectionsAPI('retrieveStatus', (status) => {
        row.insertCell(-1).innerHTML = status.liveStatus;
        row.insertCell(-1).innerHTML = status.recoveryStatus;
      },
      id);
      if (theConnection && id === theConnection.id) {
        row.classList.add('table-active');
        connectionSelected = true;
      }
    });
    if (!connectionSelected) {
      setConnection(null);
    }
  });
};

function loadConnectionTemplates() {
  fetch('templates/connectionTemplates.json')
      .then((response) => {
        response.json().then((loadedTemplates) => {
          connectionTemplates = loadedTemplates;
          Utils.setOptions(dom.selectConnectionTemplate, Object.keys(connectionTemplates));
          dom.selectConnectionTemplate.value = null;
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
        options: {
          incomingScript: '',
          outgoingScript: '',
        },
      },
    };
  }
}

