import type { TransferRequest } from "./types";

const TRANSFER_DRAFT_KEY = "payledger.transferDraft";

export type TransferDraft = TransferRequest & {
  idempotencyKey: string;
};

export type DraftStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

export function getOrCreateTransferDraft(
  request: TransferRequest,
  storage: DraftStorage = window.localStorage
): TransferDraft {
  const existing = readTransferDraft(storage);

  if (existing && sameTransferRequest(existing, request)) {
    return existing;
  }

  const draft: TransferDraft = {
    ...request,
    idempotencyKey: crypto.randomUUID()
  };
  storage.setItem(TRANSFER_DRAFT_KEY, JSON.stringify(draft));
  return draft;
}

export function clearTransferDraft(
  storage: DraftStorage = window.localStorage
): void {
  storage.removeItem(TRANSFER_DRAFT_KEY);
}

function readTransferDraft(storage: DraftStorage): TransferDraft | null {
  const raw = storage.getItem(TRANSFER_DRAFT_KEY);

  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as TransferDraft;
  } catch {
    storage.removeItem(TRANSFER_DRAFT_KEY);
    return null;
  }
}

function sameTransferRequest(
  draft: TransferDraft,
  request: TransferRequest
): boolean {
  return (
    draft.sourceWalletId === request.sourceWalletId &&
    draft.destinationWalletId === request.destinationWalletId &&
    draft.amountMinor === request.amountMinor &&
    draft.currency.toUpperCase() === request.currency.toUpperCase()
  );
}
