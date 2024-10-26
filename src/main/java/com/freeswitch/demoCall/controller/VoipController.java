package com.freeswitch.demoCall.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class VoipController {

    @GetMapping("/user1001")
    public String user1001() {
        return "user1001";
    }

    @GetMapping("/user1002")
    public String user1002() {
        return "user1002";
    }
}
