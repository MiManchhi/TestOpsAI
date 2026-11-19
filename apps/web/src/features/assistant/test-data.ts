// Self-authored frozen examples, used only by tests.
import examples from '../../../../../docs/contracts/examples.json';
import { readContract } from '../../app/contracts';
export const conversation = readContract(
  'Conversation',
  examples.instances.find((item) => item.schema === 'Conversation')?.data,
);
export const userMessage = readContract(
  'Message',
  examples.instances.find((item) => item.schema === 'Message')?.data,
);
export const assistantMessage = readContract(
  'Message',
  examples.instances.filter((item) => item.schema === 'Message').at(-1)?.data,
);
export const answer = readContract('Answer', assistantMessage.answer);
export const context = readContract('MessageContext', userMessage.context);
export const turn = readContract(
  'Turn',
  examples.instances.find((item) => item.schema === 'Turn')?.data,
);
export const queued = { ...turn, status: 'QUEUED' as const, lastSequence: 0 };
export const draft = readContract(
  'Draft',
  examples.instances.find((item) => item.schema === 'Draft')?.data,
);
export const events = examples.instances
  .filter((item) => item.schema === 'TurnEvent')
  .map((item) => readContract('TurnEvent', item.data));
export function messagePage(items = [userMessage, assistantMessage]) {
  return { items, page: 0, size: 20, total: items.length };
}
