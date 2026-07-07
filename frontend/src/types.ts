export type Role = "CUSTOMER" | "OPERATIONS" | "ADMIN";

export type UserSession = {
  authenticated: boolean;
  subject: string;
  username: string;
  email?: string;
  roles: Role[];
};

export type ApiError = {
  code: string;
  message: string;
  traceId?: string;
  timestamp?: string;
};

export type WalletBalance = {
  walletId: string;
  currency: string;
  status: string;
  ledgerBalanceMinor: number;
  heldAmountMinor: number;
  availableBalanceMinor: number;
};

export type WalletStatementLine = {
  postingId: string;
  journalEntryId: string;
  journalType: string;
  referenceType: string;
  referenceId: string;
  description: string;
  occurredAt: string;
  direction: "DEBIT" | "CREDIT";
  amountMinor: number;
  signedAmountMinor: number;
  currency: string;
  lineNumber: number;
};

export type WalletStatement = {
  walletId: string;
  currency: string;
  status: string;
  ledgerBalanceMinor: number;
  page: number;
  size: number;
  totalEntries: number;
  hasNext: boolean;
  entries: WalletStatementLine[];
};

export type TransferRequest = {
  sourceWalletId: string;
  destinationWalletId: string;
  amountMinor: number;
  currency: string;
};

export type TransferResponse = TransferRequest & {
  id: string;
  journalEntryId: string;
  status: string;
};

export type PaymentIntentRequest = {
  sourceWalletId: string;
  merchantId: string;
  amountMinor: number;
  currency: string;
};

export type PaymentIntent = PaymentIntentRequest & {
  id: string;
  fundsHoldId?: string;
  captureJournalEntryId?: string;
  refundJournalEntryId?: string;
  status: string;
  createdAt: string;
  authorizedAt?: string;
  canceledAt?: string;
  capturedAt?: string;
  refundedAt?: string;
};

export type OperationPage<T> = {
  page: number;
  size: number;
  totalElements: number;
  hasNext: boolean;
  items: T[];
};

export type OperationalCustomer = {
  id: string;
  customerType: string;
  legalName: string;
  email: string;
  status: string;
  kycStatus: string;
  createdAt: string;
};

export type OperationalWallet = {
  id: string;
  customerId: string;
  currency: string;
  status: string;
  createdAt: string;
};

export type OperationalPaymentIntent = {
  id: string;
  customerId: string;
  sourceWalletId: string;
  merchantId: string;
  amountMinor: number;
  currency: string;
  status: string;
  createdAt: string;
  authorizedAt?: string;
  capturedAt?: string;
  refundedAt?: string;
};

export type OperationalReconciliationCase = {
  id: string;
  settlementBatchId: string;
  merchantId: string;
  providerReference: string;
  status: string;
  expectedAmountMinor: number;
  actualAmountMinor: number;
  expectedCurrency: string;
  actualCurrency: string;
  discrepancyReason?: string;
  createdAt: string;
};

export type OperationalAuditEvent = {
  id: string;
  actionType: string;
  actorExternalSubject?: string;
  actorCustomerId?: string;
  resourceType: string;
  resourceId: string;
  metadata: string;
  createdAt: string;
};

export type Merchant = {
  id: string;
  legalName: string;
  displayName: string;
  status: string;
  settlementCurrencies: Array<{
    currency: string;
    settlementDelayDays: number;
    enabled: boolean;
  }>;
};

export type SettlementBatch = {
  id: string;
  merchantId: string;
  currency: string;
  status: string;
  totalAmountMinor: number;
  journalEntryId: string;
};

export type DemoSeed = {
  customerId: string;
  customerWalletId: string;
  customerWalletBalanceMinor: number;
  recipientCustomerId: string;
  recipientWalletId: string;
  merchantId: string;
  currency: string;
  topUpCreated: boolean;
};
