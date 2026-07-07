import { createIcons, Activity, ArrowRightLeft, Banknote, LogIn, LogOut, RefreshCw, ShieldCheck } from "lucide";
import { ApiClient } from "./api";
import { KeycloakAuthClient, hasOperationsRole } from "./auth";
import { clearTransferDraft, getOrCreateTransferDraft } from "./idempotency";
import type {
  ApiError,
  DemoSeed,
  OperationPage,
  OperationalAuditEvent,
  OperationalCustomer,
  OperationalPaymentIntent,
  OperationalReconciliationCase,
  OperationalWallet,
  PaymentIntent,
  TransferRequest,
  UserSession,
  WalletBalance,
  WalletStatement
} from "./types";
import { navigationFor, roleLabel } from "./view-model";
import "./styles.css";

type UiState = {
  session: UserSession;
  activeView: "customer" | "operations";
  message: string;
  walletId: string;
  balance?: WalletBalance;
  statement?: WalletStatement;
  lastTransfer?: string;
  lastPayment?: PaymentIntent;
  lastDemoSeed?: DemoSeed;
  operations?: OperationsSnapshot;
};

type OperationsSnapshot = {
  customers: OperationPage<OperationalCustomer>;
  wallets: OperationPage<OperationalWallet>;
  payments: OperationPage<OperationalPaymentIntent>;
  reconciliation: OperationPage<OperationalReconciliationCase>;
  audit: OperationPage<OperationalAuditEvent>;
};

const root = document.querySelector<HTMLDivElement>("#app");

if (!root) {
  throw new Error("Missing #app root element.");
}

const appRoot = root;

const auth = new KeycloakAuthClient();
const api = new ApiClient(() => auth.token());

let state: UiState = {
  session: {
    authenticated: false,
    subject: "",
    username: "",
    roles: []
  },
  activeView: "customer",
  message: "",
  walletId: ""
};

void boot();

async function boot() {
  try {
    state.session = await auth.init();
  } catch (error) {
    state.message = readableError(error);
  }

  render();
}

function render() {
  appRoot.innerHTML = `
    <div class="shell">
      <header class="topbar">
        <div>
          <p class="eyebrow">Simulated wallet operations</p>
          <h1>PayLedger</h1>
        </div>
        <div class="identity">${renderIdentity()}</div>
      </header>
      ${state.session.authenticated ? renderWorkspace() : renderSignedOut()}
    </div>
  `;

  bindCommonActions();
  if (state.session.authenticated) {
    bindWorkspaceActions();
  }
  createIcons({
    icons: { Activity, ArrowRightLeft, Banknote, LogIn, LogOut, RefreshCw, ShieldCheck }
  });
}

function renderIdentity() {
  if (!state.session.authenticated) {
    return `
      <button class="button primary" data-action="login">
        <i data-lucide="log-in"></i><span>Log in</span>
      </button>
    `;
  }

  return `
    <div class="principal">
      <strong>${escapeHtml(state.session.username)}</strong>
      <span>${escapeHtml(roleLabel(state.session.roles))}</span>
    </div>
    <button class="icon-button" data-action="logout" title="Log out" aria-label="Log out">
      <i data-lucide="log-out"></i>
    </button>
  `;
}

function renderSignedOut() {
  return `
    <main class="signed-out">
      <section class="login-panel">
        <div>
          <p class="eyebrow">Authorization Code + PKCE</p>
          <h2>Sign in with Keycloak</h2>
          <p>Use the local realm to access customer and operations workflows.</p>
        </div>
        <button class="button primary" data-action="login">
          <i data-lucide="shield-check"></i><span>Continue</span>
        </button>
      </section>
    </main>
  `;
}

