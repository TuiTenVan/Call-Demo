package com.freeswitch.demoCall.mysql.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IvrQueue {

    private Integer id;

    private String name;

    private Integer ownerId;

    private Integer enableRecord;

    private Integer wrapupTimeLimit;

    private Integer ticketMissCall;

    private Integer autoTicketMissCall;

    private Integer autoTicketAgentNotReady;

    private Integer ticketAgentNearest;

    private Integer schedule;

    private Integer scheduleWorkingTime;

    private Integer waitAnswerTimeout;

    private String ringtone;

    private Integer maxQueueSize;

    private Integer timeout;

    private Long notifyAgentNoAnswerTimeout;

    private Long notifyWaitTimeout;

    private Long notifyAnswerTimeout;

    private Integer routeBackupGroupAfter;

    private Integer enableVoicemail;

    private Integer voicemailMode;

    private String voicemailContent;

    private Integer voicemailDuration;

    private Integer voicemailExitSoundMode;

    private String voicemailExitContent;

    private String calloutToAgent;

    private Integer conditionRouting;

    private Integer enableSpeechToText;
}
