package com.freeswitch.demoCall.mysql.dao;

import com.freeswitch.demoCall.entity.CallCdrDto;
import com.freeswitch.demoCall.mysql.entity.CallCDR;
import com.freeswitch.demoCall.mysql.entity.UserInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Repository
public class CallCDRDao {

    private final Logger logger = LogManager.getLogger(CallCDRDao.class);

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private WebClient.Builder webClientBuilder;

//    @Value("${ringme.microservice.log.baseUrl}")
//    private String logServiceBaseUrl;

    @Autowired
    private UserInfoDao userInfoDao;

//    public Map<String, Object> getBySessionId(String sessionId, boolean isCallout) {
//        try {
//            String sql = "SELECT caller_phonenumber, callee_phonenumber, mnp_from, mnp_to, ";
//
//            if(isCallout) {
//                sql = sql + "caller_position, caller_type, caller_appid, caller_id_province, caller_id_department, caller_version_app, ";
//            } else {
//                sql = sql + "callee_position, callee_type, callee_appid, callee_id_province, callee_id_department, callee_version_app, ";
//            }
//            sql = sql + "order_id, additional_data \n" +
//                    "FROM call_cdr \n" +
//                    "WHERE session_id = :session_id \n" +
//                    "ORDER BY created_at DESC \n" +
//                    "LIMIT 0, 1;";
//
//            MapSqlParameterSource params = new MapSqlParameterSource();
//            params.addValue("session_id", sessionId);
//
//            Map<String, Object> map = new HashMap<>();
//            jdbcTemplate.query(sql, params, (rs) -> {
//                map.put("caller_phonenumber", rs.getString("caller_phonenumber"));
//                map.put("callee_phonenumber", rs.getString("callee_phonenumber"));
//                map.put("mnp_from", rs.getString("mnp_from"));
//                map.put("mnp_to", rs.getString("mnp_to"));
//
//                map.put("order_id", rs.getString("order_id"));
//                map.put("additionaldata", rs.getString("additional_data"));
//
//                if(isCallout) {
//
//                    map.put("position", rs.getString("caller_position"));
//                    map.put("type", rs.getString("caller_type"));
//                    map.put("appid", rs.getString("caller_appid"));
//                    map.put("id_province", rs.getString("caller_id_province"));
//                    map.put("id_department", rs.getString("caller_id_department"));
//                    map.put("version_app", rs.getString("caller_version_app"));
//                } else {
//
//                    map.put("position", rs.getString("callee_position"));
//                    map.put("type", rs.getString("callee_type"));
//                    map.put("appid", rs.getString("callee_appid"));
//                    map.put("id_province", rs.getString("callee_id_province"));
//                    map.put("id_department", rs.getString("callee_id_department"));
//                    map.put("version_app", rs.getString("callee_version_app"));
//                }
//            });
//            return map;
//
//        } catch (Exception ex) {
//            logger.error(ex.getMessage(), ex);
//        }
//        return new HashMap<>();
//    }

    public CallCDR getBySessionId(String sessionId) {
        try {
            String sql = "SELECT caller_phonenumber, callee_phonenumber, caller_username, callee_username, " +
                    "call_type, hotline \n" +
                    "FROM call_cdr \n" +
                    "WHERE session_id = :session_id;";

            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("session_id", sessionId);

            return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> CallCDR.builder()
                    .callerPhoneNumber(rs.getString("caller_phonenumber"))
                    .calleePhoneNumber(rs.getString("callee_phonenumber"))
                    .callerUsername(rs.getString("caller_username"))
                    .calleeUsername(rs.getString("callee_username"))
                    .callType(rs.getString("call_type"))
                    .hotline(rs.getString("hotline"))
                    .build());

        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
        return null;
    }

