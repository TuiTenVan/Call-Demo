package com.freeswitch.demoCall.sip;

import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserSession {

    private String from;

    private String to;

    private String caller;

    private String callee;

    private String calleeName;

    private String sessionId;

    private boolean isSendSdp;

    private SIPRequest requestInvite;

    private SIPResponse response;

    private String sipWebsocketId;

    private long timeRemain;

    private boolean isIvr;
}
