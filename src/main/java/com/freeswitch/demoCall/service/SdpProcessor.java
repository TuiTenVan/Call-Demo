package com.freeswitch.demoCall.service;

import com.freeswitch.demoCall.entity.CalloutInfo;
import com.freeswitch.demoCall.entity.UA;
import com.freeswitch.demoCall.mysql.dao.CallCDRDao;
import com.freeswitch.demoCall.mysql.entity.IvrQueue;
import com.freeswitch.demoCall.service.callin.SipProxy;
import com.freeswitch.demoCall.service.callin.queue.CallQueueNotifyService;
import com.freeswitch.demoCall.service.callin.queue.processv2.ProcessQueueTask;
import com.freeswitch.demoCall.service.callin.queue.processv2.RedisQueueSessionService;
import com.freeswitch.demoCall.service.processor.CallInMgr;
import com.freeswitch.demoCall.utils.SipUtil;
import io.pkts.packet.sip.SipResponse;
import io.sipstack.netty.codec.sip.SipMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
public class SdpProcessor {

    @Autowired
    private CallCDRDao callCDRDao;

    @Autowired
    private RedisQueueSessionService redisQueueSessionService;

    @Autowired
    private CallQueueNotifyService callQueueNotifyService;


    public void process(CalloutInfo job) throws IOException {

        UA ua = CallInMgr.instance().get(job.getCallId());
        if (ua != null) {
            SipMessageEvent sipEvent = ua.getSipEvent();
            int responseCode = 180;
            if (!ua.isAnswer() && ua.isRecv200()) {
                ua.setAnswer(true);
                responseCode = 200;
            }

            SipResponse response = SipUtil.makeResponseSDP(sipEvent.getMessage(), job.getData());
            log.info("Sdp processor: {}|{}", responseCode, response);
            ua.setToSipRespSDP(response);

            SipProxy.send(sipEvent, ua.getToSipRespSDP());

            if (ua.isIvr()) {

                redisQueueSessionService.setJidTicketCall(ua.getCaller(), ua.getJid().split("@")[0]);

                String originCallId = redisQueueSessionService.getCallIdIvrByCaller(ua.getCaller());

                if (originCallId != null) {

                    ProcessQueueTask.killQueue(originCallId);

                    if (callCDRDao.updateCallee(originCallId, ua.getCallee(), ua.getJid().split("@")[0]) == 1) {
                        log.info("SdpProcessor|updateCalleeIvr|" + job.getCallId());

                        redisQueueSessionService.cacheCallStateByCallId(originCallId, 3, ua.getCaller());
                    }

                    IvrQueue ivrQueue = callQueueNotifyService.getAndRemoveIvrQueue(originCallId);
                    callQueueNotifyService.stopNotifyWaitTimeout(ua.getCaller(), originCallId);

                    if (ivrQueue != null && ivrQueue.getNotifyAnswerTimeout() != null && ivrQueue.getNotifyAnswerTimeout() > 0L) {

                        callQueueNotifyService.startNotifyAnswerTimeout(originCallId, ua.getCaller(), ua.getCallee(),
                                ivrQueue.getNotifyAnswerTimeout(), ivrQueue , ua.getJid());
                    } else {
                        log.info("cannot startNotifyAnswerTimeout: {}", originCallId);
                    }
                } else {
                    log.info("cannot get originCallId: {}", ua.getCaller());
                }
            }
        } else {
            log.warn("|CALL_NOT_FOUND|" + job);
        }
    }
}
