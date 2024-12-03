package com.freeswitch.demoCall.service;

import com.freeswitch.demoCall.mysql.entity.UserInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Objects;

@Service
public class UserService {
    private final Logger logger = LogManager.getLogger(UserService.class);

    public static final String REMAIN_CHARGE_CALLOUT_KEY = "remain_charge_callout";

    @Value("${ringme.microservice.userinfo.baseUrl}")
    private String userInfoServiceBaseUrl;

//    @Autowired
//    private UserCallAccountDao userCallAccountDao;
    @Autowired
    private WebClient.Builder webClientBuilder;

    @Autowired
    @Qualifier(value = "redisTemplate2")
    private RedisTemplate<String, Object> jedisTemplate2;

    public UserInfo getUserInfoByUsernameFromUserService(String username) {
        try {

//            return webClientBuilder.baseUrl(userInfoServiceBaseUrl)
//                    .build()
//                    .method(HttpMethod.GET)
//                    .uri(builder -> builder.path("/query-registered-user").queryParam("username", username).build())
//                    .header("Accept", "application/json, text/plain, */*")
//                    .retrieve()
//                    .bodyToMono(UserInfo.class).block();
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
        return null;
    }

    public UserInfo getUserInfoByPhoneFromUserService(String phonenumber) {
        try {

//            return webClientBuilder.baseUrl(userInfoServiceBaseUrl)
//                    .build()
//                    .method(HttpMethod.GET)
//                    .uri(builder -> UriComponentsBuilder.fromUri(builder.build())
//                            .path("/get-user-by-phone-number")
//                            .queryParam("phonenumber", "{phonenumber}")
//                            .encode()
//                            .buildAndExpand(phonenumber)
//                            .toUri()
//                    )
//                    .header("Accept", "application/json, text/plain, */*")
//                    .retrieve()
//                    .bodyToMono(UserInfo.class).block();
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
        return null;
    }

    public UserInfo getStaffInfoByUsernameFromUserService(String username) {
        try {

//            return webClientBuilder.baseUrl(userInfoServiceBaseUrl)
//                    .build()
//                    .method(HttpMethod.GET)
//                    .uri(builder -> builder.path("/get-staff-info-by-username").queryParam("username", username).build())
//                    .header("Accept", "application/json, text/plain, */*")
//                    .retrieve()
//                    .bodyToMono(UserInfo.class).block();
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
        return null;
    }

//    public UserCallAccount getRemainChargeByUsername(String username) {
//
//        logger.info("getRemainChargeByUsername: {}", username);
//        Object obj = jedisTemplate2.opsForHash().get(REMAIN_CHARGE_CALLOUT_KEY, username);
//        if (Objects.nonNull(obj)) {
//            return UserCallAccount.builder()
//                    .username(username)
//                    .amount(Long.parseLong(String.valueOf(obj)))
//                    .build();
//        }
//        UserCallAccount remainCharge = userCallAccountDao.getRemainChargeByUsername(username);
//        if (remainCharge == null) {
//            jedisTemplate2.opsForHash().put(REMAIN_CHARGE_CALLOUT_KEY, username, 0);
//            return null;
//        }
//        jedisTemplate2.opsForHash().put(REMAIN_CHARGE_CALLOUT_KEY, username, remainCharge.getAmount());
//        return remainCharge;
//    }
//
//    public int setRemainChargeByUsername(String username, Long amount) {
//        logger.info("setRemainChargeByUsername: {} -> amount: {}", username, amount);
//        UserCallAccount remainCharge = userCallAccountDao.getRemainChargeByUsername(username);
//        if (remainCharge != null) {
//
//            int update = userCallAccountDao.setRemainChargeByUsername(username, amount);
//
//            if (update > 0) {
//                logger.info("setRemainChargeByUsername UPDATE SUCCESS: {} -> amount: {}", username, amount);
//                jedisTemplate2.opsForHash().put(REMAIN_CHARGE_CALLOUT_KEY, username, amount);
//                return 1;
//            }
//        } else {
//            int create = userCallAccountDao.createRemainChargeByUsername(username, amount);
//            if (create > 0) {
//                logger.info("setRemainChargeByUsername CREATE SUCCESS: {} -> amount: {}", username, amount);
//                jedisTemplate2.opsForHash().put(REMAIN_CHARGE_CALLOUT_KEY, username, amount);
//                return 1;
//            }
//        }
//        logger.info("setRemainChargeByUsername FAILED: {} -> amount: {}", username, amount);
//        return 0;
//    }

//    public int subtractRemainChargeCallout(Map<String, String> map) {
//        long timeCall = Long.parseLong(map.get("duration"));
//        if (timeCall == 0) {
//            timeCall = 1;
//        }
//        String phonenumber = map.get("caller");
//
//        UserInfo userInfo = getUserInfoByPhoneFromUserService(phonenumber);
//        String PREFIX_LOG = "CALLOUT|" + map.get("caller") + "|" + map.get("callee") + "|" + map.get("call_id");
//
//        if (userInfo != null) {
//
//            int subtract = userCallAccountDao.subtractRemainChargeCallout(userInfo.getUsername(), timeCall);
//            if (subtract > 0) {
//                logger.info(PREFIX_LOG + "|subtractRemainChargeCallout|SUCCESS");
//                Object obj = jedisTemplate2.opsForHash().get(REMAIN_CHARGE_CALLOUT_KEY, userInfo.getUsername());
//                if (Objects.nonNull(obj)) {
//
//                    jedisTemplate2.opsForHash().put(REMAIN_CHARGE_CALLOUT_KEY, userInfo.getUsername(),
//                            Long.parseLong(String.valueOf(obj)) - timeCall);
//                }
//                return 1;
//            } else {
//                logger.error(PREFIX_LOG + "|subtractRemainChargeCallout|FAIL");
//            }
//        } else {
//            logger.error(PREFIX_LOG + "|subtractRemainChargeCallout|FAIL|userInfo is null");
//        }
//        return 0;
//    }
}
