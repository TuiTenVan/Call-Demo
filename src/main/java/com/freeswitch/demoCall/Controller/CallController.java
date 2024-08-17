package com.freeswitch.demoCall.Controller;

import com.freeswitch.demoCall.Service.Inbound.ESLInboundManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CallController {
    private final ESLInboundManager eslInboundManager;

    @PostMapping("/transfer")
    public String kickAndAddToConference(@RequestParam String callee, @RequestParam String calleeTransfer) {
        eslInboundManager.transfer(callee, calleeTransfer);
        return "Kick and add request sent";
    }
}
