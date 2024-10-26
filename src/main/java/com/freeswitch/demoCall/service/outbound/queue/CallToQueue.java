package com.freeswitch.demoCall.service.outbound.queue;

import com.freeswitch.demoCall.service.outbound.EventSocketAPI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.freeswitch.esl.client.inbound.Client;
import org.freeswitch.esl.client.transport.CommandResponse;
import org.freeswitch.esl.client.transport.SendMsg;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Service
public class CallToQueue {
    private static final Logger logger = LogManager.getLogger(CallToQueue.class);

    @Autowired
    private RedisQueueService redisQueueService;

    public void handleBridgeQueueAction(Client inboudClient, EslEvent eslEvent, String prefixLog, String callId, String caller) {
        String originCallId = redisQueueService.getCallIdIvrByCaller(caller);
        if(originCallId != null) {
            handleCallQueue(prefixLog, originCallId, caller,
                    inboudClient, eslEvent, null);
        }

    }
    public void handleBridgeToCall(Client inboudClient, EslEvent eslEvent, String prefixLog, String caller) {
        String originCallId = redisQueueService.getCallIdIvrByCaller(caller);
        if(originCallId != null) {
            bridgeToCall(prefixLog, caller, inboudClient, eslEvent, null);
        }
    }

    public void handleCallQueue(String prefixLog, String callId, String caller, Client inboudClient, EslEvent eslEvent, ChannelHandlerContext ctx) {
        List<String> list = new ArrayList<>();
        list.add("1002");
        logger.info("{}list Agent before filter: {}", prefixLog, list);
        if (caller.equals("1001")) {
            if (inboudClient != null) {
                handleCallToConference(inboudClient, eslEvent, null, callId);
            } else {
                handleCallToConference(null, null, ctx, callId);
            }
        }
    }

    public void bridgeToCall(String prefixLog, String caller, Client inboudClient, EslEvent eslEvent, ChannelHandlerContext ctx){
        logger.info("{}bridgeToCall", prefixLog);
        if (caller.equals("1001")) {
            if (inboudClient != null) {
                handleBridgeToCall(inboudClient, eslEvent, null);
            } else {
                handleBridgeToCall(null, null, ctx);
            }
        }
    }

    private void handleBridgeToCall(Client inboudClient, EslEvent eslEvent,
                                        ChannelHandlerContext ctx) {
        if (inboudClient != null) {
            sendMsgCommand(inboudClient, eslEvent, "execute",
                    "set", "record_sample_rate=8000", true);
            sendMsgCommand(inboudClient, eslEvent, "execute",
                    "set", "RECORD_STEREO=true", true);
            sendMsgCommand(inboudClient, eslEvent, "execute", "bridge", "user/1002", true);
        }
        else {
            EventSocketAPI.runCommand(ctx, "set",
                    "record_sample_rate=8000", true);
            EventSocketAPI.runCommand(ctx, "set",
                    "RECORD_STEREO=true", true);
            EventSocketAPI.runCommand(ctx, "bridge",
                    "user/1002", true);
        }
    }
    private void handleCallToConference(Client inboudClient, EslEvent eslEvent,
                                        ChannelHandlerContext ctx,
                                        String callId) {
        if (inboudClient != null) {
            sendMsgCommand(inboudClient, eslEvent, "execute",
                    "set", "record_sample_rate=8000", true);
            sendMsgCommand(inboudClient, eslEvent, "execute",
                    "set", "RECORD_STEREO=true", true);
            sendMsgCommand(inboudClient, eslEvent, "execute",
                    "set", "conference_auto_record=/etc/freeswitch/call-record/${strftime(%Y/%m/%d)}/${sip_call_id}.wav", true);
            sendMsgCommand(inboudClient, eslEvent, "execute",
                    "set", "conference_member_flags=mintwo", true);
            String fsGw2 = "{api_on_answer='sched_api +1 none uuid_transfer ${uuid} -both 'conference:" + callId + "@ctx_conf_ringme' inline'}user/";
            sendMsgCommand(inboudClient, eslEvent, "execute",
                    "bridge", fsGw2 + "1002", true);
        }
        else {
            EventSocketAPI.runCommand(ctx, "set",
                    "record_sample_rate=8000", true);
            EventSocketAPI.runCommand(ctx, "set",
                    "RECORD_STEREO=true", true);
            EventSocketAPI.runCommand(ctx, "set",
                    "conference_auto_record=/etc/freeswitch/call-record/${strftime(%Y/%m/%d)}/${sip_call_id}.wav", true);
            EventSocketAPI.runCommand(ctx, "set",
                    "conference_member_flags=mintwo", true);
            String fsGw2 = "{api_on_answer='sched_api +1 none uuid_transfer ${uuid} -both 'conference:" + callId + "@ctx_conf_ringme' inline'}user/";
            EventSocketAPI.runCommand(ctx, "bridge",
                    fsGw2 + "1002.", true);
        }
    }

    private void sendMsgCommand(Client inboudClient, EslEvent eslEvent, String callCommand,
                                String appName, String appArg, boolean isEventLog) {
        SendMsg cmd = new SendMsg(eslEvent.getEventHeaders().get("Channel-Call-UUID"));
        cmd.addCallCommand(callCommand);
        cmd.addExecuteAppName(appName);
        if (appArg != null) {
            cmd.addExecuteAppArg(appArg);
        }
        if (isEventLog || appName.equals("bridge")) {
            // not async
            cmd.addEventLock();
        }
        CommandResponse commandResponse = inboudClient.sendMessage(cmd);
        logger.info("sendMsgCommand: {} = {} => {}", callCommand + " " + appName + " " + appArg,
                commandResponse.isOk(), commandResponse.getReplyText());
    }
}
