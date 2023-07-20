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
import * as API from '../api.js';
import * as Utils from '../utils.js';
import {TabHandler} from '../utils/tabHandler.js';
import connectionsHTML from './connections.html';

const observers = [];

document.getElementById('connectionsHTML').innerHTML = connectionsHTML;

export function addChangeListener(observer) {
  observers.push(observer);
}

function notifyAll(connection, isNewConnection) {
  observers.forEach((observer) => observer.call(null, connection, isNewConnection));
}

let dom = {
  tbodyConnections: null,
  buttonLoadConnections: null,
  tabConnections: null,
  collapseConnections: null,
  tableValidationConnections: null,
};

let selectedConnectionId;

export function ready() {
  Utils.getAllElementsById(dom);
  TabHandler(dom.tabConnections, dom.collapseConnections, refreshTab, 'disableConnections');

  Utils.addValidatorToTable(dom.tbodyConnections, dom.tableValidationConnections);

  dom.buttonLoadConnections.onclick = loadConnections;
  dom.tbodyConnections.addEventListener('click', onConnectionsTableClick);
}

export function setConnection(connection, isNewConnection = false) {
  selectedConnectionId = connection ? connection.id : null;
  notifyAll(connection, isNewConnection);
}

export function loadConnections() {
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
        row.insertCell(0).innerHTML = connection.name ? connection.name : id;
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

function onConnectionsTableClick(event) {
  if (event.target && event.target.tagName === 'TD') {
    if (selectedConnectionId === event.target.parentNode.id) {
      selectedConnectionId = null;
      setConnection(null);
    } else {
      selectedConnectionId = event.target.parentNode.id;
      API.callConnectionsAPI('retrieveConnection', setConnection, selectedConnectionId);
    }
  }
}

function refreshTab(otherEnvironment) {
  if (otherEnvironment) {
    selectedConnectionId = null;
    setConnection(null);
  }
  loadConnections();
}