    public int setSpeechToText(String sessionId) {
        try {
            String sql = "UPDATE `call_cdr` " +
                    "SET `speech_to_text` = '1' " +
                    "WHERE (`session_id` = :session_id);\n";

            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("session_id", sessionId);

            return jdbcTemplate.update(sql, params);

        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
        return 0;
    }

    public Map<String, Object> getRecentCdrByCallee(String callee) {
        try {
            String sql = "SELECT session_id, caller_phonenumber, caller_username, caller_position, caller_type, " +
                    "caller_appid, caller_id_province, caller_id_department, caller_version_app, order_id, additional_data \n" +
                    "FROM call_cdr \n" +
                    "WHERE call_type = \"callout\" AND callee_phonenumber = :callee_phonenumber \n" +
                    "ORDER BY created_at DESC \n" +
                    "LIMIT 0, 1;";

            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("callee_phonenumber", callee);

            Map<String, Object> map = new HashMap<>();
            jdbcTemplate.query(sql, params, (rs) -> {
                map.put("call_id", rs.getString("session_id"));
                map.put("caller", rs.getString("caller_phonenumber"));
                map.put("ott_username", rs.getString("caller_username"));
                map.put("order_id", rs.getString("order_id"));
                map.put("additionaldata", rs.getString("additional_data"));
                map.put("caller_position", rs.getString("caller_position"));
                map.put("caller_type", rs.getString("caller_type"));
                map.put("caller_appid", rs.getString("caller_appid"));
                map.put("caller_id_province", rs.getString("caller_id_province"));
                map.put("caller_id_department", rs.getString("caller_id_department"));
                map.put("caller_version_app", rs.getString("caller_version_app"));
            });
            return map;

        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
        return new HashMap<>();
    }
//
//    public int create(CallCDR callCDR) {
//
//        try {
//            Map map = webClientBuilder.baseUrl(logServiceBaseUrl)
//                    .build()
//                    .method(HttpMethod.POST)
//                    .uri(builder -> builder.path("/api/v1/cdr/invite").build())
//                    .bodyValue(callCDR)
//                    .header("Accept", "application/json, text/plain, */*")
//                    .retrieve()
//                    .bodyToMono(Map.class).block();
//            if (map != null && map.get("code").equals(200)) {
//                return 1;
//            }
//            return 0;
//        } catch (Exception ex) {
//            logger.error(ex.getMessage(), ex);
//        }
//        return 0;
//    }

    public int updateCallee(String callId, String callee, String calleeUsername) {

        try {
            CallCDR callCDR = CallCDR.builder()
                    .sessionId(callId)
                    .calleePhoneNumber(callee)
                    .calleeUsername(calleeUsername)
                    .build();

            UserInfo userInfo = userInfoDao.getUserInfoByUsername(calleeUsername);

            if (userInfo != null) {

                callCDR.setCalleeIdDepartment(userInfo.getIdDepartment());
                callCDR.setCalleeIdProvince(userInfo.getIdProvince());
                callCDR.setCalleeAppId(userInfo.getAppId());
                callCDR.setCalleeType(userInfo.getType());
                callCDR.setCalleePosition(userInfo.getPosition());
            }
//            Map map = webClientBuilder.baseUrl(logServiceBaseUrl)
//                    .build()
//                    .method(HttpMethod.POST)
//                    .uri(builder -> builder.path("/api/v1/cdr/update_callee").build())
//                    .bodyValue(callCDR)
//                    .header("Accept", "application/json, text/plain, */*")
//                    .retrieve()
//                    .bodyToMono(Map.class).block();
//            if (map != null && map.get("code").equals(200)) {
//                return 1;
//            }
            return 0;
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
        return 0;
    }

    public int updateQueue(String callId, Integer queueId) {

        try {
            String sql = "UPDATE `call_cdr` " +
                    "SET `queue_id` = :queue_id " +
                    "WHERE (`session_id` = :session_id);";

            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("queue_id", queueId);
            params.addValue("session_id", callId);

            return jdbcTemplate.update(sql, params);

        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
        return 0;
    }

//    public int createError(CallCDR callCDR) {
//
//        try {
//            Map map = webClientBuilder.baseUrl(logServiceBaseUrl)
//                    .build()
//                    .method(HttpMethod.POST)
//                    .uri(builder -> builder.path("/api/v1/cdr/error").build())
//                    .bodyValue(callCDR)
//                    .header("Accept", "application/json, text/plain, */*")
//                    .retrieve()
//                    .bodyToMono(Map.class).block();
//            if (map != null && map.get("code").equals(200)) {
//                return 1;
//            }
//            return 0;
//        } catch (Exception ex) {
//            logger.error(ex.getMessage(), ex);
//        }
//        return 0;
//    }

    public int updateStart(String callId, String status, long timeStart, long setupDuration) {

        try {
            CallCdrDto callCdrDto = CallCdrDto.builder()
                    .callId(callId)
                    .status(status)
                    .timeStart(timeStart)
                    .setupDuration(setupDuration)
                    .build();
//            Map map = webClientBuilder.baseUrl(logServiceBaseUrl)
//                    .build()
//                    .method(HttpMethod.POST)
//                    .uri(builder -> builder.path("/api/v1/cdr/accept").build())
//                    .bodyValue(callCdrDto)
//                    .header("Accept", "application/json, text/plain, */*")
//                    .retrieve()
//                    .bodyToMono(Map.class).block();
//            if (map != null && map.get("code").equals(200)) {
//                return 1;
//            }
//            return 0;
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
        return 0;
    }

    public int updateEnd(String callId, String callStatus, int callStatusCode,
                         long callDuration, String closedBy, long endTime, String linkRecord) {

        try {
            CallCdrDto callCdrDto = CallCdrDto.builder()
                    .callId(callId)
                    .callStatus(callStatus)
                    .callStatusCode(callStatusCode)
                    .callDuration(callDuration)
                    .closedBy(closedBy)
                    .endTime(endTime)
                    .linkRecord(linkRecord)
                    .build();
//            Map map = webClientBuilder.baseUrl(logServiceBaseUrl)
//                    .build()
//                    .method(HttpMethod.POST)
//                    .uri(builder -> builder.path("/api/v1/cdr/bye").build())
//                    .bodyValue(callCdrDto)
//                    .header("Accept", "application/json, text/plain, */*")
//                    .retrieve()
//                    .bodyToMono(Map.class).block();
//            if (map != null && map.get("code").equals(200)) {
//                return 1;
//            }
            return 0;
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
        return 0;
    }

    public int updateCancel(String callId, String callStatus, int callStatusCode,
                            long setupDuration, String closedBy, long endTime) {

        try {
            CallCdrDto callCdrDto = CallCdrDto.builder()
                    .callId(callId)
                    .callStatus(callStatus)
                    .callStatusCode(callStatusCode)
                    .setupDuration(setupDuration)
                    .closedBy(closedBy)
                    .endTime(endTime)
                    .build();
//            Map map = webClientBuilder.baseUrl(logServiceBaseUrl)
//                    .build()
//                    .method(HttpMethod.POST)
//                    .uri(builder -> builder.path("/api/v1/cdr/cancel").build())
//                    .bodyValue(callCdrDto)
//                    .header("Accept", "application/json, text/plain, */*")
//                    .retrieve()
//                    .bodyToMono(Map.class).block();
//            if (map != null && map.get("code").equals(200)) {
//                return 1;
//            }
            return 0;
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
        return 0;
    }

//    public int updateCalleeIvr(String callId, String calleePhonenumber, String calleeUsername) {
//
//        try {
//            String sql = "UPDATE `call_cdr` " +
//                    "SET `callee_phonenumber` = :callee_phonenumber, `callee_username` = :callee_username " +
//                    "WHERE (`session_id` = :session_id);";
//
//            MapSqlParameterSource params = new MapSqlParameterSource();
//            params.addValue("callee_phonenumber", calleePhonenumber);
//            params.addValue("callee_username", calleeUsername);
//            params.addValue("session_id", callId);
//
//            return jdbcTemplate.update(sql, params);
//
//        } catch (Exception ex) {
//            logger.error(ex.getMessage(), ex);
//        }
//        return 0;
//    }
}
