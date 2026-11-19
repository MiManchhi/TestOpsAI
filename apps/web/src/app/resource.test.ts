import { afterEach, expect, it, vi } from 'vitest';
import { effectScope, nextTick, ref } from 'vue';
import { auth } from '../features/auth/session';
import { projects } from '../features/projects/context';
import { idempotencyKey, useProjectAction, useResource } from './resource';
vi.mock('../features/auth/session', async () => {
  const { reactive } = await import('vue');
  return { auth: { state: reactive({ phase: 'authenticated' }) } };
});
vi.mock('../features/projects/context', async () => {
  const { reactive } = await import('vue');
  return { projects: { state: reactive({ selectedId: '1' }) } };
});
const scope = effectScope();
afterEach(() => {
  scope.stop();
  vi.restoreAllMocks();
});
it('cancels old reads, clears visible data synchronously and ignores late responses', async () => {
  const active = effectScope();
  const key = ref('first');
  const pending: Array<(value: string) => void> = [];
  const signals: AbortSignal[] = [];
  const resource = active.run(() =>
    useResource(
      () => key.value,
      (signal) => {
        signals.push(signal);
        return new Promise<string>((resolve) => pending.push(resolve));
      },
    ),
  )!;
  pending[0]?.('first result');
  await nextTick();
  expect(resource.data.value).toBe('first result');
  key.value = 'second';
  expect(resource.data.value).toBeNull();
  expect(signals[0]?.aborted).toBe(true);
  key.value = 'third';
  pending[2]?.('current result');
  pending[1]?.('obsolete result');
  await nextTick();
  expect(resource.data.value).toBe('current result');
  active.stop();
  expect(signals[2]?.aborted).toBe(true);
});
it('suppresses mutation success when the project scope changes before response', async () => {
  const active = effectScope();
  const action = active.run(useProjectAction)!;
  const done = vi.fn();
  let resolve: (value: string) => void = () => {};
  const pending = action.run(
    () =>
      new Promise<string>((finish) => {
        resolve = finish;
      }),
    done,
  );
  // The readonly facade is intentionally bypassed only on this reactive test double.
  Object.assign(projects.state, { selectedId: '2' });
  expect(action.busy.value).toBe(false);
  resolve('old project saved');
  await pending;
  expect(done).not.toHaveBeenCalled();
  Object.assign(projects.state, { selectedId: '1' });
  Object.assign(auth.state, { phase: 'authenticated' });
  active.stop();
});
it('keeps retry identity for the same payload and replaces it when intent changes', () => {
  const key = idempotencyKey();
  const first = key('project-1/file-generation-1');
  expect(key('project-1/file-generation-1')).toBe(first);
  expect(key('project-2/file-generation-1')).not.toBe(first);
});
