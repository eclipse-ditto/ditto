/* eslint-disable prefer-const */
/* eslint-disable max-len */
/* eslint-disable no-invalid-this */
/* eslint-disable require-jsdoc */
import * as Utils from '../utils.js';
import * as API from '../api.js';
import * as Environments from '../environments/environments.js';

let dom = {
  connectionTemplateRadios: null,
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
  textareaConnectionLogDetail: null,
  textareaConnectionStatusDetail: null,
  tabConnections: null,
  collapseConnections: null,
};

let connectionEditor;
let incomingEditor;
let outgoingEditor;

let theConnection;
let connectionLogs;

let connectionTemplates;

export function ready() {
  Environments.addChangeListener(onEnvironmentChanged);

  Utils.getAllElementsById(dom);

  connectionEditor = ace.edit('connectionEditor');
  incomingEditor = ace.edit('connectionIncomingScript');
  outgoingEditor = ace.edit('connectionOutgoingScript');

  connectionEditor.session.setMode('ace/mode/json');
  incomingEditor.session.setMode('ace/mode/javascript');
  outgoingEditor.session.setMode('ace/mode/javascript');

  loadConnectionTemplates();

  dom.buttonLoadConnections.onclick = loadConnections;
  dom.tabConnections.onclick = loadConnections;

  dom.buttonCreateConnection.onclick = () => {
    const newConnection = JSON.parse(JSON.stringify(
        connectionTemplates[document.querySelector('input[name=connectionTemplate]:checked').value]));
    if (API.env() !== 'things') {
      newConnection.id = Math.random().toString(36).replace('0.', '');
    }
    setConnection(newConnection);
    API.callConnectionsAPI('createConnection', loadConnections, null, newConnection);
  };

  dom.tbodyConnections.addEventListener('click', (event) => {
    if (event.target && event.target.tagName === 'TD') {
      API.callConnectionsAPI('retrieveConnection', setConnection, event.target.parentNode.id);
    }
  });

  dom.tbodyConnectionLogs.addEventListener('click', (event) => {
    dom.textareaConnectionLogDetail.value = JSON.stringify(connectionLogs[event.target.parentNode.rowIndex], null, 2);
  });

  incomingEditor.on('blur', function() {
    theConnection.mappingDefinitions.javascript.options.incomingScript = incomingEditor.getValue();
    connectionEditor.setValue(JSON.stringify(theConnection, null, 2));
  });
  outgoingEditor.on('blur', function() {
    theConnection.mappingDefinitions.javascript.options.outgoingScript = outgoingEditor.getValue();
    connectionEditor.setValue(JSON.stringify(theConnection, null, 2));
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
      dom.textareaConnectionStatusDetail.value = JSON.stringify(connectionStatus, null, 2);
    },
    dom.inputConnectionId.value);
  };

  dom.buttonEnableConnectionLogs.onclick = () => {
    Utils.assert(dom.inputConnectionId.value, 'Please select a connection');
    API.callConnectionsAPI('connectionCommand', null, dom.inputConnectionId.value, null, 'connectivity.commands:enableConnectionLogs');
  };

  dom.buttonResetConnectionLogs.onclick = () => {
    Utils.assert(dom.inputConnectionId.value, 'Please select a connection');
    API.callConnectionsAPI('connectionCommand', null, dom.inputConnectionId.value, null, 'connectivity.commands:resetConnectionLogs');
  };

  dom.buttonRetrieveConnectionLogs.onclick = () => {
    Utils.assert(dom.inputConnectionId.value, 'Please select a connection');
    dom.tbodyConnectionLogs.innerHTML = '';
    dom.textareaConnectionLogDetail.value = null;
    API.callConnectionsAPI('retrieveConnectionLogs', (response) => {
      connectionLogs = response.connectionLogs;
      response.connectionLogs.forEach((entry) => {
        Utils.addTableRow(dom.tbodyConnectionLogs, entry.timestamp, false, false, entry.type, entry.level);
      });
    },
    dom.inputConnectionId.value);
  };

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

function setConnection(connection) {
  theConnection = connection;
  dom.inputConnectionId.value = (theConnection && theConnection.id) ? theConnection.id : null;
  connectionEditor.setValue(theConnection ? JSON.stringify(theConnection, null, 2) : '');
  const withJavaScript = theConnection && theConnection.mappingDefinitions && theConnection.mappingDefinitions.javascript;
  incomingEditor.setValue(withJavaScript ?
    theConnection.mappingDefinitions.javascript.options.incomingScript :
    '', -1);
  outgoingEditor.setValue(withJavaScript ?
    theConnection.mappingDefinitions.javascript.options.outgoingScript :
    '', -1);
  dom.textareaConnectionStatusDetail.value = null;
  dom.tbodyConnectionMetrics.innerHTML = '';
  dom.tbodyConnectionLogs.innerHTML = '';
  dom.textareaConnectionLogDetail.value = null;
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

function onEnvironmentChanged() {
  if (dom.collapseConnections.classList.contains('show')) {
    loadConnections();
  }
}

function loadConnectionTemplates() {
  fetch('templates/connectionTemplates.json')
      .then((response) => {
        response.json().then((loadedTemplates) => {
          connectionTemplates = loadedTemplates;
          Object.keys(connectionTemplates).forEach((templateName, i) => {
            Utils.addRadioButton(dom.connectionTemplateRadios, 'connectionTemplate', templateName, i == 0);
          });
        });
      });
}