function renderWorkspace() {
  const navigation = navigationFor(state.session)
    .map(
      (item) => `
        <button
          class="tab ${state.activeView === item.id ? "active" : ""}"
          data-view="${item.id}"
        >
          ${item.label}
        </button>
      `
    )
    .join("");

  return `
    <nav class="tabs" aria-label="Workspace">${navigation}</nav>
    ${state.message ? `<div class="notice">${escapeHtml(state.message)}</div>` : ""}
    <main class="workspace">
      ${state.activeView === "operations" ? renderOperationsView() : renderCustomerView()}
    </main>
  `;
}

function renderCustomerView() {
  return `
    <section class="band two-column">
      <div class="panel">
        <div class="panel-header">
          <h2>Wallet</h2>
          <button class="icon-button" data-action="refresh-wallet" title="Refresh wallet">
            <i data-lucide="refresh-cw"></i>
          </button>
        </div>
        <label>Wallet ID
          <input id="wallet-id" value="${escapeAttribute(state.walletId)}" placeholder="UUID" />
        </label>
        ${state.balance ? renderBalance(state.balance) : emptyState("Load an owned wallet balance.")}
      </div>
      <div class="panel">
        <div class="panel-header">
          <h2>Statement</h2>
          <button class="icon-button" data-action="refresh-statement" title="Refresh statement">
            <i data-lucide="activity"></i>
          </button>
        </div>
        ${state.statement ? renderStatement(state.statement) : emptyState("Statement rows appear after ledger postings.")}
      </div>
    </section>
    <section class="band two-column">
      <form class="panel" id="transfer-form">
        <h2>Transfer</h2>
        <label>Source wallet <input name="sourceWalletId" value="${escapeAttribute(state.walletId)}" required /></label>
        <label>Destination wallet <input name="destinationWalletId" required /></label>
        <div class="inline-fields">
          <label>Amount minor <input name="amountMinor" type="number" min="1" required /></label>
          <label>Currency <input name="currency" maxlength="3" value="${escapeAttribute(state.balance?.currency ?? "TRY")}" required /></label>
        </div>
        <button class="button" type="submit">
          <i data-lucide="arrow-right-left"></i><span>Send transfer</span>
        </button>
        ${state.lastTransfer ? `<p class="result">${escapeHtml(state.lastTransfer)}</p>` : ""}
      </form>
      <form class="panel" id="payment-form">
        <h2>Payment Authorization</h2>
        <label>Source wallet <input name="sourceWalletId" value="${escapeAttribute(state.walletId)}" required /></label>
        <label>Merchant ID <input name="merchantId" required /></label>
        <div class="inline-fields">
          <label>Amount minor <input name="amountMinor" type="number" min="1" required /></label>
          <label>Currency <input name="currency" maxlength="3" value="${escapeAttribute(state.balance?.currency ?? "TRY")}" required /></label>
        </div>
        <button class="button" type="submit">
          <i data-lucide="banknote"></i><span>Authorize</span>
        </button>
        ${renderPaymentResult()}
      </form>
    </section>
  `;
}

function renderBalance(balance: WalletBalance) {
  return `
    <div class="balance-grid">
      <div><span>Ledger</span><strong>${formatMinor(balance.ledgerBalanceMinor, balance.currency)}</strong></div>
      <div><span>Held</span><strong>${formatMinor(balance.heldAmountMinor, balance.currency)}</strong></div>
      <div><span>Available</span><strong>${formatMinor(balance.availableBalanceMinor, balance.currency)}</strong></div>
      <div><span>Status</span><strong>${escapeHtml(balance.status)}</strong></div>
    </div>
  `;
}

function renderStatement(statement: WalletStatement) {
  if (statement.entries.length === 0) {
    return emptyState("No postings for this wallet page.");
  }

  return `
    <div class="table-wrap">
      <table>
        <thead><tr><th>Occurred</th><th>Type</th><th>Direction</th><th>Amount</th></tr></thead>
        <tbody>
          ${statement.entries
            .map(
              (entry) => `
                <tr>
                  <td>${formatDate(entry.occurredAt)}</td>
                  <td>${escapeHtml(entry.journalType)}</td>
                  <td>${escapeHtml(entry.direction)}</td>
                  <td>${formatMinor(entry.signedAmountMinor, entry.currency)}</td>
                </tr>
              `
            )
            .join("")}
        </tbody>
      </table>
    </div>
  `;
}

