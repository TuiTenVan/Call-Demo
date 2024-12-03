package com.freeswitch.demoCall.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CallCdrDto {

    private String callId;
    private String status;

    private Integer callStatusCode;

    private String callStatus;
    private Long timeStart;

    private Long setupDuration;

    private Long callDuration;

    private Long endTime;

    private String closedBy;

    private String linkRecord;
}
