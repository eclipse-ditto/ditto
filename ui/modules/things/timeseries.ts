/* eslint-disable require-jsdoc */
/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
import * as Features from './features.js';
import * as Things from './things.js';

/**
 * Backing module for the "Timeseries" tab inside the feature pane.
 * <p>
 * Wires up four DOM controls (path / from / to / limit) and a Retrieve button, fetches
 * timeseries data via {@code GET /api/2/things/<id>/timeseries?...}, then renders the result
 * into a (timestamp, value) table plus a hand-rolled SVG sparkline so the user gets a visual
 * cue without pulling in a chart library.
 * <p>
 * The path input is auto-prefilled with {@code /features/<currentFeatureId>/properties/} on
 * feature selection so the typical case is a single keystroke ({@code temperature}, etc.). The
 * from/to fields default to "the last 24h" relative to the user's clock — good enough for
 * exploratory use; precise queries land in the URL.
 */

const dom = {
  inputTimeseriesPath: null as HTMLInputElement | null,
  inputTimeseriesFrom: null as HTMLInputElement | null,
  inputTimeseriesTo: null as HTMLInputElement | null,
  inputTimeseriesLimit: null as HTMLInputElement | null,
  buttonTimeseriesRetrieve: null as HTMLButtonElement | null,
  timeseriesStatus: null as HTMLElement | null,
  timeseriesSparkline: null as SVGSVGElement | null,
  tbodyTimeseries: null as HTMLTableSectionElement | null,
};

interface TimeseriesPoint {
  t: string;
  v: unknown;
}

interface TimeseriesSeries {
  thingId: string;
  path: string;
  result?: { count?: number; unit?: string | null; dataType?: string | null };
  data?: TimeseriesPoint[];
}

export function ready(): void {
  Utils.getAllElementsById(dom);

  // Defaults: from = now-24h, to = now. Datetime-local inputs use the local timezone for display
  // but we read them back as if they were UTC (see toUtcIso), which is the convention the rest
  // of the Ditto Protocol uses for time arguments.
  const now = new Date();
  const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);
  if (dom.inputTimeseriesFrom && !dom.inputTimeseriesFrom.value) {
    dom.inputTimeseriesFrom.value = toLocalInputValue(yesterday);
  }
  if (dom.inputTimeseriesTo && !dom.inputTimeseriesTo.value) {
    dom.inputTimeseriesTo.value = toLocalInputValue(now);
  }

  dom.buttonTimeseriesRetrieve?.addEventListener('click', onRetrieveClick);

  Features.addChangeListener(onFeatureChanged);
  // Clear the previous thing's results when a different thing is selected — otherwise the
  // sparkline + table + status text from the previous thing carry over and mislead the user
  // until they click Retrieve again. The isNewThingId flag (second arg) is true only when the
  // selected thingId actually changed, so an in-place refresh (e.g. after an edit) keeps the
  // view in place.
  Things.addChangeListener((_thing: unknown, isNewThingId: boolean) => {
    if (isNewThingId) {
      clearTable();
      clearSparkline();
      setStatus('');
    }
  });
}

function onFeatureChanged(featureId: string): void {
  if (!dom.inputTimeseriesPath) return;
  const currentValue = dom.inputTimeseriesPath.value;
  const looksLikePrefix = !currentValue || /^\/features\/[^/]*\/properties\/?$/.test(currentValue);
  // Only auto-update when the field is empty or still holds an auto-generated prefix from the
  // previous selection — never clobber a path the user has actually typed into.
  if (looksLikePrefix) {
    dom.inputTimeseriesPath.value = featureId ? `/features/${featureId}/properties/` : '';
  }
}