function renderPaymentResult() {
  if (!state.lastPayment) {
    return "";
  }

  const payment = state.lastPayment;
  return `
    <div class="result">
      <strong>${escapeHtml(payment.status)}</strong>
      <span>${escapeHtml(payment.id)}</span>
      ${
        payment.status === "AUTHORIZED"
          ? `<button class="button subtle" type="button" data-action="cancel-payment" data-id="${escapeAttribute(payment.id)}">Cancel</button>`
          : ""
      }
    </div>
  `;
}

function renderOperationsView() {
  if (!hasOperationsRole(state.session)) {
    return `<section class="band">${emptyState("Operations require OPERATIONS or ADMIN.")}</section>`;
  }

  return `
    <section class="band ops-actions">
      <div class="panel">
        <h2>Demo Seed</h2>
        <button class="button" type="button" data-action="seed-demo">
          <i data-lucide="refresh-cw"></i><span>Seed demo data</span>
        </button>
        ${state.lastDemoSeed ? renderDemoSeed(state.lastDemoSeed) : emptyState("Create demo wallet funding, recipient, and merchant.")}
      </div>
      <form class="panel" id="kyc-form">
        <h2>KYC Review</h2>
        <label>Customer ID <input name="customerId" required /></label>
        <label>Reason <input name="reason" required /></label>
        <div class="segmented">
          <button type="submit" name="decision" value="submit">Submit</button>
          <button type="submit" name="decision" value="approve">Approve</button>
          <button type="submit" name="decision" value="reject">Reject</button>
        </div>
      </form>
      <form class="panel" id="wallet-lifecycle-form">
        <h2>Wallet Lifecycle</h2>
        <label>Wallet ID <input name="walletId" required /></label>
        <label>Reason <input name="reason" required /></label>
        <div class="segmented">
          <button type="submit" name="transition" value="freeze">Freeze</button>
          <button type="submit" name="transition" value="unfreeze">Unfreeze</button>
          <button type="submit" name="transition" value="close">Close</button>
        </div>
      </form>
      <form class="panel" id="merchant-form">
        <h2>Merchant</h2>
        <label>Legal name <input name="legalName" required /></label>
        <label>Display name <input name="displayName" required /></label>
        <div class="inline-fields">
          <label>Currency <input name="settlementCurrency" maxlength="3" value="TRY" required /></label>
          <label>Delay days <input name="settlementDelayDays" type="number" min="0" max="30" value="1" required /></label>
        </div>
        <label>Reason <input name="reason" required /></label>
        <button class="button" type="submit">Onboard</button>
      </form>
      <form class="panel" id="payment-ops-form">
        <h2>Capture / Refund</h2>
        <label>Payment intent ID <input name="paymentIntentId" required /></label>
        <label>Reason <input name="reason" required /></label>
        <div class="segmented">
          <button type="submit" name="action" value="capture">Capture</button>
          <button type="submit" name="action" value="refund">Refund</button>
        </div>
      </form>
      <form class="panel" id="settlement-form">
        <h2>Settlement</h2>
        <label>Merchant ID <input name="merchantId" required /></label>
        <label>Currency <input name="currency" maxlength="3" value="TRY" required /></label>
        <label>Reason <input name="reason" required /></label>
        <button class="button" type="submit">Create batch</button>
      </form>
      <form class="panel" id="reconciliation-form">
        <h2>Reconciliation</h2>
        <label>Settlement batch ID <input name="settlementBatchId" required /></label>
        <label>Provider reference <input name="providerReference" required /></label>
        <div class="inline-fields">
          <label>Actual amount <input name="actualAmountMinor" type="number" min="0" required /></label>
          <label>Currency <input name="actualCurrency" maxlength="3" value="TRY" required /></label>
        </div>
        <label>Reason <input name="reason" required /></label>
        <button class="button" type="submit">Reconcile</button>
      </form>
    </section>
    <section class="band">
      <div class="panel-header">
        <h2>Operational Reads</h2>
        <button class="icon-button" data-action="refresh-operations" title="Refresh operations">
          <i data-lucide="refresh-cw"></i>
        </button>
      </div>
      ${state.operations ? renderOperationsSnapshot(state.operations) : emptyState("Refresh operations data.")}
    </section>
  `;
}

