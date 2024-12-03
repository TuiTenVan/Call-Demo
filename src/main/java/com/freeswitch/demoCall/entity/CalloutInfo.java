package com.freeswitch.demoCall.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CalloutInfo {

    private int jobId;
    private String fullMsg;
    private int serviceType;
    private String callId;
    private String caller;
    private String callee;
    private String data;
    private int errorCode;
    private boolean outDir;
    private String messageId;
    private long cst;
//    private boolean callKPI;

//    public SignlingServerJob(SignlingBuilder builder) {
//        super();
//        this.fullMsg = builder.fullMsg;
//        this.serviceType = builder.serviceType;
//        this.callId = builder.callId;
//        this.caller = builder.caller;
//        this.callee = builder.callee;
//        this.data = builder.data;
//        this.jobId = builder.jobId;
//        this.errorCode = builder.errorCode;
//        this.outDir = builder.outDir;
//        this.messageId = builder.messageId;
//        this.cst = builder.cst;
//        this.callKPI = builder.callKPI;
//    }
//
//    public int getJobId() {
//        return jobId;
//    }
//
//    public String getFullMsg() {
//        return fullMsg;
//    }
//
//    public int getServiceType() {
//        return serviceType;
//    }
//
//    public String getCallId() {
//        return callId;
//    }
//
//    public String getData() {
//        return data;
//    }
//
//    public String getCaller() {
//        return caller;
//    }
//
//    public String getCallee() {
//        return callee;
//    }
//
//    public int getErrorCode() {
//        return errorCode;
//    }
//
//    public boolean isOutDir() {
//        return outDir;
//    }
//
//    public String getMessageId() {
//        return messageId;
//    }
//
//    public long getCst() {
//        return cst;
//    }
//
//    public void setCst(long cst) {
//        this.cst = cst;
//    }
//
//    public boolean isCallKPI() {
//        return callKPI;
//    }
//
//    @Override
//    public String toString() {
//        StringBuilder buf = new StringBuilder();
//        buf.append("|").append(caller);
//        buf.append("|").append(callee);
//        buf.append("|").append(callId);
//        buf.append("|").append(serviceType);
//        buf.append("|").append(data);
//        return buf.toString();
//    }
//
//    public static class SignlingBuilder {
//        private String fullMsg;
//        private int serviceType;
//        private String callId;
//        private String data;
//        private int jobId;
//        private String caller;
//        private String callee;
//        private int errorCode;
//        private boolean outDir;
//        private String messageId;
//        private long cst;
//        private boolean callKPI;
//
//        public SignlingBuilder(int _jobId) {
//            this.jobId = _jobId;
//        }
//
//        public SignlingBuilder fullMsg(String _fullMsg) {
//            this.fullMsg = _fullMsg;
//            return this;
//        }
//
//        public SignlingBuilder cst(long cst) {
//            this.cst = cst;
//            return this;
//        }
//
//        public SignlingBuilder callKPI(boolean callKPI) {
//            this.callKPI = callKPI;
//            return this;
//        }
//
//        public SignlingBuilder serviceType(int _serviceType) {
//            this.serviceType = _serviceType;
//            return this;
//        }
//
//        public SignlingBuilder callId(String _callId) {
//            this.callId = _callId;
//            return this;
//        }
//
//        public SignlingBuilder caller(String _caller) {
//            this.caller = _caller;
//            return this;
//        }
//
//        public SignlingBuilder callee(String _callee) {
//            this.callee = _callee;
//            return this;
//        }
//
//        public SignlingBuilder data(String _data) {
//            this.data = _data;
//            return this;
//        }
//
//        public SignlingBuilder errorCode(int _errorCode) {
//            this.errorCode = _errorCode;
//            return this;
//        }
//
//        public SignlingBuilder outDir(boolean _outDir) {
//            this.outDir = _outDir;
//            return this;
//        }
//
//        public SignlingBuilder messageId(String _messageId) {
//            this.messageId = _messageId;
//            return this;
//        }
//
//        public SignlingServerJob build() {
//            return new SignlingServerJob(this);
//        }
//
//    }

}
