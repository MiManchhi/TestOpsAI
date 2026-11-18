import { onScopeDispose, ref, shallowRef, watch, type WatchSource } from 'vue';
import { auth } from '../features/auth/session';
import { projects } from '../features/projects/context';
import { errorMessage, requestId } from './http';

/** Page-owned requests: changed scope clears visible data before starting new work. */
export function useResource<T>(
  key: WatchSource<string>,
  read: (signal: AbortSignal) => Promise<T>,
) {
  const data = shallowRef<T | null>(null);
  const loading = ref(false);
  const error = ref('');
  const trace = ref('');
  let controller: AbortController | undefined;
  let version = 0;
  async function load(preserve = false) {
    const generation = ++version;
    controller?.abort();
    controller = new AbortController();
    const signal = controller.signal;
    if (!preserve) data.value = null;
    error.value = trace.value = '';
    loading.value = false;
    if (!projects.state.selectedId || auth.state.phase !== 'authenticated') return;
    loading.value = true;
    try {
      const value = await read(signal);
      if (!signal.aborted && generation === version) data.value = value;
    } catch (cause) {
      if (!signal.aborted && generation === version) {
        data.value = null;
        error.value = errorMessage(cause);
        trace.value = requestId(cause);
      }
    } finally {
      if (generation === version) loading.value = false;
    }
  }
  const reload = () => load();
  const refresh = () => load(true);
  watch([key, () => projects.state.selectedId, () => auth.state.phase], () => void reload(), {
    immediate: true,
    flush: 'sync',
  });
  onScopeDispose(() => {
    version++;
    controller?.abort();
  });
  return { data, loading, error, trace, reload, refresh };
}

export function useProjectAction() {
  const busy = ref(false);
  const error = ref('');
  const notice = ref('');
  const trace = ref('');
  let controller: AbortController | undefined;
  function reset() {
    controller?.abort();
    busy.value = false;
    error.value = notice.value = trace.value = '';
  }
  watch([() => projects.state.selectedId, () => auth.state.phase], reset, { flush: 'sync' });
  onScopeDispose(reset);
  async function run<T>(
    action: (signal: AbortSignal) => Promise<T>,
    done: (value: T) => void | Promise<void>,
  ) {
    if (busy.value) return;
    controller = new AbortController();
    const signal = controller.signal;
    busy.value = true;
    error.value = notice.value = trace.value = '';
    try {
      const value = await action(signal);
      if (!signal.aborted) await done(value);
    } catch (cause) {
      if (!signal.aborted) {
        error.value = errorMessage(cause);
        trace.value = requestId(cause);
      }
    } finally {
      if (!signal.aborted) busy.value = false;
    }
  }
  return { busy, error, notice, trace, run };
}

export function idempotencyKey() {
  let previous = '';
  let key = '';
  return (fingerprint: string) => {
    if (fingerprint !== previous || !key) {
      previous = fingerprint;
      key = crypto.randomUUID();
    }
    return key;
  };
}
