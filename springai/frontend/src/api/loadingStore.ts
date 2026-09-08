/** Tiny pub-sub in-flight-request counter, instrumented once in `client.ts` -- covers every
 * api.* call and the chat stream automatically, so no call site has to remember to report. */

type Listener = () => void;

let count = 0;
const listeners = new Set<Listener>();

function notify() {
  listeners.forEach((l) => l());
}

export function beginRequest() {
  count += 1;
  notify();
}

export function endRequest() {
  count = Math.max(0, count - 1);
  notify();
}

export function subscribe(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getSnapshot(): boolean {
  return count > 0;
}