function renderDemoSeed(seed: DemoSeed) {
  return `
    <div class="result stack">
      <strong>${seed.topUpCreated ? "Seed created" : "Seed already existed"}</strong>
      <span>Wallet: ${escapeHtml(seed.customerWalletId)}</span>
      <span>Recipient: ${escapeHtml(seed.recipientWalletId)}</span>
      <span>Merchant: ${escapeHtml(seed.merchantId)}</span>
      <span>Balance: ${formatMinor(seed.customerWalletBalanceMinor, seed.currency)}</span>
    </div>
  `;
}

function renderOperationsSnapshot(snapshot: OperationsSnapshot) {
  return `
    <div class="ops-grid">
      ${renderList("Customers", snapshot.customers.items, (item) => `${item.legalName} · ${item.kycStatus}`)}
      ${renderList("Wallets", snapshot.wallets.items, (item) => `${shortId(item.id)} · ${item.status} · ${item.currency}`)}
      ${renderList("Payments", snapshot.payments.items, (item) => `${shortId(item.id)} · ${item.status} · ${formatMinor(item.amountMinor, item.currency)}`)}
      ${renderList("Reconciliation", snapshot.reconciliation.items, (item) => `${shortId(item.id)} · ${item.status} · ${item.discrepancyReason ?? "MATCHED"}`)}
      ${renderList("Audit", snapshot.audit.items, (item) => `${item.actionType} · ${item.resourceType}`)}
    </div>
  `;
}

function renderList<T>(title: string, items: T[], label: (item: T) => string) {
  return `
    <div class="list-panel">
      <h3>${escapeHtml(title)}</h3>
      ${
        items.length === 0
          ? "<p>No rows</p>"
          : `<ul>${items.map((item) => `<li>${escapeHtml(label(item))}</li>`).join("")}</ul>`
      }
    </div>
  `;
}

function bindCommonActions() {
  appRoot.querySelectorAll<HTMLElement>("[data-action='login']").forEach((button) => {
    button.addEventListener("click", () => void auth.login());
  });
  appRoot.querySelectorAll<HTMLElement>("[data-action='logout']").forEach((button) => {
    button.addEventListener("click", () => void auth.logout());
  });
}

function bindWorkspaceActions() {
  appRoot.querySelectorAll<HTMLButtonElement>("[data-view]").forEach((button) => {
    button.addEventListener("click", () => {
      state.activeView = button.dataset.view === "operations" ? "operations" : "customer";
      render();
    });
  });

  appRoot.querySelector<HTMLInputElement>("#wallet-id")?.addEventListener("input", (event) => {
    state.walletId = (event.target as HTMLInputElement).value.trim();
  });

  appRoot.querySelector("[data-action='refresh-wallet']")?.addEventListener("click", () => {
    void loadWallet();
  });
  appRoot.querySelector("[data-action='refresh-statement']")?.addEventListener("click", () => {
    void loadStatement();
  });
  appRoot.querySelector("[data-action='refresh-operations']")?.addEventListener("click", () => {
    void loadOperations();
  });
  appRoot.querySelector("[data-action='seed-demo']")?.addEventListener("click", () => {
    void seedDemoData();
  });
  appRoot.querySelector("[data-action='cancel-payment']")?.addEventListener("click", (event) => {
    const id = (event.currentTarget as HTMLElement).dataset.id;
    if (id) {
      void run(async () => {
        state.lastPayment = await api.cancelPayment(id);
        state.message = `Canceled payment ${shortId(id)}.`;
      });
    }
  });

  bindForm("#transfer-form", handleTransfer);
  bindForm("#payment-form", handlePaymentAuthorization);
  bindForm("#kyc-form", handleKyc);
  bindForm("#wallet-lifecycle-form", handleWalletLifecycle);
  bindForm("#merchant-form", handleMerchant);
  bindForm("#payment-ops-form", handlePaymentOperation);
  bindForm("#settlement-form", handleSettlement);
  bindForm("#reconciliation-form", handleReconciliation);
}

