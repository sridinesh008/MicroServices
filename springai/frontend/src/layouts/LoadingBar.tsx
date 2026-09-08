import { useApiLoading } from "../design/useApiLoading";

/** Slim indeterminate progress bar, pinned to the very top, visible whenever any backend
 * request (any api.* call, or the chat stream) is in flight -- instrumented once in
 * `api/client.ts` so every request shows it automatically, nothing to wire per call site. */
export default function LoadingBar() {
  const loading = useApiLoading();
  return <div className={`ft-loadingbar ${loading ? "active" : ""}`} aria-hidden="true" />;
}
