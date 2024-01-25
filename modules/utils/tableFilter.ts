/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
import tableFilterHTML from './tableFilter.html';
import { BasicFilters, FilterListener, Term } from './basicFilters.js';

enum Mode {
  BASIC=0,
  ADVANCED=1,
}

export class TableFilter extends HTMLElement implements FilterListener {

  basicFilters = new BasicFilters;
  mode = Mode.BASIC;
  mainInput: HTMLInputElement;
  dropDownInput: HTMLInputElement;
  validatedFilter: string;
  constructor() {
    super();
    this.basicFilters.onFilterChange = this;
  }
  connectedCallback() {
    this.innerHTML = tableFilterHTML;
  
    this.mainInput = this.querySelector('input[name="main"]');
    this.mainInput.addEventListener('keyup', this.mainInputChangedCallback(this));
    this.mainInput.addEventListener('search', this.mainInputChangedCallback(this));

    this.dropDownInput = this.querySelector('input[name="dropdown"]');
    this.dropDownInput.addEventListener('keyup', this.filterDropdown(this));
    this.dropDownInput.addEventListener('search', this.filterDropdown(this));

    this.querySelector('button').addEventListener('click', this.onDropDownToggle(this));
    this.querySelector('ul').addEventListener('click', this.filterSelectedCallback(this));

    this.querySelector('input[role="switch"').addEventListener('click', this.toggleModeCallback(this));
  }

  set filterOptions(value: [Term?]) {
    const el = this.querySelector('ul') as HTMLUListElement;
    const preservedInput = el.firstElementChild;
    el.replaceChildren(preservedInput);
    value.forEach((entry) => {
      Utils.addDropDownEntry(el, entry.toString(), false, JSON.stringify(entry));
    })
  }

  get filter() {
    return this.validatedFilter;
  }

  filterItems(items: Array<any>) {
    let result = items;
    if (this.validatedFilter) {
      try {
        result = JSONPath({
          path: this.validatedFilter,
          json: items,
        });
      }
      catch (error) {
        console.log(error.message);
      }
      
    }
    return result;
  }
    
  private fillUIChips() {
    const div = this.querySelector('[data-bs-theme="dark"]') as HTMLElement;
    div.innerHTML = '';
    this.basicFilters.getAllUIs().forEach((chip) => div.appendChild(chip));
  }
  
  private toggleModeCallback(tableFilter: TableFilter) {
    return (event) => {
      switch(tableFilter.mode) {
        case Mode.BASIC:
          tableFilter.mainInput.value = tableFilter.filter;
          tableFilter.mainInput.placeholder = 'Enter JSONPath to filter...';
          (tableFilter.querySelector('[data-bs-theme="dark"]') as HTMLElement).hidden = true;
          tableFilter.mode = Mode.ADVANCED;
          break;
        case Mode.ADVANCED:
          tableFilter.mainInput.value = '';
          tableFilter.mainInput.placeholder = 'Add basic filter...';
          (tableFilter.querySelector('[data-bs-theme="dark"]') as HTMLElement).hidden = false;
          tableFilter.mode = Mode.BASIC;
          this.basicFilters.clear();
          this.filterChanged();
      }
    }
  }

  private filterSelectedCallback(tableFilter: TableFilter) {
    return (event: Event) => {
      const target = event.target as HTMLElement;
      if (target && target.classList.contains('dropdown-item')) {

        let newTerm = Term.fromJSON(JSON.parse(target.dataset.value));

        if (tableFilter.mode === Mode.BASIC) {
          tableFilter.basicFilters.addOrUpdate(newTerm);
        } else {
          tableFilter.mainInput.value = new BasicFilters()
            .addOrUpdate(newTerm)
            .createJsonPath();
          }
        }
        tableFilter.filterChanged();
      }
  }

  private mainInputChangedCallback(tableFilter: TableFilter) {
    return (event: Event) => {
      if (event.type === 'search' ||
          (event as KeyboardEvent).key === 'Enter' ||
          (event as KeyboardEvent).code === 'Enter') {
        switch (tableFilter.mode) {
          case Mode.BASIC:
              tableFilter.basicFilters.addFromString(tableFilter.mainInput.value)
              tableFilter.filterChanged();
              tableFilter.mainInput.value = '';
            break;
          case Mode.ADVANCED:
            try {
              let test = JSONPath({
                path: tableFilter.mainInput.value,
                json: [{
                  _context: {
                    topic: 'test',
                    path: 'test',
                  }
                }],
              });
              tableFilter.filterChanged();
            }
            catch (error) {
              Utils.assert(false, error.message, tableFilter.mainInput);
            }
        }
      }
    }
  }

  private filterDropdown(tableFilter: TableFilter) {
    return (event) => {
      tableFilter.querySelectorAll('.dropdown-menu li').forEach((element: HTMLElement) => {
        const value = (event.target as HTMLInputElement).value.toLowerCase();
        element.hidden = element.textContent.toLowerCase().indexOf(value) < 0;  
      });
    }
  }

  filterChanged() {
    switch(this.mode) {
      case Mode.BASIC:
        this.validatedFilter = this.basicFilters.createJsonPath();
        this.fillUIChips();
        break;
      case Mode.ADVANCED:
        this.validatedFilter = this.mainInput.value;
    }
    this.dispatchEvent(new CustomEvent('filterChange', {
        composed: true,
        detail: {
          filter: this.validatedFilter
        }
    }));
  }

  onDropDownToggle(tableFilter: TableFilter) {
    return () => {
      tableFilter.dropDownInput.value = '';
      tableFilter.dropDownInput.dispatchEvent(new Event('keyup'));
      tableFilter.dropDownInput.focus();
    }
  }
}

customElements.define('table-filter', TableFilter);
