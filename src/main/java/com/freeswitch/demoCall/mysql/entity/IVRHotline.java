package com.freeswitch.demoCall.mysql.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IVRHotline {

    private String hotline;

    private String name;

    private Integer enableIvr;

    private Integer ivrTreeId;

    private Integer queueId;

    private IVRMenu ivrMenu;

    private Integer callPartnerId;

    private String telecom;

    private Integer ownerId;

    private Integer enableRecord;

    private Integer enableSpeechToText;
}
