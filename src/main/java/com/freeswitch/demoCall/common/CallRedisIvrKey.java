package com.freeswitch.demoCall.common;

public class CallRedisIvrKey {

    public static final String KEY_IVR_HOTLINE = "ivr_hotline:";

    public static final String KEY_IVR_MENU = "ivr_menu:";

    public static final String KEY_IVR_QUEUE = "ivr_queue:";

    public static final String KEY_IVR_CALLID = "ivr:callid";

    public static final String KEY_IVR_WRAPUP = "ivr:wrapup:";

    public static final String KEY_TIME_AGENT_HANGUP = "ivr:agent_hangup";

    public static final String KEY_IVR_AGENT_NEAREST = "ivr:nearest";

    public static final String KEY_IVR_QUEUE_STAT = "ivr:queue_call:";

    public static final String KEY_IVR_QUEUE_CLIENT_WAIT = "ivr:queue_client_waiting:";

    public static final String KEY_IVR_QUEUE_AGENT_CALLING = "agent_calling";
    // ============================================== SESSION CALL ==============================================

    public static final String KEY_IVR_TICKET = "ivr:ticket";

    public static final String KEY_IVR_CALL_ID_BY_CALLER = "ivr:call_id";

    public static final String KEY_IVR_ADDITIONAL_DATA = "ivr:additionaldata";

    public static final String KEY_IVR_QUEUE_INFO = "ivr:queueinfo";

    public static final String KEY_IVR_CALL_CDR = "ivr:callcdr";

    public static final String KEY_IVR_IS_VIDEO_CALL = "ivr:video";

    public static final String KEY_IVR_LINK_RING_BACK_VIDEO = "ivr:ringback_video";

    public static final String KEY_IVR_IS_IVR_CALL = "ivr:is_ivr";
    // ============================================================================================
    public static final String KEY_REDIS_LIST_AGENT_QUEUE = "LIST_AGENT_QUEUE";

    public static final String KEY_REDIS_AGENT_STATUS_CALL = "AGENT_STATUS_CALL";
}