async function onRetrieveClick(): Promise<void> {
  Utils.assert(Things.theThing, 'No Thing selected');
  const path = (dom.inputTimeseriesPath?.value ?? '').trim();
  const fromLocal = dom.inputTimeseriesFrom?.value ?? '';
  const toLocal = dom.inputTimeseriesTo?.value ?? '';
  const limit = (dom.inputTimeseriesLimit?.value ?? '').trim();
  Utils.assert(path, 'Path must not be empty', dom.inputTimeseriesPath);
  Utils.assert(fromLocal, 'From must not be empty', dom.inputTimeseriesFrom);
  Utils.assert(toLocal, 'To must not be empty', dom.inputTimeseriesTo);

  // The path the user types is the full Ditto pointer
  // (e.g. /features/<feature>/properties/<property>); the new URL shape splits it back into
  // {featureId, propertyPointer} so timeseries reads sit at /api/2/timeseries/... as a
  // first-class resource (matching IOT-495 Phase 1). Reject any path that doesn't fit so the
  // user gets feedback at the form rather than a 400 from the gateway.
  const split = splitFullPath(path);
  if (!split) {
    Utils.assert(false,
        'Path must look like /features/<featureId>/properties/<propertyPointer>',
        dom.inputTimeseriesPath);
    return;
  }

  setStatus('Loading…');
  clearTable();
  clearSparkline();

  const params = new URLSearchParams();
  params.append('from', toUtcIso(fromLocal));
  params.append('to', toUtcIso(toLocal));
  if (limit) {
    params.append('limit', limit);
  }
  const url = `/timeseries/things/${encodeURIComponent(Things.theThing.thingId)}` +
      `/features/${encodeURIComponent(split.featureId)}` +
      `/properties/${encodePointer(split.propertyPointer)}` +
      `?${params.toString()}`;

  try {
    const response = await API.callDittoREST('GET', url);
    renderResults(response, path);
  } catch (err) {
    // callDittoREST already toasts the error via Utils.showError; we just blank the status.
    setStatus('');
  }
}

/**
 * Splits a full Ditto pointer of the form /features/{featureId}/properties/{rest} into its parts.
 * Returns null if the input doesn't have that shape — the caller surfaces a UI-side validation
 * error rather than letting the gateway 400 on it.
 */
function splitFullPath(fullPath: string): { featureId: string; propertyPointer: string } | null {
  const match = /^\/features\/([^/]+)\/properties\/(.+)$/.exec(fullPath);
  if (!match) return null;
  return { featureId: match[1], propertyPointer: match[2] };
}

/**
 * Encodes a slash-separated property pointer for inclusion in a URL path. We percent-encode each
 * segment individually so segment-level slashes survive (the gateway expects multi-segment property
 * pointers like {@code temperature/avg}), then join with raw slashes.
 */
function encodePointer(pointer: string): string {
  return pointer.split('/').map(encodeURIComponent).join('/');
}

function renderResults(response: unknown, requestedPath: string): void {
  // The timeseries-service returns RetrieveTimeseriesResponse#getResults() — a JSON array of
  // per-path series. callDittoREST returns the parsed JSON envelope directly, so we expect
  // either an array of series or a single envelope with a `results` field; tolerate both.
  const seriesList: TimeseriesSeries[] = Array.isArray(response)
      ? (response as TimeseriesSeries[])
      : (((response as { results?: TimeseriesSeries[] }).results) ?? []);
  const matching = seriesList.find((s) => s.path === requestedPath) ?? seriesList[0];
  if (!matching || !matching.data || matching.data.length === 0) {
    setStatus(matching ? 'No data points in range.' : 'Empty response.');
    return;
  }

  renderTable(matching.data);
  renderSparkline(matching.data);
  const meta = matching.result;
  setStatus(`${matching.data.length} point(s)` +
    (meta?.unit ? ` · unit ${meta.unit}` : '') +
    (meta?.dataType ? ` · type ${meta.dataType}` : ''));
}

function renderTable(points: TimeseriesPoint[]): void {
  if (!dom.tbodyTimeseries) return;
  // textContent on every cell — no innerHTML — so user-controlled values can't escape the cell.
  const rows = points.map((p) => {
    const tr = document.createElement('tr');
    const tdT = document.createElement('td');
    tdT.textContent = p.t;
    const tdV = document.createElement('td');
    tdV.textContent = formatValue(p.v);
    tr.appendChild(tdT);
    tr.appendChild(tdV);
    return tr;
  });
  dom.tbodyTimeseries.replaceChildren(...rows);
}

