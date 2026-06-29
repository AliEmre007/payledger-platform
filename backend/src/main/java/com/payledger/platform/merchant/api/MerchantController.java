package com.payledger.platform.merchant.api;

import com.payledger.platform.merchant.application.MerchantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping("/{merchantId}")
    public PublicMerchantResponse getActiveMerchant(
            @PathVariable UUID merchantId
    ) {
        return PublicMerchantResponse.from(
                merchantService.getActiveMerchant(merchantId)
        );
    }
}
