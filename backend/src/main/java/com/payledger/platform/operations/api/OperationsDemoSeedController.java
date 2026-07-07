package com.payledger.platform.operations.api;

import com.payledger.platform.operations.application.CurrentOperationActorService;
import com.payledger.platform.operations.application.DemoSeedResult;
import com.payledger.platform.operations.application.OperationActor;
import com.payledger.platform.operations.application.OperationsDemoSeedService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations/demo-seed")
@PreAuthorize("hasAnyRole('OPERATIONS', 'ADMIN')")
public class OperationsDemoSeedController {

    private final OperationsDemoSeedService demoSeedService;
    private final CurrentOperationActorService currentActorService;

    public OperationsDemoSeedController(
            OperationsDemoSeedService demoSeedService,
            CurrentOperationActorService currentActorService
    ) {
        this.demoSeedService = demoSeedService;
        this.currentActorService = currentActorService;
    }

    @PostMapping
    public DemoSeedResponse seed() {
        OperationActor actor = currentActorService.getCurrentActor();
        DemoSeedResult result = demoSeedService.seed(actor.externalSubject());

        return DemoSeedResponse.from(result);
    }
}
