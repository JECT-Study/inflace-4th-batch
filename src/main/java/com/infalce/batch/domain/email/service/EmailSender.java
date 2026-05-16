package com.infalce.batch.domain.email.service;

public interface EmailSender {

    boolean sendEmail(String to, String title, String htmlContent);
}
