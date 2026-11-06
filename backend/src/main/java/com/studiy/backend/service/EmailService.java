package com.studiy.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Envio de e-mails. Só envia se o SMTP estiver configurado (SPRING_MAIL_HOST + MAIL_FROM);
 * sem isso apenas registra um aviso. O envio é assíncrono para o tempo de resposta não
 * revelar se o e-mail existe. O token nunca vai para os logs.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String remetente;
    private final String frontUrl;

    public EmailService(ObjectProvider<JavaMailSender> mailSender,
                        @Value("${studiy.mail.from:}") String remetente,
                        @Value("${studiy.front.url:http://localhost:5500}") String frontUrl) {
        this.mailSender = mailSender;
        this.remetente = remetente;
        this.frontUrl = frontUrl;
    }

    public void enviarVerificacao(String para, String token) {
        enviar(para, "Studiy: confirme seu e-mail",
                "Bem-vindo ao Studiy! Confirme seu e-mail para ativar a conta:\n\n"
                        + frontUrl.replaceAll("/+$", "") + "/verificar-email.html#token=" + token
                        + "\n\nO link vale por 24 horas. Se não foi você, ignore esta mensagem.");
    }

    private void enviar(String para, String assunto, String texto) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || remetente.isBlank()) {
            log.warn("E-mail não configurado (SPRING_MAIL_HOST/MAIL_FROM): mensagem não enviada.");
            return;
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(remetente);
        msg.setTo(para);
        msg.setSubject(assunto);
        msg.setText(texto);
        CompletableFuture.runAsync(() -> {
            try {
                sender.send(msg);
            } catch (Exception e) {
                log.error("Falha ao enviar e-mail ({}).", e.getClass().getSimpleName());
            }
        });
    }

    public void enviarResetSenha(String para, String token) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || remetente.isBlank()) {
            log.warn("E-mail não configurado (SPRING_MAIL_HOST/MAIL_FROM): recuperação de senha não enviada.");
            return;
        }
        String link = frontUrl.replaceAll("/+$", "") + "/recuperar-senha.html#token=" + token;
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(remetente);
        msg.setTo(para);
        msg.setSubject("Studiy: redefinir sua senha");
        msg.setText("Recebemos um pedido para redefinir sua senha.\n\n" + link
                + "\n\nO link vale por 30 minutos e só pode ser usado uma vez."
                + "\nSe não foi você, ignore esta mensagem.");
        CompletableFuture.runAsync(() -> {
            try {
                sender.send(msg);
            } catch (Exception e) {
                log.error("Falha ao enviar e-mail de recuperação ({}).", e.getClass().getSimpleName());
            }
        });
    }
}
