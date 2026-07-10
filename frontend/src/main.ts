import {
  createIcons,
  Activity,
  ArrowRightLeft,
  Banknote,
  Building2,
  CreditCard,
  Landmark,
  LayoutDashboard,
  LogIn,
  LogOut,
  RefreshCw,
  ShieldCheck,
  WalletCards
} from "lucide";
import { ApiClient } from "./api";
import { KeycloakAuthClient, hasOperationsRole } from "./auth";
import { clearTransferDraft, getOrCreateTransferDraft } from "./idempotency";
import { formatMinor, parseMajorToMinor } from "./money";
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
  Wallet,
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
  wallets: Wallet[];
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
  walletId: "",
  wallets: []
};

void boot();

async function boot() {
  try {
    state.session = await auth.init();
    if (state.session.authenticated) {
      await refreshCustomerWallets(false);
    }
  } catch (error) {
    state.message = readableError(error);
  }

  render();
}

function render() {
  appRoot.innerHTML = `
    <div class="shell ${state.session.authenticated ? "workspace-shell" : "signed-out-shell"}">
      <header class="topbar">
        <div>
          <p class="eyebrow">Digital wallet</p>
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
    icons: {
      Activity,
      ArrowRightLeft,
      Banknote,
      Building2,
      CreditCard,
      Landmark,
      LayoutDashboard,
      LogIn,
      LogOut,
      RefreshCw,
      ShieldCheck,
      WalletCards
    }
  });
}

function renderIdentity() {
  if (!state.session.authenticated) {
    return "";
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
      <section class="login-hero" aria-labelledby="login-title">
        <div class="login-copy">
          <p class="eyebrow">Wallet operations workspace</p>
          <h2 id="login-title">Manage simulated payments with confidence.</h2>
          <p>Review wallets, authorize payments, monitor settlements, and inspect operational activity from one focused workspace.</p>
          <button class="button primary login-cta" data-action="login">
            <i data-lucide="log-in"></i><span>Log in</span>
          </button>
        </div>
        <div class="wallet-visual" aria-hidden="true">
          <div class="visual-toolbar">
            <span></span>
            <span></span>
            <span></span>
          </div>
          <div class="visual-grid">
            <div class="wallet-card primary-card">
              <span>Available balance</span>
              <strong>$12,480.00</strong>
              <small>USD wallet</small>
            </div>
            <div class="wallet-card secondary-card">
              <span>Pending settlement</span>
              <strong>$3,214.72</strong>
              <small>Merchant batch</small>
            </div>
            <div class="wallet-card accent-card">
              <span>Risk status</span>
              <strong>Clear</strong>
              <small>Last check 2 min ago</small>
            </div>
            <div class="mini-ledger">
              <div><span></span><strong></strong></div>
              <div><span></span><strong></strong></div>
              <div><span></span><strong></strong></div>
              <div><span></span><strong></strong></div>
            </div>
          </div>
          <div class="activity-strip">
            <span></span>
            <span></span>
            <span></span>
          </div>
        </div>
      </section>
    </main>
  `;
}

function renderWorkspace() {
  const navigation = navigationFor(state.session)
    .map(
      (item) => `
        <button
          class="side-nav-item ${state.activeView === item.id ? "active" : ""}"
          data-view="${item.id}"
        >
          <i data-lucide="${item.id === "operations" ? "shield-check" : "layout-dashboard"}"></i>
          <span>${item.label}</span>
        </button>
      `
    )
    .join("");

  return `
    <div class="app-frame">
      <aside class="sidebar">
        <div class="sidebar-brand">
          <span class="brand-mark">PL</span>
          <div>
            <strong>PayLedger</strong>
            <small>Payments console</small>
          </div>
        </div>
        <nav class="side-nav" aria-label="Workspace">${navigation}</nav>
        <div class="sidebar-status">
          <span>Simulation mode</span>
            <strong>Demo funds only</strong>
        </div>
      </aside>
      <div class="work-surface">
        <div class="workspace-header">
          <div>
            <p class="eyebrow">${state.activeView === "operations" ? "Operations workspace" : "Customer dashboard"}</p>
            <h2>${state.activeView === "operations" ? "Operational control center" : "Wallet overview"}</h2>
          </div>
          <div class="header-actions">
            <button class="button subtle" data-action="refresh-wallet" type="button">
              <i data-lucide="refresh-cw"></i><span>Refresh</span>
            </button>
          </div>
        </div>
        ${state.message ? `<div class="notice">${escapeHtml(state.message)}</div>` : ""}
        <main class="workspace">
          ${state.activeView === "operations" ? renderOperationsView() : renderCustomerView()}
        </main>
      </div>
    </div>
  `;
}

