package com.freeswitch.demoCall.service.inbound;


import com.freeswitch.demoCall.service.outbound.queue.CallToQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.freeswitch.esl.client.IEslEventListener;
import org.freeswitch.esl.client.inbound.Client;
import org.freeswitch.esl.client.transport.CommandResponse;
import org.freeswitch.esl.client.transport.SendMsg;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.freeswitch.esl.client.transport.message.EslMessage;
import org.springframework.context.ApplicationContext;

import java.security.InvalidParameterException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ESLInboundHandler {

    private final Logger logger = LogManager.getLogger(ESLInboundHandler.class);
    private Client inboundClient;
    private String firstCallId = null;
    private CallToQueue callToQueue;

    public ESLInboundHandler(String ip, ApplicationContext context) {
        this.callToQueue = context.getBean(CallToQueue.class);
        try {
            inboundClient = new Client();
            startEslInboundListener(inboundClient);
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public boolean isConnected() {
        return inboundClient.canSend();
    }

    public void reconnect() {
        try {
            inboundClient = new Client();
            startEslInboundListener(inboundClient);
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public void disconnect() {
        CommandResponse commandResponse = inboundClient.close();
        logger.info(commandResponse);
    }

    private void startEslInboundListener(Client inboundClient) {
        try {
            logger.info("FREESWITCH ESL: {} {}", "localhost", 8021);
            inboundClient.connect("localhost", 8021, "ClueCon", 10);
            inboundClient.addEventFilter("Event-Name", "CHANNEL_HANGUP_COMPLETE");
            inboundClient.addEventFilter("Event-Name", "CHANNEL_ANSWER");
            inboundClient.addEventFilter("Event-Name", "DTMF");
            inboundClient.addEventListener(new IEslEventListener() {
                @Override
                public void eventReceived(EslEvent event) {
                    String eventName = event.getEventName();
//                    System.out.println(eventName);
                    switch (eventName) {
                        case "DTMF":
                            handleDtmfAction(event);
//                            printLog(event);
                            break;
                        case "CHANNEL_ANSWER":
                            handleChannelAnswer(event);
                            break;
                        case "CHANNEL_HANGUP_COMPLETE":
                            handleHangupComplete(event);
                            break;
                        default:
                            break;
                    }
                }
                @Override
                public void backgroundJobResultReceived(EslEvent eslEvent) {
                    logger.info("===============================backgroundJobResultReceived===============================");
//                    printLog(eslEvent);
                }
            });
            inboundClient.setEventSubscriptions("plain", "all");
        } catch (Exception t) {
            System.out.println(t.getMessage());
        }
    }

    private void handleDtmfAction(EslEvent eslEvent) {
        String digit = eslEvent.getEventHeaders().get("DTMF-Digit");
        String callId = eslEvent.getEventHeaders().get("Unique-ID");
        String caller = eslEvent.getEventHeaders().get("Caller-Orig-Caller-ID-Number");
        String callee = eslEvent.getEventHeaders().get("Caller-Destination-Number");
        logger.info("DTMF|caller={}|callee={}|callId={}|digit={}", caller, callee, callId, digit);
        if ("1".equals(digit)) {
            final String prefixLog = "CALL_IVR_1|" + callId + "|";
            callToQueue.handleBridgeQueueAction(inboundClient, eslEvent, prefixLog, callId, caller);
            logger.info("DTMF 1 pressed");
        } else {
            final String prefixLog = "CALL_IVR_2|" + callId + "|";
            callToQueue.handleBridgeToCall(inboundClient, eslEvent, prefixLog, caller);
            logger.info("DTMF 2 pressed");
        }
    }

    public void transferCall(String callId, String caller, String oldCallee, String newCallee, int type) {
        EslMessage list = inboundClient.sendSyncApiCommand( "conference",
                 " list");
        logger.info(list.getBodyLines());
        if (list.getBodyLines().size() == 1 && list.getBodyLines().get(0).equals("-ERR Conference " + callId + " not found")) {

            throw new InvalidParameterException("transferCall callId not true"); // TODO
        }
        if(type == 5){ // 5 = interrupt
            String command = "ringmeconf hup all";
            logger.info("transferCall|type={}|conference {}| caller {}| callee {}", type, command, caller, oldCallee);
            inboundClient.sendAsyncApiCommand( "conference", command);
        }
        else if (type == 1 || type == 2 || type == 3 || type == 4) { // 1 = transfer , 2 = rob, 3 = nghe len, 4 = join, 5 = hangup
            String confIdKick = "last";
            if ((type != 3 && type != 4) && !list.getBodyLines().isEmpty()) {
                for (String bodyLine : list.getBodyLines()) {
                    if (bodyLine.contains(oldCallee)) {
                        confIdKick = bodyLine.split(";")[0];
                        break;
                    }
                }
            }

            String fsGw = "{ringme_type_transfer=" + type + ",ringme_call_id=" + callId;
            if (type != 3 && type != 4) {
                fsGw = fsGw + ",api_on_answer='sched_api +1 none conference " + callId + " hup " + confIdKick + "'";
            } else if (type == 3){
                fsGw = fsGw + ",api_on_answer='conference " + callId + " mute last'";
            } else{
                fsGw = fsGw + ",api_on_answer='conference ringmeconf";
            }
            fsGw = fsGw + "}user/";

            String command =  "ringmeconf dial " +
                    fsGw  + newCallee + (type == 2 ? "_rob" : "");

            logger.info("transferCall|type={}|conference {}", type, command);
            inboundClient.sendAsyncApiCommand( "conference", command);
        } else { // transfer to hotline
            String uuidCaller = null;
            if (!list.getBodyLines().isEmpty()) {
                for (String bodyLine : list.getBodyLines()) {
                    if (bodyLine.contains(caller)) {
                        uuidCaller = bodyLine.split(";")[2];
                        break;
                    }
                }
            }
            logger.info("transferCall|type={}| uuid_transfer {} {}", type, uuidCaller, newCallee);
            inboundClient.sendAsyncApiCommand( "uuid_transfer",
                    uuidCaller + " " + newCallee);
        }
    }

    public void musicOnHold(String callId, String caller) {
        EslMessage list = inboundClient.sendSyncApiCommand("conference", callId + " list");
        logger.info(String.join("\n", list.getBodyLines()));
        if (list.getBodyLines().size() == 1 && list.getBodyLines().get(0).equals("-ERR Conference " + callId + " not found")) {
            throw new IllegalArgumentException("transferCall callId not true");
        }
        else{ // conference
            String uuidCaller = null;
            if (!list.getBodyLines().isEmpty()) {
                for (String bodyLine : list.getBodyLines()) {
                    if (bodyLine.contains(caller)) {
                        uuidCaller = bodyLine.split(";")[2];
                        break;
                    }
                }
            }
            String holdFlag = inboundClient.sendSyncApiCommand("uuid_getvar", uuidCaller + " hold_flag").getBodyLines().get(0);
            logger.info("Hold flag: {}", holdFlag);
            if (!holdFlag.equals("true")) {
                inboundClient.sendSyncApiCommand("uuid_displace", uuidCaller + " start /etc/freeswitch/call-record/sounds/holdcall.wav 0 loop");
                logger.info("Start music on hold | callId: {} | caller: {}", callId, caller);
                inboundClient.sendSyncApiCommand("uuid_setvar", uuidCaller + " hold_flag true");
            } else {
                inboundClient.sendSyncApiCommand("uuid_displace", uuidCaller + " stop /etc/freeswitch/call-record/sounds/holdcall.wav");
                logger.info("Stop music on hold | callId: {} | caller: {}", callId, caller);
                inboundClient.sendSyncApiCommand("uuid_setvar", uuidCaller + " hold_flag false");
            }
        }
    }

//    public void musicOnHold(String callId, String caller) {
//        EslMessage list = inboundClient.sendSyncApiCommand("conference", callId + "list");
//        if (list.getBodyLines().size() == 1 && list.getBodyLines().get(0).equals("-ERR Conference " + callId + " not found")) {
//            throw new InvalidParameterException("transferCall callId not true");
//        }
//        EslMessage listUuid = inboundClient.sendSyncApiCommand("show", "channels like" + callId);
//        if (!listUuid.getBodyLines().isEmpty()) {
//            String uuidCaller = listUuid.getBodyLines().get(1).split(",")[0];
//            String holdFlag = inboundClient.sendSyncApiCommand("uuid_getvar", uuidCaller + " hold_flag").getBodyLines().get(0);
//            logger.info("Hold flag: {}", holdFlag);
//            if (!holdFlag.equals("true")) {
//                inboundClient.sendSyncApiCommand("uuid_displace", uuidCaller + " start /etc/freeswitch/call-record/sounds/holdcall.wav 0 loop");
//                logger.info("Start music on hold | callId: {} | caller: {}", callId, caller);
//                inboundClient.sendSyncApiCommand("uuid_setvar", uuidCaller + " hold_flag true");
//            } else {
//                inboundClient.sendSyncApiCommand("uuid_displace", uuidCaller + " stop /etc/freeswitch/call-record/sounds/holdcall.wav");
//                logger.info("Stop music on hold | callId: {} | caller: {}", callId, caller);
//                inboundClient.sendSyncApiCommand("uuid_setvar", uuidCaller + " hold_flag false");
//            }
//        } else {
//            throw new InvalidParameterException("No active channels found");
//        }
//    }


    private long getSetUpDurationForAnswer(Map<String, String> eventHeaders) {
        String timeAnswer = eventHeaders.get("Caller-Channel-Answered-Time");
        long timeInviteStamp = 0;
        if (eventHeaders.get("variable_sip_invite_stamp") != null) {
            timeInviteStamp = Long.parseLong(eventHeaders.get("variable_sip_invite_stamp"));
        } else if (eventHeaders.get("Caller-Channel-Created-Time") != null) {
            timeInviteStamp = Long.parseLong(eventHeaders.get("Caller-Channel-Created-Time"));
        }
        long timeAnswerStamp = Long.parseLong(timeAnswer);
        double d = (timeAnswerStamp - timeInviteStamp) / 1000000d;
        long setupDuration = (long) Math.ceil(d);
        logger.info("TIME_INVITE={}|TIME_ANSWER={}|SETUP_DURATION_MILLISEC={}", timeInviteStamp, timeAnswerStamp, d);
        return setupDuration;
    }
    private void handleChannelAnswer(EslEvent eslEvent) {
        Map<String, String> eventHeaders = eslEvent.getEventHeaders();
        String callId = eventHeaders.get("variable_sip_call_id");
        if (firstCallId == null) {
            firstCallId = callId;
        }

        String typeCall = eventHeaders.get("Caller-Context");


        logger.info("TYPE_CALL={}|CALL_ID={}", typeCall, callId);
        if (typeCall.equals("company-a") || typeCall.equals("default")) {
            String timeAnswer = eventHeaders.get("Caller-Channel-Answered-Time");
            logger.info("TIME-ANSWER={}", timeAnswer);
            long setupDuration = 0L;
            if (eventHeaders.get("variable_sip_invite_stamp") != null || eventHeaders.get("Caller-Channel-Created-Time") != null) {
                setupDuration = getSetUpDurationForAnswer(eventHeaders);
                logger.info("DURATION={}", setupDuration);
            } else {
                logger.info("====================> {} --- {}", eventHeaders.get("variable_start_epoch"),
                        eventHeaders.get("variable_answer_epoch"));
            }

        }
    }
    private void handleHangupComplete(EslEvent eslEvent) {
        Map<String, String> map = getCDRFromESLEvent(eslEvent);
        try{
            logger.info("Go to handle hangup event|state:{}|type_call:{}|caller-for-callout:{}|callee-for-callin:{}|callId:{}",
                    map.get("state"),
                    map.get("type_call"),
                    eslEvent.getEventHeaders().get("variable_sip_from_user"),
                    eslEvent.getEventHeaders().get("variable_sip_to_user"),
                    map.get("call_id"));
        }
        catch(Exception ex){
            logger.error(ex.getMessage(), ex);
        }
    }
    private void sendMsgCommand(String uuid, String callCommand,
                                String appName, String appArg, boolean isEventLog) {
        SendMsg cmd = new SendMsg(uuid);
        cmd.addCallCommand(callCommand);
        cmd.addExecuteAppName(appName);
        if (appArg != null) {

            cmd.addExecuteAppArg(appArg);
        }
        if (isEventLog || appName.equals("bridge")) {
            // not async
            cmd.addEventLock();
        }
        CommandResponse commandResponse = inboundClient.sendMessage(cmd);
        logger.info("sendMsgCommand: {} = {} => {}", callCommand + " " + appName + " " + appArg,
                commandResponse.isOk(), commandResponse.getReplyText());
    }

    private Map<String, String> getCDRFromESLEvent(EslEvent eslEvent) {
        Map<String, String> eventHeaders = eslEvent.getEventHeaders();
        Map<String, String> map = new HashMap<>();
        if (eventHeaders != null) {
            map.put("type_call", eventHeaders.get("Caller-Context"));
            map.put("call_id", eventHeaders.get("variable_sip_call_id")); //ringme=sip
            map.put("state", eventHeaders.get("Channel-Call-State"));

            map.put("sip_invite_failure_status", eventHeaders.get("variable_sip_invite_failure_status"));
            map.put("originate_failed_cause", eventHeaders.get("variable_originate_failed_cause"));

            map.put("answer-state", eventHeaders.get("Answer-State")); // hangup, answered
            map.put("hangup_cause", eventHeaders.get("variable_hangup_cause"));

            map.put("sip_hangup_phrase", eventHeaders.get("variable_sip_hangup_phrase"));
            map.put("status_code", eventHeaders.get("variable_sip_term_status"));

            long duration = (long) Math.ceil(Long.parseLong(eventHeaders.get("variable_billmsec")) / 1000d);
            map.put("duration", String.valueOf(duration));
            map.put("mduration", eventHeaders.get("variable_mobilise"));
            map.put("total-duration", eventHeaders.get("variable_duration"));
            map.put("wait-duration", eventHeaders.get("variable_duration"));
            map.put("time_invite", convertTimestampToStringDate(Long
                    .parseLong(eventHeaders.get("variable_start_epoch")) * 1000));
            map.put("time_answer", convertTimestampToStringDate(Long
                    .parseLong(eventHeaders.get("variable_answer_epoch")) * 1000));
            map.put("time_end", convertTimestampToStringDate(Long
                    .parseLong(eventHeaders.get("variable_end_epoch")) * 1000));

            if (map.get("type_call").equals("company-a")) {

                if (eventHeaders.get("variable_ringme_origin_caller") != null) {
                    map.put("caller", eventHeaders.get("variable_ringme_origin_caller"));
                } else {
                    map.put("caller", eventHeaders.get("Caller-Caller-ID-Number"));
                }
                map.put("callee", eventHeaders.get("Caller-Callee-ID-Number"));
                if (map.get("callee") == null) {
                    String[] arr = eventHeaders.get("Caller-Destination-Number")
                            .replace("no_record_", "")
                            .replace("record_", "")
                            .split("_");
                    if (arr.length > 0) {
                        map.put("callee", arr[0]);
                    }
                }
                map.put("transfer_to", eventHeaders.get("variable_sip_req_user"));
                map.put("transfer_from", eventHeaders.get("Caller-Caller-ID-Number"));

                if (eventHeaders.get("variable_sip_req_user") != null) {
                    map.put("sip_req_user", eventHeaders.get("variable_sip_req_user"));
                }
                if (eventHeaders.get("variable_sip_req_host") != null) {
                    map.put("sip_req_host", eventHeaders.get("variable_sip_req_host"));
                }
                if (eventHeaders.get("variable_sip_req_port") != null) {
                    map.put("sip_req_port", eventHeaders.get("variable_sip_req_port"));
                }
                if (eventHeaders.get("variable_sip_req_uri") != null) {
                    map.put("sip_req_uri", eventHeaders.get("variable_sip_req_uri"));
                }
            }
        }
        return map;
    }
    void printLog(EslEvent event) {
        logger.info("Received connect response [{}]", event.getEventName());
        Map<String, String> eventHeaders = event.getEventHeaders();

        logger.info("=======================  eventHeaders  =============================");
        if (eventHeaders != null) {
            for (String key : eventHeaders.keySet()) {
                logger.info("{}: {}", key, eventHeaders.get(key));
            }
        }
    }
    private String convertTimestampToStringDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date(timestamp));
    }
}