async function seedDemoData() {
  await run(async () => {
    state.lastDemoSeed = await api.seedDemoData();
    state.message = "Demo data seeded. Use the returned wallet and merchant IDs in the Customer tab.";
    await loadOperations();
  });
}

function bindForm(selector: string, handler: (form: HTMLFormElement, submitter?: HTMLElement) => void) {
  appRoot.querySelector<HTMLFormElement>(selector)?.addEventListener("submit", (event) => {
    event.preventDefault();
    handler(
      event.currentTarget as HTMLFormElement,
      (event as SubmitEvent).submitter as HTMLElement | undefined
    );
  });
}

async function loadWallet() {
  await run(async () => {
    const walletId = requireWalletId();
    state.balance = await api.walletBalance(walletId);
    state.message = `Loaded wallet ${shortId(walletId)}.`;
  });
}

async function loadStatement() {
  await run(async () => {
    const walletId = requireWalletId();
    state.statement = await api.walletStatement(walletId, 0, 20);
    state.message = `Loaded statement ${shortId(walletId)}.`;
  });
}

function handleTransfer(form: HTMLFormElement) {
  void run(async () => {
    const request = transferRequest(form);
    const draft = getOrCreateTransferDraft(request);
    const transfer = await api.createTransfer(request, draft.idempotencyKey);
    clearTransferDraft();
    state.lastTransfer = `${transfer.status} · ${shortId(transfer.id)} · ${formatMinor(transfer.amountMinor, transfer.currency)}`;
    state.message = "Transfer completed from backend response.";
    if (state.walletId) {
      state.balance = await api.walletBalance(state.walletId);
      state.statement = await api.walletStatement(state.walletId, 0, 20);
    }
  });
}

function handlePaymentAuthorization(form: HTMLFormElement) {
  void run(async () => {
    const values = formValues(form);
    state.lastPayment = await api.authorizePayment(
      {
        sourceWalletId: values.sourceWalletId,
        merchantId: values.merchantId,
        amountMinor: Number(values.amountMinor),
        currency: values.currency.toUpperCase()
      },
      crypto.randomUUID()
    );
    state.message = `Payment ${state.lastPayment.status.toLowerCase()}.`;
  });
}

function handleKyc(form: HTMLFormElement, submitter?: HTMLElement) {
  void run(async () => {
    const values = formValues(form);
    const decision = submitter?.getAttribute("value");
    if (decision === "approve") {
      await api.approveKyc(values.customerId, values.reason);
    } else if (decision === "reject") {
      await api.rejectKyc(values.customerId, values.reason);
    } else {
      await api.submitKyc(values.customerId, values.reason);
    }
    state.message = `KYC ${decision ?? "submit"} recorded.`;
    await loadOperations();
  });
}

function handleWalletLifecycle(form: HTMLFormElement, submitter?: HTMLElement) {
  void run(async () => {
    const values = formValues(form);
    const transition = submitter?.getAttribute("value");
    if (transition === "unfreeze") {
      await api.unfreezeWallet(values.walletId, values.reason);
    } else if (transition === "close") {
      await api.closeWallet(values.walletId, values.reason);
    } else {
      await api.freezeWallet(values.walletId, values.reason);
    }
    state.message = `Wallet ${transition ?? "freeze"} recorded.`;
    await loadOperations();
  });
}