function renderCustomerView() {
  const selectedWallet = currentWallet();

  return `
    <section class="summary-strip" aria-label="Wallet summary">
      ${renderSummaryMetric("Available", state.balance ? formatMinor(state.balance.availableBalanceMinor, state.balance.currency) : "No wallet", "Ready to use")}
      ${renderSummaryMetric("Total balance", state.balance ? formatMinor(state.balance.ledgerBalanceMinor, state.balance.currency) : "-", "Posted balance")}
      ${renderSummaryMetric("Pending", state.balance ? formatMinor(state.balance.heldAmountMinor, state.balance.currency) : "-", "Reserved payments")}
      ${renderSummaryMetric("Wallet status", state.balance?.status ?? selectedWallet?.status ?? "-", selectedWallet ? accountReference(selectedWallet.id) : "No wallet selected")}
    </section>

    <section class="customer-dashboard">
      <aside class="account-panel panel">
        <div class="panel-header">
          <div>
            <p class="section-label">Accounts</p>
            <h2>My wallets</h2>
          </div>
          <button class="icon-button" data-action="refresh-wallet" title="Refresh wallet">
            <i data-lucide="refresh-cw"></i>
          </button>
        </div>
        ${renderWalletCards()}
        ${state.balance ? renderBalance(state.balance) : emptyState("Load an owned wallet balance.")}
      </aside>

      <section class="main-ledger panel">
        <div class="panel-header">
          <div>
            <p class="section-label">Activity</p>
            <h2>Recent transactions</h2>
          </div>
          <button class="icon-button" data-action="refresh-statement" title="Refresh statement">
            <i data-lucide="activity"></i>
          </button>
        </div>
        ${state.statement ? renderStatement(state.statement) : emptyState("Statement rows appear after ledger postings.")}
      </section>

      <aside class="detail-panel">
        <div class="panel selected-wallet-panel">
          <p class="section-label">Selected wallet</p>
          <div class="selected-wallet-title">
            <i data-lucide="wallet-cards"></i>
            <div>
              <h2>${selectedWallet ? `${escapeHtml(selectedWallet.currency)} wallet` : "No wallet"}</h2>
              <span>${selectedWallet ? escapeHtml(selectedWallet.status) : "Unavailable"}</span>
            </div>
          </div>
          <dl class="wallet-meta">
            <div><dt>Account ref</dt><dd>${selectedWallet ? escapeHtml(accountReference(selectedWallet.id)) : "-"}</dd></div>
            <div><dt>Currency</dt><dd>${selectedWallet ? escapeHtml(selectedWallet.currency) : "-"}</dd></div>
            <div><dt>Profile</dt><dd>${escapeHtml(roleLabel(state.session.roles))}</dd></div>
          </dl>
        </div>

        <form class="panel action-panel" id="transfer-form">
        <div class="action-title">
          <i data-lucide="arrow-right-left"></i>
          <h2>Transfer</h2>
        </div>
        ${renderWalletSelect("sourceWalletId", "From wallet")}
        <label>Recipient account <input name="destinationWalletId" placeholder="Recipient account reference" required /></label>
        <div class="inline-fields">
          <label>Amount <input name="amountMajor" inputmode="decimal" placeholder="125.00" required /></label>
          <label>Currency <input name="currency" maxlength="3" value="${escapeAttribute(state.balance?.currency ?? "TRY")}" required /></label>
        </div>
        <button class="button" type="submit">
          <i data-lucide="arrow-right-left"></i><span>Send transfer</span>
        </button>
        ${state.lastTransfer ? `<p class="result">${escapeHtml(state.lastTransfer)}</p>` : ""}
      </form>

      <form class="panel action-panel" id="payment-form">
        <div class="action-title">
          <i data-lucide="credit-card"></i>
          <h2>Pay merchant</h2>
        </div>
        ${renderWalletSelect("sourceWalletId", "From wallet")}
        <label>Merchant reference <input name="merchantId" placeholder="Merchant account reference" required /></label>
        <div class="inline-fields">
          <label>Amount <input name="amountMajor" inputmode="decimal" placeholder="125.00" required /></label>
          <label>Currency <input name="currency" maxlength="3" value="${escapeAttribute(state.balance?.currency ?? "TRY")}" required /></label>
        </div>
        <button class="button" type="submit">
          <i data-lucide="banknote"></i><span>Pay merchant</span>
        </button>
        ${renderPaymentResult()}
      </form>
      </aside>
    </section>
  `;
}

function renderSummaryMetric(label: string, value: string, detail: string) {
  return `
    <div class="metric-card">
      <span>${escapeHtml(label)}</span>
      <strong>${escapeHtml(value)}</strong>
      <small>${escapeHtml(detail)}</small>
    </div>
  `;
}

function currentWallet() {
  return state.wallets.find((wallet) => wallet.id === state.walletId);
}

function renderWalletCards() {
  if (state.wallets.length === 0) {
    return emptyState("No wallets are linked to this customer yet.");
  }

  return `
    <div class="wallet-list" aria-label="My wallets">
      ${state.wallets
        .map(
          (wallet) => `
            <button
              class="wallet-option ${state.walletId === wallet.id ? "active" : ""}"
              type="button"
              data-wallet-id="${escapeAttribute(wallet.id)}"
            >
              <span>${escapeHtml(wallet.currency)} wallet</span>
              <strong>${escapeHtml(wallet.status)}</strong>
              <small>${escapeHtml(accountReference(wallet.id))}</small>
            </button>
          `
        )
        .join("")}
    </div>
  `;
}

