package com.freeswitch.demoCall.service.outbound.queue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AgentCall {

    private String username;

    private String phoneNumber;

    private String staffName;

    private boolean isInit;

    private long timeHangup;

    private boolean isNearest;

    private boolean isOnlineApp;

    private String userSipPhone;

    private boolean isOnlineSipPhone;

    private Integer priorityInQueue;

    private Integer typeGroupInQueue;

    private Integer statusCall;
}