function renderSparkline(points: TimeseriesPoint[]): void {
  if (!dom.timeseriesSparkline) return;
  // Inline SVG path — works for numeric data; non-numeric series produce NaN, which we filter
  // out so the line stays continuous through gaps. ViewBox is fixed at 600x120 and the SVG
  // uses preserveAspectRatio="none" + width:100% so it stretches to the container.
  const numeric: { t: number; v: number }[] = points
      .map((p) => ({ t: Date.parse(p.t), v: Number(p.v) }))
      .filter((p) => Number.isFinite(p.t) && Number.isFinite(p.v));
  if (numeric.length < 2) {
    clearSparkline();
    return;
  }
  const minT = numeric[0].t;
  const maxT = numeric[numeric.length - 1].t;
  let minV = Infinity;
  let maxV = -Infinity;
  for (const p of numeric) {
    if (p.v < minV) minV = p.v;
    if (p.v > maxV) maxV = p.v;
  }
  const xSpan = maxT - minT || 1;
  const ySpan = maxV - minV || 1;
  const w = 600;
  const h = 120;
  const padX = 4;
  const padY = 8;
  const innerW = w - 2 * padX;
  const innerH = h - 2 * padY;
  const d = numeric.map((p, i) => {
    const x = padX + ((p.t - minT) / xSpan) * innerW;
    const y = padY + innerH - ((p.v - minV) / ySpan) * innerH;
    return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(' ');

  const ns = 'http://www.w3.org/2000/svg';
  const svg = dom.timeseriesSparkline;
  svg.replaceChildren();
  const path = document.createElementNS(ns, 'path');
  path.setAttribute('d', d);
  path.setAttribute('fill', 'none');
  path.setAttribute('stroke', 'var(--bs-primary, #0d6efd)');
  path.setAttribute('stroke-width', '1.5');
  svg.appendChild(path);
  // Min/max value labels at top-left and bottom-left so the user can read the range without
  // adding axis ticks (which would balloon scope for what's meant to be a sparkline).
  const lblMax = document.createElementNS(ns, 'text');
  lblMax.setAttribute('x', String(padX));
  lblMax.setAttribute('y', String(padY + 4));
  lblMax.setAttribute('font-size', '9');
  lblMax.setAttribute('fill', 'currentColor');
  lblMax.textContent = formatNumeric(maxV);
  svg.appendChild(lblMax);
  const lblMin = document.createElementNS(ns, 'text');
  lblMin.setAttribute('x', String(padX));
  lblMin.setAttribute('y', String(h - padY));
  lblMin.setAttribute('font-size', '9');
  lblMin.setAttribute('fill', 'currentColor');
  lblMin.textContent = formatNumeric(minV);
  svg.appendChild(lblMin);
}

function clearTable(): void {
  if (dom.tbodyTimeseries) dom.tbodyTimeseries.replaceChildren();
}

function clearSparkline(): void {
  if (dom.timeseriesSparkline) dom.timeseriesSparkline.replaceChildren();
}

function setStatus(text: string): void {
  if (dom.timeseriesStatus) dom.timeseriesStatus.textContent = text;
}

function formatValue(v: unknown): string {
  if (v === null || v === undefined) return '';
  if (typeof v === 'number' || typeof v === 'string' || typeof v === 'boolean') return String(v);
  try {
    return JSON.stringify(v);
  } catch {
    return String(v);
  }
}

function formatNumeric(n: number): string {
  // Three significant digits is enough for a sparkline label without overlapping the line.
  if (!Number.isFinite(n)) return '';
  const abs = Math.abs(n);
  if (abs >= 1000 || abs < 0.01) return n.toExponential(2);
  return n.toFixed(2);
}

/**
 * Converts a value from a {@code <input type="datetime-local">} (e.g. "2026-05-08T10:00") into a
 * UTC ISO-8601 instant ("2026-05-08T10:00:00Z"). The browser stores datetime-local in the user's
 * local clock, but we want the wire value to be UTC — so we treat the input as if the wall-clock
 * time the user typed was already UTC. This matches how operators usually think about
 * timeseries windows ("show me 10:00–11:00 UTC") and keeps the conversion logic-free.
 */
function toUtcIso(local: string): string {
  if (!local) return '';
  const trimmed = local.length === 16 ? `${local}:00` : local;
  return trimmed.endsWith('Z') ? trimmed : `${trimmed}Z`;
}

function toLocalInputValue(date: Date): string {
  // datetime-local expects "YYYY-MM-DDTHH:MM" without seconds/zone — but with step="1" we keep
  // seconds for precision. Render as if the date's UTC components were the local clock so the
  // user sees / writes UTC consistently across the form.
  const pad = (n: number) => n.toString().padStart(2, '0');
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}` +
      `T${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}:${pad(date.getUTCSeconds())}`;
}
