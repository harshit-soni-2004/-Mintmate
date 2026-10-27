package com.spendsense.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class SendOtpRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Valid email is required")
    private String email;

    private String purpose = "EMAIL_VERIFICATION";

    public SendOtpRequest() {}

    public SendOtpRequest(String email, String purpose) {
        this.email = email;
        this.purpose = purpose != null ? purpose : "EMAIL_VERIFICATION";
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }
}
