package com.agentdsl.tools.builtin;

import com.agentdsl.core.annotation.AgentTool;
import com.agentdsl.core.annotation.ToolParam;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * 邮件发送工具。
 * 默认使用 SMTP 协议发送，配置（账号、密码等）可通过环境变量或传参传入。
 */
public class EmailTool {
    private static final Logger log = LoggerFactory.getLogger(EmailTool.class);

    @AgentTool(name = "email_send", description = "发送纯文本或 HTML 邮件。")
    public String emailSend(
            @ToolParam(name = "to", description = "收件人邮箱") String to,
            @ToolParam(name = "subject", description = "邮件主题") String subject,
            @ToolParam(name = "content", description = "邮件文本内容（可为 HTML）") String content,
            @ToolParam(name = "smtpHost", description = "SMTP 服务器地址，为空则读环境变量 AGENTDSL_SMTP_HOST", required = false) String smtpHost,
            @ToolParam(name = "smtpPort", description = "SMTP 服务器端口 (如 465, 587)，为空则读环境变量 AGENTDSL_SMTP_PORT，默认465", required = false) Integer smtpPort,
            @ToolParam(name = "username", description = "邮箱账号，为空则读 AGENTDSL_SMTP_USER", required = false) String username,
            @ToolParam(name = "password", description = "授权码/密码，为空则读 AGENTDSL_SMTP_PASSWORD", required = false) String password) {

        String host = (smtpHost != null && !smtpHost.isEmpty()) ? smtpHost : System.getenv("AGENTDSL_SMTP_HOST");
        String user = (username != null && !username.isEmpty()) ? username : System.getenv("AGENTDSL_SMTP_USER");
        String pass = (password != null && !password.isEmpty()) ? password : System.getenv("AGENTDSL_SMTP_PASSWORD");
        int port = smtpPort != null ? smtpPort : (System.getenv("AGENTDSL_SMTP_PORT") != null ? Integer.parseInt(System.getenv("AGENTDSL_SMTP_PORT")) : 465);

        if (host == null || user == null || pass == null) {
            return "Error: SMTP host, username or password is not provided or configured in environment.";
        }

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", "true");
        if (port == 465) {
            props.put("mail.smtp.ssl.enable", "true");
        } else if (port == 587) {
            props.put("mail.smtp.starttls.enable", "true");
        }

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, pass);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(user));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            if (content.trim().startsWith("<") && content.trim().endsWith(">")) {
                message.setContent(content, "text/html; charset=utf-8");
            } else {
                message.setText(content);
            }

            Transport.send(message);
            return "Successfully sent email to " + to;
        } catch (Exception e) {
            log.error("邮件发送失败, host:{}, to:{}", host, to, e);
            return "Error: " + e.getMessage();
        }
    }
}
