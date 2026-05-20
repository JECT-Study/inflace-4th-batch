package com.infalce.batch.domain.email.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class JavaMailEmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username:}")
    private String from;

    @Override
    public boolean sendEmail(String toEmail, String title, String htmlContent) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            if (StringUtils.hasText(from)) {
                helper.setFrom(from);
            }
            helper.setTo(toEmail);
            helper.setSubject(title);
            helper.setText(htmlContent, true);
            javaMailSender.send(message);
            return true;
        } catch (MessagingException | RuntimeException e) {
            log.error("email send failed. To: {}, Title: {}", toEmail, title, e);
            return false;
        }
    }
}
