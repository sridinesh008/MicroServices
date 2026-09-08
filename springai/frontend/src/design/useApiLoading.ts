import { useSyncExternalStore } from "react";
import { subscribe, getSnapshot } from "../api/loadingStore";

/** True whenever at least one backend request (any api.* call, or the chat stream) is in flight. */
export function useApiLoading(): boolean {
  return useSyncExternalStore(subscribe, getSnapshot, getSnapshot);
}
