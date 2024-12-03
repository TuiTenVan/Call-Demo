package com.freeswitch.demoCall.service.callin.queue.processv2;


import com.freeswitch.demoCall.mysql.entity.IvrQueue;
import com.freeswitch.demoCall.service.callin.SipProxy;
import com.freeswitch.demoCall.service.callin.queue.AgentCall;
import com.freeswitch.demoCall.service.callin.queue.RedisQueueService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.freeswitch.esl.client.inbound.Client;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class ProcessQueueTask {

    private static final Logger logger = LogManager.getLogger(ProcessQueueTask.class);

    public static final ConcurrentHashMap<String, ScheduledFuture<?>> sessionMap = new ConcurrentHashMap<>();

    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    @Autowired
    private RedisQueueService redisQueueService;

    @Autowired
    private SipProxy sipProxy;

    @Autowired
    private AgentQueueService agentQueueService;

//    public void processQueueWithEslInbound(Client inboudClient, EslEvent eslEvent,
//                                           String prefixLog, String sessionId, String caller,
//                                           List<AgentCall> list, IvrQueue ivrQueue) {
//
//        if (sessionMap.get(sessionId) != null) {
//            logger.info("processQueueWithEslInbound|" + sessionId + "|thread exist|reject");
//            return;
//        }
//        QueueItemCall queueItemCall = new QueueItemCall(prefixLog, list, sessionId, caller,
//                inboudClient, eslEvent, ivrQueue, agentQueueService);
//        ScheduledFuture<?> scheduledFuture = scheduledExecutorService
//                .scheduleWithFixedDelay(queueItemCall, 0, ivrQueue.getWaitAnswerTimeout() + 1, TimeUnit.SECONDS);
//
//        sessionMap.put(sessionId, scheduledFuture);
//        logger.info("processQueueWithEslInbound|" + sessionId);
//    }
//
//    public void processQueueWithEslInboundCtx(ChannelHandlerContext ctx,
//                                              String prefixLog, String sessionId, String caller,
//                                              List<AgentCall> list, IvrQueue ivrQueue) {
//        if (sessionMap.get(sessionId) != null) {
//            logger.info("processQueueWithEslInboundCtx|" + sessionId + "|thread exist|reject");
//            return;
//        }
//        QueueItemCall queueItemCall = new QueueItemCall(prefixLog, list, sessionId, caller,
//                ctx, ivrQueue, agentQueueService);
//        ScheduledFuture<?> scheduledFuture = scheduledExecutorService
//                .scheduleWithFixedDelay(queueItemCall, 0, ivrQueue.getWaitAnswerTimeout() + 1, TimeUnit.SECONDS);
//
//        sessionMap.put(sessionId, scheduledFuture);
//        logger.info("processQueueWithEslInboundCtx|" + sessionId);
//    }

    public static void killQueue(String sessionId) {
        ScheduledFuture<?> futureTask = sessionMap.get(sessionId);

        if (futureTask != null) {
            logger.info("killQueue: " + sessionId);
            futureTask.cancel(true);
            sessionMap.remove(sessionId);
        } else {
            logger.info("killQueue session not exist: " + sessionId);
        }
    }
}
