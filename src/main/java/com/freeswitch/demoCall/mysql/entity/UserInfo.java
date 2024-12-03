package com.freeswitch.demoCall.mysql.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class UserInfo {
    private String sUserId; //ok
    private String serviceId;
    private Date created_at;
    private String country;
    private String birthday;
    private String avatar;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer gender;
    private String fullname;
    private String pushId;
    private String vpushId;
    private String provision;
    private String revision;
    private String deviceType;
    private String deviceVersion;
    private String deviceId;
    private String phoneNumber;
    private String username;
    private String password;
    private String domain;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer state;
    private String appId;
    private String type;
    private String idProvince;
    private String status;
    private String nameProvince;
    private String language;
    private String idStaff;
    private String staffName;
    private String position;
    private String email;
    private String idDepartment;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer notifySetting;

    @JsonProperty("call-minutes")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long remain_call_charge;

    @JsonProperty("enable-log")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer enable_record_callout;

    @JsonProperty("enable-call")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer enable_callout;

    private String hostname;
    private Integer ownerId;

    private Integer enableVip;
    private String agentNameSupport;
}
