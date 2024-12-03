package com.freeswitch.demoCall.service;

import com.freeswitch.demoCall.entity.CalloutInfo;
import com.freeswitch.demoCall.entity.UA;
import com.freeswitch.demoCall.service.callin.SipProxy;
import com.freeswitch.demoCall.service.callin.queue.RedisQueueService;
import com.freeswitch.demoCall.service.callin.queue.processv2.RedisQueueSessionService;
import com.freeswitch.demoCall.service.processor.CallInMgr;
import io.pkts.packet.sip.SipResponse;
import io.sipstack.netty.codec.sip.SipMessageEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CodeProcessor {
    private final Logger logger = LogManager.getLogger(CodeProcessor.class);
    @Autowired
    private RedisService redisService;

    @Autowired
    private RedisQueueService redisQueueService;

    @Autowired
    private RedisQueueSessionService redisQueueSessionService;

    public void process(CalloutInfo job) {
        UA ua = CallInMgr.instance().get(job.getCallId());
        if (ua == null) {
            logger.error("|CALL_NOT_FOUND|" + job);
            return;
        }
        SipMessageEvent sipEvent = ua.getSipEvent();
        SipResponse response = null;
        switch (job.getErrorCode()) {
            case 183:
            case 180:
                response = sipEvent.getMessage().createResponse(180);
                SipProxy.send(sipEvent, response);
                if (ua.isIvr()) {
                    String originCallId = redisQueueSessionService.getCallIdIvrByCaller(ua.getCaller());

                    if (originCallId != null) {

                        redisQueueSessionService.cacheCallStateByCallId(originCallId, 2, ua.getCaller());
                    }
                }
                break;
            case 200:
                ua.setRecv200(true);
                if (ua.getToSipRespSDP() != null && !ua.isAnswer()) {
                    /* SEND 200 OK */
                    ua.setAnswer(true);
                    response = sipEvent.getMessage().createResponse(200);
                    SipProxy.send(sipEvent, response);
                }

//			BalancePedingMgr.instance().put(job.getCallId(), System.currentTimeMillis());
                break;
            case 486:
            case 487:
            case 488:
            case 489:
                if (!ua.isIvr()) {

                    redisService.putRecentCallinStatusCode(job.getCallId(), job.getErrorCode());
                    response = sipEvent.getMessage().createResponse(486);
                    SipProxy.send(sipEvent, response);
                    CallInMgr.instance().remove(ua);
                }
                redisQueueService.removeCacheAgentCalling(ua.getCallee());
//			BalancePedingMgr.instance().remove(job.getCallId());

                break;
            default:
                logger.warn("Unsupport error code|" + job.getErrorCode() + "|" + job);
                break;
        }
    }

}
