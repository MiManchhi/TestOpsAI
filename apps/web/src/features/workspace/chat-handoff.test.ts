import { describe, expect, it } from 'vitest';
import { carryFromChat, handoffId, returnToChat } from './chat-handoff';
const id = '12345678-1234-4123-8123-123456789abc';
const make = (fileName: string, expiresAt: string) => ({
  attachmentId: id,
  fileName,
  expiresAt,
  sha256: 'a'.repeat(64),
});
describe('转入业务表单', () => {
  it('带入本人对话中仍有效的文件', () => {
    const ready = carryFromChat(id, [
      make('output.xml', '2099-01-01'),
      make('需求.md', '2000-01-01'),
    ]);
    expect(ready.run.output).toBe(id);
    expect(ready.document.attachmentId).toBeUndefined();
  });
  it('仅构建本项目会话的返回地址', () => {
    expect(returnToChat('21', { returnConversationId: id })).toBe('/p/21/chat/' + id);
    expect(handoffId('https://example.com')).toBe('');
  });
});
