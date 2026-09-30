/*
    Copyright 2013-2026, Sumeet Chhetri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

// M15: the editor's watch mode — every successful save re-runs the script in the same view. A save during a run
// stops that run and queues exactly one re-run for when it has finished (saves in a burst collapse into one).

export interface WatchState {
  runId: number; // bumps to (re)mount the runner
  running: boolean;
  pending: boolean; // a save arrived while running
}

export type WatchAction = 'start' | 'stop' | 'none';

export const initialWatch: WatchState = { runId: 0, running: false, pending: false };

/** Watch turned on: run once straight away. */
export function watchOn(s: WatchState): [WatchState, WatchAction] {
  return [{ runId: s.runId + 1, running: true, pending: false }, 'start'];
}

/** A save succeeded. */
export function onSaved(s: WatchState): [WatchState, WatchAction] {
  if (!s.running) return [{ runId: s.runId + 1, running: true, pending: false }, 'start'];
  if (s.pending) return [s, 'none']; // the stop is already on its way
  return [{ ...s, pending: true }, 'stop'];
}

/** The current run finished (passed, failed or stopped). */
export function onFinished(s: WatchState): [WatchState, WatchAction] {
  if (s.pending) return [{ runId: s.runId + 1, running: true, pending: false }, 'start'];
  return [{ ...s, running: false }, 'none'];
}
