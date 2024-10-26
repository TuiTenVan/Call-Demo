package com.freeswitch.demoCall.mysql.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CallCDR {

    private String sessionId;
    private String callerUsername;

    private String calleeUsername;

    private String callerPhoneNumber;

    private String calleePhoneNumber;

    private String callerPosition;

    private String calleePosition;

    private String callerType;

    private String calleeType;

    private String callerAppId;

    private String calleeAppId;

    private String callerIdProvince;

    private String calleeIdProvince;

    private String callerIdDepartment;

    private String calleeIdDepartment;

    private String callerVersionApp;

    private String calleeVersionApp;

    private String callType;

    private String hotline;

    private String mnpFrom;

    private String mnpTo;

    private String networkType;

    private String callStatus;

    private Integer callStatusCode;

    private Long setupDuration;

    private Long callDuration;

    private String closeBy;

    private Date acceptTime;

    private Date endTime;

    private Date createdAt;

    private String recordFile;

    private String orderId;

    private String additionalData;

    private Integer ownerId; // TODO: chua co ben vnpost
}
