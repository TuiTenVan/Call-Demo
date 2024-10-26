package com.freeswitch.demoCall.mysql.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IVRMenu {

    private Integer id;
    private Integer mode;
    private String content;
    private String linkFile;
    private Integer delay;
    private Integer digitTimeout;
    private Integer maxFailures;

    private Integer quickDtmf;
    private Integer allowKeypressRoute;
    private Integer extensionLength;

    private List<IVREntry> ivrEntries;
}
