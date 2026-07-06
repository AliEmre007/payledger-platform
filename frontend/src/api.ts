import { config } from "./config";
import type {
  ApiError,
  Merchant,
  OperationPage,
  OperationalAuditEvent,
  OperationalCustomer,
  OperationalPaymentIntent,
  OperationalReconciliationCase,
  OperationalWallet,
  PaymentIntent,
  PaymentIntentRequest,
  SettlementBatch,
  TransferRequest,
  TransferResponse,
  WalletBalance,
  WalletStatement
} from "./types";

type TokenProvider = () => Promise<string | null>;

export class ApiClient {
  constructor(private readonly tokenProvider: TokenProvider) {}

  walletBalance(walletId: string): Promise<WalletBalance> {
    return this.request(`/api/v1/wallets/${walletId}/balance`);
  }

  walletStatement(
    walletId: string,
    page: number,
    size: number
  ): Promise<WalletStatement> {
    return this.request(
      `/api/v1/wallets/${walletId}/statement?page=${page}&size=${size}`
    );
  }

  createTransfer(
    request: TransferRequest,
    idempotencyKey: string
  ): Promise<TransferResponse> {
    return this.request("/api/v1/transfers", {
      method: "POST",
      idempotencyKey,
      body: request
    });
  }

  authorizePayment(
    request: PaymentIntentRequest,
    idempotencyKey: string
  ): Promise<PaymentIntent> {
    return this.request("/api/v1/payment-intents", {
      method: "POST",
      idempotencyKey,
      body: request
    });
  }

  cancelPayment(paymentIntentId: string): Promise<PaymentIntent> {
    return this.request(`/api/v1/payment-intents/${paymentIntentId}/cancel`, {
      method: "POST"
    });
  }

  customers(): Promise<OperationPage<OperationalCustomer>> {
    return this.request("/api/v1/operations/customers?size=10");
  }

  wallets(): Promise<OperationPage<OperationalWallet>> {
    return this.request("/api/v1/operations/wallets?size=10");
  }

  paymentIntents(): Promise<OperationPage<OperationalPaymentIntent>> {
    return this.request("/api/v1/operations/payment-intents?size=10");
  }

  reconciliationCases(): Promise<
    OperationPage<OperationalReconciliationCase>
  > {
    return this.request("/api/v1/operations/reconciliation-cases?size=10");
  }

  auditEvents(): Promise<OperationPage<OperationalAuditEvent>> {
    return this.request("/api/v1/operations/audit-events?size=10");
  }

  submitKyc(customerId: string, reason: string) {
    return this.operationReason(
      `/api/v1/operations/customers/${customerId}/kyc/submit`,
      reason
    );
  }

  approveKyc(customerId: string, reason: string) {
    return this.operationReason(
      `/api/v1/operations/customers/${customerId}/kyc/approve`,
      reason
    );
  }

  rejectKyc(customerId: string, reason: string) {
    return this.operationReason(
      `/api/v1/operations/customers/${customerId}/kyc/reject`,
      reason
    );
  }

  freezeWallet(walletId: string, reason: string) {
    return this.operationReason(
      `/api/v1/operations/wallets/${walletId}/freeze`,
      reason
    );
  }

  unfreezeWallet(walletId: string, reason: string) {
    return this.operationReason(
      `/api/v1/operations/wallets/${walletId}/unfreeze`,
      reason
    );
  }

  closeWallet(walletId: string, reason: string) {
    return this.operationReason(
      `/api/v1/operations/wallets/${walletId}/close`,
      reason
    );
  }

  onboardMerchant(body: {
    legalName: string;
    displayName: string;
    settlementCurrency: string;
    settlementDelayDays: number;
    reason: string;
  }): Promise<Merchant> {
    return this.request("/api/v1/operations/merchants", {
      method: "POST",
      body
    });
  }

  activateMerchant(merchantId: string, reason: string): Promise<Merchant> {
    return this.operationReason(
      `/api/v1/operations/merchants/${merchantId}/activate`,
      reason
    );
  }

  capturePayment(
    paymentIntentId: string,
    reason: string,
    idempotencyKey: string
  ): Promise<PaymentIntent> {
    return this.request(
      `/api/v1/operations/payment-intents/${paymentIntentId}/capture`,
      {
        method: "POST",
        idempotencyKey,
        body: { reason }
      }
    );
  }

  refundPayment(
    paymentIntentId: string,
    reason: string,
    idempotencyKey: string
  ): Promise<PaymentIntent> {
    return this.request(
      `/api/v1/operations/payment-intents/${paymentIntentId}/refund`,
      {
        method: "POST",
        idempotencyKey,
        body: { reason }
      }
    );
  }

  createSettlement(body: {
    merchantId: string;
    currency: string;
    reason: string;
  }): Promise<SettlementBatch> {
    return this.request("/api/v1/operations/settlements", {
      method: "POST",
      idempotencyKey: crypto.randomUUID(),
      body
    });
  }

  reconcileSettlement(
    settlementBatchId: string,
    body: {
      providerReference: string;
      actualAmountMinor: number;
      actualCurrency: string;
      reason: string;
    }
  ) {
    return this.request(
      `/api/v1/operations/settlements/${settlementBatchId}/reconcile`,
      {
        method: "POST",
        body
      }
    );
  }

  private operationReason<T = unknown>(
    path: string,
    reason: string
  ): Promise<T> {
    return this.request(path, {
      method: "POST",
      body: { reason }
    });
  }

  private async request<T>(
    path: string,
    options: {
      method?: "GET" | "POST";
      body?: unknown;
      idempotencyKey?: string;
    } = {}
  ): Promise<T> {
    const token = await this.tokenProvider();
    const headers = new Headers({
      Accept: "application/json"
    });

    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }

    if (options.body !== undefined) {
      headers.set("Content-Type", "application/json");
    }

    if (options.idempotencyKey) {
      headers.set("Idempotency-Key", options.idempotencyKey);
    }

    const response = await fetch(`${config.apiBaseUrl}${path}`, {
      method: options.method ?? "GET",
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body)
    });

    if (!response.ok) {
      throw await toApiError(response);
    }

    return (await response.json()) as T;
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    return (await response.json()) as ApiError;
  } catch {
    return {
      code: `HTTP_${response.status}`,
      message: response.statusText || "Request failed."
    };
  }
}
