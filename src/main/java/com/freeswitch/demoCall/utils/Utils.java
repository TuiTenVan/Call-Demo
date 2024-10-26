package com.freeswitch.demoCall.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.stream.Collectors;

public class Utils {

    private static final Logger logger = LogManager.getLogger(Utils.class);

    public static final String VIETNAM_COUNTRYCODE = "VN";
    public static final String VIETNAM_PHONECODE = "84";
    public static final ThreadLocal<SimpleDateFormat> formatter2 = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        }
    };
    private static final String ALPHA_NUMERIC_STRING = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final String prefix = "ws://";
    private static final String spliter = ":";

    public static String genRandomString(int size) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < size; i++) {
            int character = (int) (Math.random() * ALPHA_NUMERIC_STRING.length());
            builder.append(ALPHA_NUMERIC_STRING.charAt(character));
        }
        return builder.toString();
    }

    public static String getSipDomainFromWsUrl(String freeswitchWsUrl) {
        return freeswitchWsUrl.substring(freeswitchWsUrl.indexOf(prefix) + prefix.length(),
                freeswitchWsUrl.lastIndexOf(spliter));
    }

    public static String getSipAddrFromWsUrl(String freeswitchWsUrl) {
        return freeswitchWsUrl.substring(freeswitchWsUrl.indexOf(prefix) + prefix.length());
    }

    public static String formatPhoneNumberByCountryCode(String phoneNumber) {
        String newPhoneNumber = phoneNumber;
        if (Utils.isVNPhoneNumber(phoneNumber)) {
            if (phoneNumber.startsWith("0")) {
                newPhoneNumber = phoneNumber.substring(1);
            }
            return "84" + newPhoneNumber;
        } else {
            if (phoneNumber.startsWith("+")) {
                return phoneNumber.substring(1);
            }
        }
        return newPhoneNumber;

    }

    public static String getRegionCodeForNumber(String phonenumber) {
//		try {
//			if (phonenumber.startsWith("0")) {
//				return VIETNAM_COUNTRYCODE;
//			}
//			PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
//			PhoneNumber swissNumberProto = phoneUtil.parse(phonenumber, VIETNAM_COUNTRYCODE);
//			return phoneUtil.getRegionCodeForNumber(swissNumberProto);
//		} catch (Exception e) {
//			logger.error("Parser Region Code Number Exception = " + phonenumber, e);
//		}
        return VIETNAM_COUNTRYCODE;
    }

    public static boolean isVNPhoneNumber(String phoneNumber) {
        String regionCode = getRegionCodeForNumber(phoneNumber);
        return VIETNAM_COUNTRYCODE.equals(regionCode);
    }

    public static final String validatePhoneNumber(String phoneNumber) {
        if (phoneNumber.startsWith("+")) {
            return phoneNumber;
        } else if (!phoneNumber.startsWith("0")) {
            phoneNumber = "0" + phoneNumber;
        }
        return phoneNumber;
    }

    public static final String makeSipURI(String schema, String id, String url) {
        return schema + id + "@" + url;
    }

    public static String toJson(Map<String, Object> map) {
        Gson gson = new GsonBuilder().create();
        return gson.toJson(map);

    }
    public static boolean isValidPhoneNumber(String phoneNumber) {
        if (!(phoneNumber.startsWith("0") || phoneNumber.startsWith("+84"))) {
            return false;
        }
        if (phoneNumber.endsWith(".")) {
            return false;
        }
        return true;
    }
    public static String convertTimestampToStringDate(long stamp) {

//        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        return formatter2.get().format(new Date(stamp));
    }

    public static String validatePhone(String phoneNumber, String defaultContryCode) {

        if(phoneNumber.startsWith("queue_")) {
            return phoneNumber;
        }
        PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        String formattedNumber;
        try {
            Phonenumber.PhoneNumber parsedNumber = phoneNumberUtil.parse(phoneNumber, defaultContryCode);
            formattedNumber = phoneNumberUtil.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.NATIONAL);
            formattedNumber = formattedNumber.replaceAll(" ", "");
            logger.info("Phonenumber format: {} => {}", phoneNumber, formattedNumber);
            return formattedNumber;
        } catch (Exception e) {
            logger.error("invalid phone number: {}" + e.getMessage(), phoneNumber);
        }
        return phoneNumber;
    }

    public static String getLink(String path, boolean setHttpCache) {

        return (setHttpCache ? "http_cache://" : "") + "http://30.29.0.178:8089" + path;
    }
}
