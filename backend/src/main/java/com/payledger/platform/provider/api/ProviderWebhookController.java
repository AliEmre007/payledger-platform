package com.payledger.platform.provider.api;

import com.payledger.platform.provider.application.ProviderWebhookResult;
import com.payledger.platform.provider.application.ProviderWebhookService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/provider/webhooks")
public class ProviderWebhookController {

    private final ProviderWebhookService webhookService;

    public ProviderWebhookController(
            ProviderWebhookService webhookService
    ) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public ProviderWebhookResult receive(
            @RequestBody String payload,
            @RequestHeader("X-PayLedger-Signature") String signature
    ) {
        return webhookService.receive(payload, signature);
    }
}
