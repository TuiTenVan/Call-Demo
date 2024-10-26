package com.freeswitch.demoCall.common;

public class CallRedisKey {

    // ============================================================================================

    public static final String KEY_IVR_HOTLINE = "ivr_hotline:";
    public static final String KEY_IVR_MENU = "ivr_menu:";
    public static final String KEY_IVR_QUEUE = "ivr_queue:";
    public static final String KEY_IVR_CALLID = "ivr:callid";
    public static final String KEY_IVR_WRAPUP = "ivr:wrapup:";

    // ============================================================================================
    public static final String KEY_REDIS_MNP_FOR_PHONE = "SIPGW_MNP:"; // TODO: thêm env vào prefix redis key

    public static final String KEY_REDIS_PARTNER = "CALL_PARTNER";

    public static final String KEY_REDIS_HOTLINE = "CALL_HOTLINE";
    public static final String KEY_REDIS_HOTLINE_PARTNER = "CALL_HOTLINE_PARTNER";
    public static final String KEY_REDIS_HOTLINE_INDEX = "CALL_HOTLINE_INDEX";

    public static final String KEY_REDIS_HOTLINE_CALLIN = "CALL_HOTLINE_CALLIN";
    public static final String KEY_REDIS_HOTLINE_CALLIN_INDEX = "CALL_HOTLINE_CALLIN_INDEX";

    public static final String KEY_REDIS_HOTLINE_CALLIN_ITEM = "CALL_HOTLINE_CALLIN_ITEM";

    // ============================================================================================
    public static final String KEY_REDIS_LIST_AGENT_QUEUE = "LIST_AGENT_QUEUE";

    public static final String KEY_REDIS_AGENT_STATUS_CALL = "AGENT_STATUS_CALL";
}
