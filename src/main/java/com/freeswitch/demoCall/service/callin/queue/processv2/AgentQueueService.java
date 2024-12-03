package com.freeswitch.demoCall.service.callin.queue.processv2;

import com.freeswitch.demoCall.mysql.entity.IvrQueue;
import com.freeswitch.demoCall.service.RedisService;
import com.freeswitch.demoCall.service.callin.EventSocketAPI;
import com.freeswitch.demoCall.service.callin.queue.AgentCall;
import com.freeswitch.demoCall.service.callin.queue.CallToQueueServiceV2;
import com.freeswitch.demoCall.service.callin.queue.RedisQueueService;
import com.freeswitch.demoCall.utils.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.freeswitch.esl.client.inbound.Client;
import org.freeswitch.esl.client.outbound.AbstractOutboundClientHandler;
import org.freeswitch.esl.client.transport.CommandResponse;
import org.freeswitch.esl.client.transport.SendMsg;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.freeswitch.esl.client.transport.message.EslMessage;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AgentQueueService {

    private static final Logger logger = LogManager.getLogger(AgentQueueService.class);

    @Value("${ringme.domain.xmpp}")
    private String domainXmpp;

    @Autowired
    private RedisService redisService;

    @Autowired
    private RedisQueueService redisQueueService;

    @Autowired
    private RedisQueueSessionService redisQueueSessionService;

//    @Autowired
//    private IVRDao ivrDao;

    @Autowired
    private CallToQueueServiceV2 callToQueueServiceV2;

    private static final String fsConferenceAudio = "ctx_conf_ringme";
    private static final String fsConferenceVideo = "ringmeconf";

//    public List<AgentCall> getListAgentCallForTransfer(String prefixLog, String callId) {
//
//        Map<String, Object> queueInfo = redisQueueSessionService.getIvrQueueByCallId(callId);
//        if (!queueInfo.isEmpty()) {
//
//            String queueId = String.valueOf(queueInfo.get("queueId"));
//
//            IvrQueue ivrQueue = callToQueueServiceV2.getIvrQueue(queueId);
//            List<AgentCall> list = redisQueueService.getListAgentForQueue(ivrQueue.getId());
//            if (list.isEmpty()) {
//                list = ivrDao.getListAgentFromIvrQueue(ivrQueue.getId());
//                logger.info(prefixLog + "|getListAgentCallForTransfer|MISS|ListDB={}", list.size());
//                redisQueueService.cacheListAgentForQueue(ivrQueue.getId(), list);
//            }
//            return getListAgentCall(prefixLog, list, null, ivrQueue, null, null);
//        }
//        return new ArrayList<>();
//    }

    public List<AgentCall> getListAgentCall(String prefixLog,
                                             List<AgentCall> list,
                                             String caller,
                                             IvrQueue ivrQueue,
                                             Client inboudClient,
                                             ChannelHandlerContext ctx) {

        list.forEach(item -> {
            if (checkAgentReadyCall(item.getUsername(), item.getPhoneNumber(), true, true)) {
                item.setOnlineApp(true);
            } else if (ivrQueue.getConditionRouting() == 2 && item.getUserSipPhone() != null) {
                EslMessage eslMessage = null;
                if (inboudClient != null) {
                    eslMessage = inboudClient.sendSyncApiCommand("sofia_contact",
                            "callout-external/" + item.getUserSipPhone());
                } else if (ctx != null) {

                    AbstractOutboundClientHandler handler = (AbstractOutboundClientHandler) ctx.getHandler();
                    eslMessage = handler.sendSyncSingleLineCommand(ctx.getChannel(),
                            "api sofia_contact " + "callout-external/" + item.getUserSipPhone());
                }
                if (eslMessage != null && !eslMessage.getBodyLines().isEmpty() &&
                        !eslMessage.getBodyLines().get(0).equals("error/user_not_registered")) {
                    logger.info(prefixLog + "handleCallQueue|agent online sip_phone|{}", item.getUserSipPhone());
                    item.setOnlineSipPhone(true); // TODO: check wrapup user sipphone
                } else {
                    logger.info(prefixLog + "handleCallQueue|agent offline sip_phone|{}", item.getUserSipPhone());
                }
            }
            item.setTimeHangup(redisQueueService.getTimeHangupAgent(item.getUsername()));

            if (item.isOnlineApp()) {
                String statusCall = redisQueueService.getStatusCallAgent(item.getUsername().split("@")[0]);
                if (statusCall != null) {
                    item.setStatusCall(Integer.valueOf(statusCall));
                }

                item.setStatusCall(1); // TODO: hardcode statusCall = 1
            }
        });

        if (ivrQueue.getTicketAgentNearest() == 1 && caller != null) {

            // nearest call queue
            String jidNearest = redisQueueService.getNearestAgent(caller);
            logger.info(prefixLog + "handleCallQueue|jidNearest: {}", jidNearest);
            // nearest callout
            String jidNearestCallout = null;
            Map<String, String> calloutNearest = redisService.getRecentCalleeForCallin(caller, false);
            if (calloutNearest != null) {
                jidNearestCallout = calloutNearest.get("jid");
                logger.info(prefixLog + "handleCallQueue|jidNearest callout: {}", jidNearestCallout);
            }

            for (AgentCall agentCall : list) {

                String usernameAgent = agentCall.getUsername().split("@")[0];
                if (usernameAgent.equals(jidNearest) || usernameAgent.equals(jidNearestCallout)) {
                    logger.info(prefixLog + "handleCallQueue|have jid nearest in list");
                    agentCall.setNearest(true);
                }
            }
        }

        logger.info(prefixLog + "handleCallQueue|list Agent before filter: {}", list);
        List<AgentCall> listFilter = list.stream()
                .filter(item -> ((item.isOnlineApp() && item.getStatusCall().equals(1)) || item.isOnlineSipPhone()))
                .sorted((o1, o2) -> {

                    // sort agent ít hỗ trợ nhất and nearest
                    if (o1.isNearest()) return 1;
                    if (o2.isNearest()) return -1;
                    if (o1.getTimeHangup() == o2.getTimeHangup()) return 0;
                    return o1.getTimeHangup() > o2.getTimeHangup() ? 1 : -1;
                }).collect(Collectors.toList());

        return listFilter;
    }

    public boolean checkAgentReadyCall(String username, String phoneNumber,
                                       boolean checkWrapup, boolean checkCalling) {

        try {

            final String uri = "http://192.168.22.175:7700/api/user_sessions_info?user=" +
                    username.replace("@" + domainXmpp, "") + "&host=" + domainXmpp;
            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<List<Map<String, String>>> responseEntity = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, String>>>() {
                    });

            if (responseEntity.getStatusCodeValue() == HttpStatus.OK.value()) {

                List<Map<String, String>> pojoObjList = responseEntity.getBody();
                if (pojoObjList != null && !pojoObjList.isEmpty()) {

                    boolean validWrapupTime = !checkWrapup || redisQueueService
                            .checkCacheWrapupAgent(username.replace("@" + domainXmpp, ""));

                    boolean validNotCalling = !checkCalling || !redisQueueService.getCacheAgentCalling(phoneNumber);

                    logger.info("checkAgentReadyCall|{}|{}|validWrapupTime={}|phoneNumber={}|validNotCalling={}",
                            uri, pojoObjList, validWrapupTime, phoneNumber, validNotCalling);
                    return pojoObjList.get(0).get("status").equals("available") && validWrapupTime && validNotCalling;
                }
            }
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
        return false;
    }

    public String getProfileConference(String callId) {
        return redisQueueSessionService.getCacheVideoCallBySessionId(callId) ? fsConferenceVideo : fsConferenceAudio;
    }
    public void setTicketToAgent(String caller, String username) {

        redisQueueSessionService.setJidTicketCall(caller, username);
    }

    public boolean checkCallExist(String callId, Integer queueId) {
        return redisQueueService.checkCallQueueExist(callId, queueId);
    }


    public void sendMsgCommand(Client inboudClient, EslEvent eslEvent,
                                String appName, String appArg) {
        SendMsg cmd = new SendMsg(eslEvent.getEventHeaders().get("Channel-Call-UUID"));
        cmd.addCallCommand("execute");
        cmd.addExecuteAppName(appName);
        if (appArg != null) {

            cmd.addExecuteAppArg(appArg);
        }
        cmd.addEventLock();
        CommandResponse commandResponse = inboudClient.sendMessage(cmd);
        logger.info("sendMsgCommand: {} = {} => {}", "execute" + " " + appName + " " + appArg,
                commandResponse.isOk(), commandResponse.getReplyText());
    }

    public void processVoiceMail(Client inboudClient, EslEvent eslEvent, IvrQueue ivrQueue, String callId) {
        boolean isVoicemail = ivrQueue.getEnableVoicemail() != null && ivrQueue.getEnableVoicemail() == 1;

        if (isVoicemail) {
            if (redisQueueSessionService.getCacheVideoCallBySessionId(callId)) {
                String fsRecordAudioPath = "/etc/freeswitch/call-record/${strftime(%Y/%m/%d)}/${sip_call_id}.wav";

                sendMsgCommand(inboudClient, eslEvent,
                        "set", "link_record=" + fsRecordAudioPath);
            }
            sendMsgCommand(inboudClient, eslEvent, "set", "record_sample_rate=8000");
//            sendMsgCommand(inboudClient, eslEvent, "set", "RECORD_STEREO=true");

            String fsRecordAudioPath = "/etc/freeswitch/call-record/${strftime(%Y/%m/%d)}/${sip_call_id}.wav";

//            String appArg = "execute_on_answer=record_session " + fsRecordAudioPath;
//            sendMsgCommand(inboudClient, eslEvent, "export", appArg);

            if (ivrQueue.getVoicemailMode() == 2 && ivrQueue.getVoicemailContent() != null) {

                String greetingSound = Utils.getLink(ivrQueue.getVoicemailContent(), true);
                sendMsgCommand(inboudClient, eslEvent, "playback", greetingSound);
            }

            sendMsgCommand(inboudClient, eslEvent, "record_session", fsRecordAudioPath);
            sendMsgCommand(inboudClient, eslEvent,"answer", null);

            // sleep -> bye -> hangup
            sendMsgCommand(inboudClient, eslEvent, "sleep", String.valueOf(ivrQueue.getVoicemailDuration() * 1000));

            if (ivrQueue.getVoicemailExitSoundMode() == 2 && ivrQueue.getVoicemailExitContent() != null) {
                String exitSound = Utils.getLink(ivrQueue.getVoicemailExitContent(), true);
                sendMsgCommand(inboudClient, eslEvent, "playback", exitSound);
            }
        } else {
            String agentBusyAudio = Utils.getLink("/version-dev/cms-sdk-upload/ivrFile/2024/7/23/tx1ho2cv9te9wj9ziprovzvf0etipri8.wav", true);
            sendMsgCommand(inboudClient, eslEvent, "playback", agentBusyAudio);
        }

        sendMsgCommand(inboudClient, eslEvent, "hangup", null);
    }

    public void processVoiceMailCtx(ChannelHandlerContext ctx, IvrQueue ivrQueue, String callId) {
        boolean isVoicemail = ivrQueue.getEnableVoicemail() != null && ivrQueue.getEnableVoicemail() == 1;

        if (isVoicemail) {
            if (redisQueueSessionService.getCacheVideoCallBySessionId(callId)) {
                String fsRecordAudioPath = "/etc/freeswitch/call-record/${strftime(%Y/%m/%d)}/${sip_call_id}.wav";

                EventSocketAPI.runCommand(ctx, "set",
                        "link_record=" + fsRecordAudioPath, true);
            }

            EventSocketAPI.runCommand(ctx, "set",
                    "record_sample_rate=8000", true);

//            EventSocketAPI.runCommand(ctx, "set",
//                    "RECORD_STEREO=true", true);

            String fsRecordAudioPath = "/etc/freeswitch/call-record/${strftime(%Y/%m/%d)}/${sip_call_id}.wav";

//            String appArg = "execute_on_answer=record_session " + fsRecordAudioPath;
//            EventSocketAPI.runCommand(ctx, "export",
//                    appArg, true);

            // sleep -> bye -> hangup
            if (ivrQueue.getVoicemailMode() == 2 && ivrQueue.getVoicemailContent() != null) {

                String greetingSound = Utils.getLink(ivrQueue.getVoicemailContent(), true);
                EventSocketAPI.runCommand(ctx, "playback",
                        greetingSound, true);
            }

            EventSocketAPI.runCommand(ctx, "record_session",
                    fsRecordAudioPath, true);

            EventSocketAPI.runCommand(ctx, "answer",
                    null, true);

            EventSocketAPI.runCommand(ctx, "sleep",
                    String.valueOf(ivrQueue.getVoicemailDuration() * 1000), true);

            if (ivrQueue.getVoicemailExitSoundMode() == 2 && ivrQueue.getVoicemailExitContent() != null) {
                String exitSound = Utils.getLink(ivrQueue.getVoicemailExitContent(), true);
                EventSocketAPI.runCommand(ctx, "playback",
                        exitSound, true);
            }

        } else {
            String agentBusyAudio = Utils.getLink("/version-dev/cms-sdk-upload/ivrFile/2024/7/23/tx1ho2cv9te9wj9ziprovzvf0etipri8.wav", true);
            EventSocketAPI.runCommand(ctx, "playback",
                    agentBusyAudio, true);
        }
        EventSocketAPI.hangupCall(ctx);
    }
}
