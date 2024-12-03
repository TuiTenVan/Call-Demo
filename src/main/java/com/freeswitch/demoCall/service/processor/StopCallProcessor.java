package com.freeswitch.demoCall.service.processor;

import com.freeswitch.demoCall.entity.CalloutInfo;
import com.freeswitch.demoCall.entity.UA;
import com.freeswitch.demoCall.service.callin.SipProxy;
import com.freeswitch.demoCall.service.callin.queue.CallQueueNotifyService;
import com.freeswitch.demoCall.service.callin.queue.RedisQueueService;
import com.freeswitch.demoCall.service.callin.queue.processv2.RedisQueueSessionService;
import com.freeswitch.demoCall.utils.SipUtil;
import io.pkts.packet.sip.SipRequest;
import io.sipstack.netty.codec.sip.SipMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class StopCallProcessor {

    @Autowired
    private RedisQueueService redisQueueService;

    @Autowired
    private RedisQueueSessionService redisQueueSessionService;
    @Autowired
    private CallQueueNotifyService callQueueNotifyService;

    public void process(CalloutInfo job) {
        UA ua = CallInMgr.instance().get(job.getCallId());
        if (ua == null) {
            log.warn("|CALL_NOT_FOUND|" + job);
            return;
        }
//		Long startTime = BalancePedingMgr.instance().remove(job.getCallId());
//		CDRLoggergger.logCdrBalancePending(job.getCaller(), job.getCallee(), job.getCallId(),
//				startTime == null ? 0 : ((System.currentTimeMillis() - startTime)) / 1000);
        SipMessageEvent sipEvent = ua.getSipEvent();
        SipRequest byeReq = SipUtil.makeByeRequest(sipEvent.getMessage());
        log.info("|SEND_BYE_REQUEST|" + byeReq);
        SipProxy.send(sipEvent, byeReq);
        CallInMgr.instance().remove(ua);
        redisQueueService.removeCacheAgentCalling(ua.getCallee());
        if (ua.isIvr()) {
//            redisQueueService.cacheAgentAfterHangup(ua.getJid(), 60, ua.getCaller());
            String originCallId = redisQueueSessionService.getCallIdIvrByCaller(ua.getCaller());
            callQueueNotifyService.stopNotifyAnswerTimeout(ua.getCaller(), originCallId);
        }
    }

}
