package com.freeswitch.demoCall.service.callin.queue;

import com.freeswitch.demoCall.config.Configuration;
import com.freeswitch.demoCall.mysql.entity.IvrQueue;
import com.freeswitch.demoCall.service.callin.queue.processv2.RedisQueueSessionService;
import com.freeswitch.demoCall.utils.Utils;
import com.google.gson.Gson;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class CallQueueNotifyService {

    private final Logger logger = LogManager.getLogger(CallQueueNotifyService.class);

    private static final Map<String, Future<?>> sessionMap = new ConcurrentHashMap<>();
    private static final Map<String, IvrQueue> map = new HashMap<>();

    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private Gson gson;

    @Autowired
    private RedisQueueSessionService redisQueueSessionService;

    @Autowired
    private Configuration configuration;

    public void startNotifyWaitTimeout(String sessionId, String caller, Long timeout, IvrQueue ivrQueue) {
        logger.info("startNotifyWaitTimeout|{}|{}", sessionId, timeout);
        Runnable myThread = () ->
        {
            try {

                String content = "Cuộc gọi của khách hàng " + caller + " đã quá " + timeout + " (giây) chưa được tiếp nhận";
                sendMessageNotifyToKafka(sessionId, content, ivrQueue, null, "timeout_no_answer");
                sessionMap.remove(sessionId);
                logger.info("NotifyWaitTimeout sessionMap: {}", sessionMap.size());
            } catch (Throwable t) {
                logger.error(t.getMessage(), t);
            }
        };
        ScheduledFuture<?> scheduledFuture = scheduledExecutorService.schedule(myThread, timeout, TimeUnit.SECONDS);
        sessionMap.put(sessionId, scheduledFuture);
        map.put(sessionId, ivrQueue);
    }

    // TODO: notifyService đang gọi các hàm ở cả phía caller và callee và inbound
    //  dẫn tới có thể khác server => ko hoạt động đúng
    public void stopNotifyWaitTimeout(String caller, String originCallId) {
        if (originCallId == null) {

            originCallId = redisQueueSessionService.getCallIdIvrByCaller(caller);
        }
        if (originCallId != null) {

            Future<?> futureTask = sessionMap.get(originCallId);

            if (futureTask != null) {
                logger.info("stopNotifyWaitTimeout: {} -> {}", caller, originCallId);
                futureTask.cancel(true);
                sessionMap.remove(originCallId);
                map.remove(originCallId);
            } else {
                logger.info("stopNotifyWaitTimeout Thread not exist: {} -> {}", caller, originCallId);
            }
        } else {
            logger.info("stopNotifyWaitTimeout originCallId not exist: {}", caller);
        }
    }

    public void startNotifyAnswerTimeout(String sessionId, String caller, String callee, Long timeout,
                                         IvrQueue ivrQueue, String agentUsername) {
        logger.info("startNotifyAnswerTimeout|{}|{}", sessionId, timeout);
        Runnable myThread = () ->
        {
            try {
                String content = "Cuộc gọi giữa khách hàng " + caller + " và agent " + callee + " đã hỗ trợ quá " + timeout + " (giây)";
                sendMessageNotifyToKafka(sessionId, content, ivrQueue, agentUsername, "timeout_answer");
                sessionMap.remove(sessionId);
                logger.info("NotifyAnswerTimeout sessionMap: {}", sessionMap.size());
            } catch (Throwable t) {
                logger.error(t.getMessage(), t);
            }
        };
        ScheduledFuture<?> scheduledFuture = scheduledExecutorService.schedule(myThread, timeout, TimeUnit.SECONDS);
        sessionMap.put(sessionId, scheduledFuture);
    }
    public void stopNotifyAnswerTimeout(String caller, String originCallId) {
        if (originCallId == null) {

            originCallId = redisQueueSessionService.getCallIdIvrByCaller(caller);
        }
        if (originCallId != null) {

            Future<?> futureTask = sessionMap.get(originCallId);

            if (futureTask != null) {
                logger.info("stopNotifyAnswerTimeout: {} -> {}", caller, originCallId);
                futureTask.cancel(true);
                sessionMap.remove(originCallId);
            } else {
                logger.info("stopNotifyAnswerTimeout Thread not exist: {} -> {}", caller, originCallId);
            }
        } else {
            logger.info("stopNotifyAnswerTimeout originCallId not exist: {}", caller);
        }
    }

    public IvrQueue getAndRemoveIvrQueue(String callId) {
        IvrQueue ivrQueue = map.get(callId);
        map.remove(callId);
        return ivrQueue;
    }
    private void sendMessageNotifyToKafka(String callId, String content,
                                          IvrQueue ivrQueue, String agentUsername, String type) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "queue_notification");
        map.put("ownerId", ivrQueue.getOwnerId());

        Map<String, String> data = new HashMap<>();
        data.put("type", type);
        data.put("content", content);
        data.put("time", Utils.formatter2.get().format(new Date()));
        data.put("agentUsername", agentUsername);
        data.put("call_id", callId);
        data.put("queue_id", String.valueOf(ivrQueue.getId()));
        map.put("data", data);

        kafkaTemplate.send(configuration.getNotiTopic(), gson.toJson(map));
        logger.info("sendMessageNotifyToKafka|push2Queue|Message|" + configuration.getNotiTopic() +"|" + gson.toJson(map));
    }
}
