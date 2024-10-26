package com.freeswitch.demoCall.entity;

import io.pkts.packet.sip.SipResponse;
import io.sipstack.netty.codec.sip.SipMessageEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UA {
    private SipMessageEvent sipEvent;
    private SipResponse toSipRespSDP;
    private String numOfOwner;
    private boolean isRecv200 = false;
    private boolean answer = false;

    private String caller;
    private String callee;

    private String callerName;

    private String jid;

    private String additionaldata;

    private boolean isIvr;

    public UA(SipMessageEvent sipEvent) {
        this.sipEvent = sipEvent;
    }
}
