package com.freeswitch.demoCall.mysql.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IVREntry {

    private Long id;

    private String action;

    private String digits;

    private String param;

    private String greetingSound;

    private Integer digitTimeout;

    private IvrQueue ivrQueue;
}
