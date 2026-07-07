package com.payledger.platform.operations.application;

import com.payledger.platform.customer.application.CustomerService;
import com.payledger.platform.customer.domain.Customer;
import com.payledger.platform.customer.domain.CustomerType;
import com.payledger.platform.customer.domain.KycStatus;
import com.payledger.platform.customer.infrastructure.CustomerRepository;
import com.payledger.platform.identity.domain.CustomerIdentity;
import com.payledger.platform.identity.infrastructure.CustomerIdentityRepository;
import com.payledger.platform.kyc.application.KycOperationsService;
import com.payledger.platform.ledger.application.LedgerPostingCommand;
import com.payledger.platform.ledger.application.LedgerService;
import com.payledger.platform.ledger.application.PostJournalEntryCommand;
import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.ledger.domain.LedgerAccountType;
import com.payledger.platform.ledger.domain.PostingDirection;
import com.payledger.platform.ledger.infrastructure.JournalEntryRepository;
import com.payledger.platform.ledger.infrastructure.LedgerAccountRepository;
import com.payledger.platform.merchant.application.MerchantDetails;
import com.payledger.platform.merchant.application.MerchantService;
import com.payledger.platform.merchant.application.OnboardMerchantCommand;
import com.payledger.platform.merchant.domain.Merchant;
import com.payledger.platform.merchant.domain.MerchantStatus;
import com.payledger.platform.merchant.infrastructure.MerchantRepository;
import com.payledger.platform.shared.error.BusinessRuleViolationException;
import com.payledger.platform.wallet.application.WalletService;
import com.payledger.platform.wallet.domain.Wallet;
import com.payledger.platform.wallet.infrastructure.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class OperationsDemoSeedService {

    private static final String CURRENCY = "TRY";
    private static final String RECIPIENT_EMAIL = "demo-recipient@payledger.local";
    private static final String MERCHANT_DISPLAY_NAME = "PayLedger Demo Store";
    private static final String PLATFORM_CASH_ACCOUNT = "DEMO_PLATFORM_CASH_TRY";
    private static final String SEED_REFERENCE_TYPE = "PAYLEDGER_DEMO_SEED";
    private static final long TOP_UP_AMOUNT_MINOR = 50_000;

    private final CustomerRepository customerRepository;
    private final CustomerIdentityRepository identityRepository;
    private final CustomerService customerService;
    private final KycOperationsService kycOperationsService;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final LedgerService ledgerService;
    private final MerchantRepository merchantRepository;
    private final MerchantService merchantService;

    public OperationsDemoSeedService(
            CustomerRepository customerRepository,
            CustomerIdentityRepository identityRepository,
            CustomerService customerService,
            KycOperationsService kycOperationsService,
            WalletRepository walletRepository,
            WalletService walletService,
            LedgerAccountRepository ledgerAccountRepository,
            JournalEntryRepository journalEntryRepository,
            LedgerService ledgerService,
            MerchantRepository merchantRepository,
            MerchantService merchantService
    ) {
        this.customerRepository = customerRepository;
        this.identityRepository = identityRepository;
        this.customerService = customerService;
        this.kycOperationsService = kycOperationsService;
        this.walletRepository = walletRepository;
        this.walletService = walletService;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.ledgerService = ledgerService;
        this.merchantRepository = merchantRepository;
        this.merchantService = merchantService;
    }

    @Transactional
    public DemoSeedResult seed(String actorExternalSubject) {
        Customer customer = primaryDemoCustomer();
        approveKycIfNeeded(customer, actorExternalSubject);
        Wallet customerWallet = ensureWallet(customer.getId());

        Customer recipient = ensureRecipientCustomer();
        approveKycIfNeeded(recipient, actorExternalSubject);
        Wallet recipientWallet = ensureWallet(recipient.getId());

        MerchantDetails merchant = ensureActiveMerchant(actorExternalSubject);
        boolean topUpCreated = topUpOnce(customerWallet);

        return new DemoSeedResult(
                customer.getId(),
                customerWallet.getId(),
                TOP_UP_AMOUNT_MINOR,
                recipient.getId(),
                recipientWallet.getId(),
                merchant.id(),
                CURRENCY,
                topUpCreated
        );
    }

    private Customer primaryDemoCustomer() {
        return identityRepository.findAll()
                .stream()
                .map(CustomerIdentity::getCustomerId)
                .distinct()
                .sorted()
                .findFirst()
                .flatMap(customerRepository::findById)
                .or(() -> singleExistingCustomer())
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "DEMO_CUSTOMER_REQUIRED",
                        "Create or link a customer before seeding demo data."
                ));
    }

    private java.util.Optional<Customer> singleExistingCustomer() {
        List<Customer> customers = customerRepository.findAll();
        if (customers.size() != 1) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(customers.getFirst());
    }

    private Customer ensureRecipientCustomer() {
        return customerRepository.findByEmailIgnoreCase(RECIPIENT_EMAIL)
                .orElseGet(() -> customerService.createCustomer(
                        CustomerType.PERSONAL,
                        "PayLedger Demo Recipient",
                        RECIPIENT_EMAIL
                ));
    }

    private void approveKycIfNeeded(
            Customer customer,
            String actorExternalSubject
    ) {
        if (customer.getKycStatus() == KycStatus.APPROVED) {
            return;
        }
        if (customer.getKycStatus() == KycStatus.NOT_STARTED
                || customer.getKycStatus() == KycStatus.REJECTED) {
            customer = kycOperationsService.submitForReview(
                    customer.getId(),
                    actorExternalSubject,
                    "Submit local demo KYC."
            );
        }
        if (customer.getKycStatus() == KycStatus.PENDING) {
            kycOperationsService.approve(
                    customer.getId(),
                    actorExternalSubject,
                    "Approve local demo KYC."
            );
        }
    }

    private Wallet ensureWallet(UUID customerId) {
        return walletRepository.findByCustomerIdAndCurrency(customerId, CURRENCY)
                .orElseGet(() -> walletService.createWallet(customerId, CURRENCY));
    }

    private MerchantDetails ensureActiveMerchant(String actorExternalSubject) {
        return merchantRepository.findByDisplayNameIgnoreCase(MERCHANT_DISPLAY_NAME)
                .map(merchant -> activateIfNeeded(merchant, actorExternalSubject))
                .orElseGet(() -> {
                    MerchantDetails merchant = merchantService.onboard(
                            new OnboardMerchantCommand(
                                    "PayLedger Demo Merchant LLC",
                                    MERCHANT_DISPLAY_NAME,
                                    CURRENCY,
                                    0,
                                    actorExternalSubject,
                                    "Onboard local demo merchant."
                            )
                    );
                    return merchantService.activate(
                            merchant.id(),
                            actorExternalSubject,
                            "Activate local demo merchant."
                    );
                });
    }

    private MerchantDetails activateIfNeeded(
            Merchant merchant,
            String actorExternalSubject
    ) {
        if (merchant.getStatus() == MerchantStatus.ACTIVE) {
            return merchantService.getActiveMerchant(merchant.getId());
        }
        return merchantService.activate(
                merchant.getId(),
                actorExternalSubject,
                "Activate local demo merchant."
        );
    }

    private boolean topUpOnce(Wallet wallet) {
        UUID referenceId = UUID.nameUUIDFromBytes(
                ("payledger-demo-seed:" + wallet.getId())
                        .getBytes(StandardCharsets.UTF_8)
        );

        if (journalEntryRepository.existsByReferenceTypeAndReferenceId(
                SEED_REFERENCE_TYPE,
                referenceId
        )) {
            return false;
        }

        LedgerAccount platformCash = ledgerAccountRepository
                .findByAccountCode(PLATFORM_CASH_ACCOUNT)
                .orElseGet(() -> ledgerAccountRepository.saveAndFlush(
                        LedgerAccount.createPlatformAccount(
                                PLATFORM_CASH_ACCOUNT,
                                LedgerAccountType.ASSET,
                                CURRENCY
                        )
                ));
        LedgerAccount walletAccount = ledgerAccountRepository
                .findByWalletId(wallet.getId())
                .orElseThrow();

        ledgerService.post(
                new PostJournalEntryCommand(
                        "WALLET_TOP_UP",
                        SEED_REFERENCE_TYPE,
                        referenceId,
                        CURRENCY,
                        "Fund local demo wallet.",
                        Instant.now(),
                        List.of(
                                new LedgerPostingCommand(
                                        platformCash.getId(),
                                        PostingDirection.DEBIT,
                                        TOP_UP_AMOUNT_MINOR
                                ),
                                new LedgerPostingCommand(
                                        walletAccount.getId(),
                                        PostingDirection.CREDIT,
                                        TOP_UP_AMOUNT_MINOR
                                )
                        )
                )
        );

        return true;
    }
}
