package com.freeswitch.demoCall.controller;

import com.freeswitch.demoCall.service.inbound.ESLInboundManager;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CallController {
    private static final Logger logger = LogManager.getLogger(CallController.class);
    private final ESLInboundManager eslInboundManager;

    @PostMapping("/transfer")
    public ResponseEntity<?> transferCall(@RequestParam("callId") String callId,
                                          @RequestParam("caller") String caller,
                                          @RequestParam("oldCallee") String oldCallee,
                                          @RequestParam("newCallee") String newCallee,
                                          @RequestParam("type") int type) {
        logger.info("transferCall: callId={}|caller={}|{}|{}", callId, caller, oldCallee, newCallee);
        eslInboundManager.transferCall(callId, caller, oldCallee, newCallee, type);
        return ResponseEntity.ok().body("success");
    }

    @PostMapping("/music-on-hold")
    public ResponseEntity<?> musicOnHold(@RequestParam("callId") String callId,
                                          @RequestParam("caller") String caller) {
        logger.info("Music on Hold: callId={}|caller={}", callId, caller);
        eslInboundManager.musicOnHold(callId, caller);
        return ResponseEntity.ok().body("success");
    }
}
