import { beforeEach, describe, expect, it, vi } from "vitest";
import { clearTransferDraft, getOrCreateTransferDraft } from "./idempotency";

describe("transfer idempotency draft", () => {
  beforeEach(() => {
    vi.stubGlobal("crypto", {
      randomUUID: vi
        .fn()
        .mockReturnValueOnce("key-1")
        .mockReturnValueOnce("key-2")
    });
  });

  it("reuses the same key for the same user action", () => {
    const storage = new MemoryStorage();
    const request = {
      sourceWalletId: "source",
      destinationWalletId: "destination",
      amountMinor: 2500,
      currency: "TRY"
    };

    const first = getOrCreateTransferDraft(request, storage);
    const retry = getOrCreateTransferDraft(request, storage);

    expect(first.idempotencyKey).toBe("key-1");
    expect(retry.idempotencyKey).toBe("key-1");
  });

  it("creates a new key when the request fingerprint changes", () => {
    const storage = new MemoryStorage();

    const first = getOrCreateTransferDraft(
      {
        sourceWalletId: "source",
        destinationWalletId: "destination",
        amountMinor: 2500,
        currency: "TRY"
      },
      storage
    );
    const changed = getOrCreateTransferDraft(
      {
        sourceWalletId: "source",
        destinationWalletId: "destination",
        amountMinor: 2600,
        currency: "TRY"
      },
      storage
    );

    expect(first.idempotencyKey).toBe("key-1");
    expect(changed.idempotencyKey).toBe("key-2");
  });

  it("clears the draft after a successful transfer", () => {
    const storage = new MemoryStorage();
    getOrCreateTransferDraft(
      {
        sourceWalletId: "source",
        destinationWalletId: "destination",
        amountMinor: 2500,
        currency: "TRY"
      },
      storage
    );

    clearTransferDraft(storage);

    expect(storage.getItem("payledger.transferDraft")).toBeNull();
  });
});

class MemoryStorage {
  private readonly values = new Map<string, string>();

  getItem(key: string): string | null {
    return this.values.get(key) ?? null;
  }

  setItem(key: string, value: string): void {
    this.values.set(key, value);
  }

  removeItem(key: string): void {
    this.values.delete(key);
  }
}
