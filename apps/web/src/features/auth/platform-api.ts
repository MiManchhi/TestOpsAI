import { jsonBody, resourceId } from '../../app/api-contract';
import { requestJSON } from '../../app/http';
import { readWorkspaceContract } from '../../app/workspace-contracts';

const prefix = '/api/v3/platform/accounts';

export async function listAccounts(page: number, signal: AbortSignal) {
  return readWorkspaceContract(
    'AccountPage',
    await requestJSON(`${prefix}?page=${page}&size=20`, signal),
  );
}

export async function createAccount(
  body: { username: string; displayName: string; temporaryPassword: string },
  key: string,
  signal: AbortSignal,
) {
  return readWorkspaceContract(
    'Account',
    await requestJSON(prefix, signal, jsonBody('POST', body, key)),
  );
}

export async function setAccountStatus(
  id: string,
  body: { enabled: boolean; revision: number },
  key: string,
  signal: AbortSignal,
) {
  return readWorkspaceContract(
    'Account',
    await requestJSON(`${prefix}/${resourceId(id)}`, signal, jsonBody('PATCH', body, key)),
  );
}

export async function resetAccountPassword(
  id: string,
  body: { temporaryPassword: string; revision: number },
  key: string,
  signal: AbortSignal,
) {
  await requestJSON(`${prefix}/${resourceId(id)}/reset`, signal, jsonBody('POST', body, key));
}
