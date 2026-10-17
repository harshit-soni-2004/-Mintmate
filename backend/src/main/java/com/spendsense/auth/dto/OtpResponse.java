package com.spendsense.auth.dto;

public class OtpResponse {

    private boolean success;
    private String message;
    private long resendCooldownSeconds;

    public OtpResponse() {}

    public OtpResponse(boolean success, String message, long resendCooldownSeconds) {
        this.success = success;
        this.message = message;
        this.resendCooldownSeconds = resendCooldownSeconds;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getResendCooldownSeconds() {
        return resendCooldownSeconds;
    }

    public void setResendCooldownSeconds(long resendCooldownSeconds) {
        this.resendCooldownSeconds = resendCooldownSeconds;
    }
}