function renderWalletSelect(name: string, label: string) {
  if (state.wallets.length === 0) {
    return `<label>${escapeHtml(label)} <select name="${escapeAttribute(name)}" required disabled><option>No wallet available</option></select></label>`;
  }

  return `
    <label>${escapeHtml(label)}
      <select name="${escapeAttribute(name)}" required>
        ${state.wallets
          .map(
            (wallet) => `
              <option value="${escapeAttribute(wallet.id)}" ${state.walletId === wallet.id ? "selected" : ""}>
                ${escapeHtml(wallet.currency)} wallet - ${escapeHtml(wallet.status)}
              </option>
            `
          )
          .join("")}
      </select>
    </label>
  `;
}

function renderBalance(balance: WalletBalance) {
  return `
    <div class="balance-grid">
      <div><span>Total</span><strong>${formatMinor(balance.ledgerBalanceMinor, balance.currency)}</strong></div>
      <div><span>Pending</span><strong>${formatMinor(balance.heldAmountMinor, balance.currency)}</strong></div>
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
        <thead><tr><th>Date</th><th>Description</th><th>Status</th><th>Amount</th></tr></thead>
        <tbody>
          ${statement.entries
            .map(
              (entry) => `
                <tr>
                  <td>${formatDate(entry.occurredAt)}</td>
                  <td>${escapeHtml(transactionLabel(entry.journalType, entry.direction))}</td>
                  <td>${escapeHtml(transactionStatus(entry.direction))}</td>
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
      <strong>${escapeHtml(paymentStatusLabel(payment.status))}</strong>
      <span>${escapeHtml(accountReference(payment.id))}</span>
      ${
        payment.status === "AUTHORIZED"
          ? `<button class="button subtle" type="button" data-action="cancel-payment" data-id="${escapeAttribute(payment.id)}">Cancel payment</button>`
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

  appRoot.querySelectorAll<HTMLButtonElement>("[data-wallet-id]").forEach((button) => {
    button.addEventListener("click", () => {
      const walletId = button.dataset.walletId;
      if (walletId) {
        void selectWallet(walletId);
      }
    });
  });

  appRoot.querySelector("[data-action='refresh-wallet']")?.addEventListener("click", () => {
    void refreshCustomerWallets(true);
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

async function refreshCustomerWallets(renderAfter: boolean) {
  const wallets = await api.myWallets();
  state.wallets = wallets;

  if (wallets.length === 0) {
    state.walletId = "";
    state.balance = undefined;
    state.statement = undefined;
    state.message = "No wallets are linked to this customer yet.";
  } else {
    const selected = wallets.some((wallet) => wallet.id === state.walletId)
      ? state.walletId
      : wallets[0].id;
    await loadWalletSnapshot(selected);
    state.message = `Loaded ${wallets.length} wallet${wallets.length === 1 ? "" : "s"}.`;
  }

  if (renderAfter) {
    render();
  }
}

async function selectWallet(walletId: string) {
  await run(async () => {
    await loadWalletSnapshot(walletId);
    state.message = `Selected wallet ${shortId(walletId)}.`;
  });
}

async function loadWalletSnapshot(walletId: string) {
  state.walletId = walletId;
  state.balance = await api.walletBalance(walletId);
  state.statement = await api.walletStatement(walletId, 0, 20);
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
        amountMinor: parseMajorToMinor(values.amountMajor),
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
    amountMinor: parseMajorToMinor(values.amountMajor),
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

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "short",
    timeStyle: "short"
  }).format(new Date(value));
}

function transactionLabel(journalType: string, direction: string) {
  const directionLabel = direction === "DEBIT" ? "sent" : "received";
  const labels: Record<string, string> = {
    INTERNAL_TRANSFER: `Transfer ${directionLabel}`,
    WALLET_TOP_UP: "Wallet top-up",
    PAYMENT_AUTHORIZATION: "Merchant payment pending",
    PAYMENT_CAPTURE: "Merchant payment",
    PAYMENT_REFUND: "Refund received",
    PAYMENT_CANCEL: "Payment canceled",
    SETTLEMENT: "Merchant settlement"
  };

  return labels[journalType] ?? humanizeEnum(journalType);
}

function transactionStatus(direction: string) {
  return direction === "DEBIT" ? "Money out" : "Money in";
}

function paymentStatusLabel(status: string) {
  const labels: Record<string, string> = {
    AUTHORIZED: "Payment pending",
    CAPTURED: "Payment completed",
    CANCELED: "Payment canceled",
    REFUNDED: "Payment refunded"
  };

  return labels[status] ?? humanizeEnum(status);
}

function humanizeEnum(value: string) {
  return value
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function accountReference(value: string) {
  return value.length > 4 ? `•••• ${value.slice(-4)}` : value;
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