function handleMerchant(form: HTMLFormElement) {
  void run(async () => {
    const values = formValues(form);
    const merchant = await api.onboardMerchant({
      legalName: values.legalName,
      displayName: values.displayName,
      settlementCurrency: values.settlementCurrency.toUpperCase(),
      settlementDelayDays: Number(values.settlementDelayDays),
      reason: values.reason
    });
    await api.activateMerchant(merchant.id, "Activate merchant after onboarding.");
    state.message = `Merchant ${merchant.displayName} onboarded.`;
  });
}

function handlePaymentOperation(form: HTMLFormElement, submitter?: HTMLElement) {
  void run(async () => {
    const values = formValues(form);
    const action = submitter?.getAttribute("value");
    if (action === "refund") {
      await api.refundPayment(values.paymentIntentId, values.reason, crypto.randomUUID());
    } else {
      await api.capturePayment(values.paymentIntentId, values.reason, crypto.randomUUID());
    }
    state.message = `Payment ${action ?? "capture"} recorded.`;
    await loadOperations();
  });
}

function handleSettlement(form: HTMLFormElement) {
  void run(async () => {
    const values = formValues(form);
    const batch = await api.createSettlement({
      merchantId: values.merchantId,
      currency: values.currency.toUpperCase(),
      reason: values.reason
    });
    state.message = `Settlement ${shortId(batch.id)} completed.`;
    await loadOperations();
  });
}

function handleReconciliation(form: HTMLFormElement) {
  void run(async () => {
    const values = formValues(form);
    await api.reconcileSettlement(values.settlementBatchId, {
      providerReference: values.providerReference,
      actualAmountMinor: Number(values.actualAmountMinor),
      actualCurrency: values.actualCurrency.toUpperCase(),
      reason: values.reason
    });
    state.message = "Reconciliation case recorded.";
    await loadOperations();
  });
}

async function loadOperations() {
  await run(async () => {
    state.operations = {
      customers: await api.customers(),
      wallets: await api.wallets(),
      payments: await api.paymentIntents(),
      reconciliation: await api.reconciliationCases(),
      audit: await api.auditEvents()
    };
    state.message = "Operations data refreshed.";
  });
}

async function run(work: () => Promise<void>) {
  try {
    await work();
  } catch (error) {
    state.message = readableError(error);
  } finally {
    render();
  }
}

function transferRequest(form: HTMLFormElement): TransferRequest {
  const values = formValues(form);
  return {
    sourceWalletId: values.sourceWalletId,
    destinationWalletId: values.destinationWalletId,
    amountMinor: Number(values.amountMinor),
    currency: values.currency.toUpperCase()
  };
}

function formValues(form: HTMLFormElement): Record<string, string> {
  return Object.fromEntries(new FormData(form).entries()) as Record<string, string>;
}

function requireWalletId(): string {
  const input = appRoot.querySelector<HTMLInputElement>("#wallet-id");
  const walletId = input?.value.trim() || state.walletId;
  if (!walletId) {
    throw new Error("Wallet ID is required.");
  }
  state.walletId = walletId;
  return walletId;
}

function readableError(error: unknown): string {
  if (isApiError(error)) {
    return `${error.code}: ${error.message}`;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "Request failed.";
}

function isApiError(error: unknown): error is ApiError {
  return typeof error === "object" && error !== null && "code" in error && "message" in error;
}

function emptyState(message: string) {
  return `<div class="empty">${escapeHtml(message)}</div>`;
}

function formatMinor(amountMinor: number, currency: string) {
  return `${amountMinor.toLocaleString()} ${currency}`;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "short",
    timeStyle: "short"
  }).format(new Date(value));
}

function shortId(value: string) {
  return value.length > 8 ? value.slice(0, 8) : value;
}

function escapeHtml(value: string) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function escapeAttribute(value: string) {
  return escapeHtml(value).replaceAll("'", "&#39;");
}
