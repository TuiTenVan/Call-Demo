package com.freeswitch.demoCall.mysql.dao;

import com.freeswitch.demoCall.service.UserService;
import gov.nist.javax.sip.address.UserInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserInfoDao {
    private final Logger logger = LogManager.getLogger(UserInfoDao.class);

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private UserService userService;

    public com.freeswitch.demoCall.mysql.entity.UserInfo getUserInfoByUsername(String username) {

        return userService.getUserInfoByUsernameFromUserService(username);
    }

//    public UserInfo getUserInfoByPhoneNumber(String phoneNumber) {
//
//        return userService.getUserInfoByPhoneFromUserService(phoneNumber);
//    }
//
//    public UserInfo getStaffInfoByUsername(String username) {
//
//        return userService.getStaffInfoByUsernameFromUserService(username);
//    }
}
