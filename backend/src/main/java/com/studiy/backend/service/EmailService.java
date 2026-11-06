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
 * Envio de e-mails. Só envia se o SMTP estiver configurado (SPRING_MAIL_HOST + MAIL_FROM).
 * O envio é assíncrono para o tempo de resposta não revelar se o e-mail existe.
 * Tokens nunca vão para os logs — exceto no perfil dev sem SMTP (studiy.auth.expor-token-reset-dev,
 * que a StartupGuard proíbe fora de dev), para dar para testar o fluxo localmente.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String remetente;
    private final String frontUrl;
    private final boolean modoDev;

    public EmailService(ObjectProvider<JavaMailSender> mailSender,
                        @Value("${studiy.mail.from:}") String remetente,
                        @Value("${studiy.front.url}") String frontUrl,
                        @Value("${studiy.auth.expor-token-reset-dev:false}") boolean modoDev) {
        this.mailSender = mailSender;
        this.remetente = remetente;
        this.frontUrl = frontUrl.replaceAll("/+$", "");
        this.modoDev = modoDev;
    }

    public void enviarVerificacao(String para, String token) {
        enviar(para, "Studiy: confirme seu e-mail",
                "Bem-vindo ao Studiy! Confirme seu e-mail para liberar a geração com IA e o plano Pro:\n\n"
                        + frontUrl + "/verificar-email.html#token=" + token
                        + "\n\nO link vale por 24 horas. Se não foi você, ignore esta mensagem.");
    }

    /** Alguém tentou cadastrar um e-mail que já tem conta: avisa o dono em vez de revelar isso na resposta da API. */
    public void enviarAvisoContaExistente(String para) {
        enviar(para, "Studiy: tentativa de cadastro com seu e-mail",
                "Alguém (talvez você) tentou criar uma conta no Studiy com este e-mail, mas ele já está cadastrado.\n\n"
                        + "Para entrar: " + frontUrl + "/login.html\n"
                        + "Esqueceu a senha? " + frontUrl + "/recuperar-senha.html\n\n"
                        + "Se não foi você, pode ignorar esta mensagem: nada foi alterado na sua conta.");
    }

    public void enviarResetSenha(String para, String token) {
        enviar(para, "Studiy: redefinir sua senha",
                "Recebemos um pedido para redefinir sua senha.\n\n"
                        + frontUrl + "/recuperar-senha.html#token=" + token
                        + "\n\nO link vale por 30 minutos e só pode ser usado uma vez."
                        + "\nSe não foi você, ignore esta mensagem.");
    }

    private void enviar(String para, String assunto, String texto) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || remetente.isBlank()) {
            if (modoDev) {
                log.warn("[DEV] SMTP não configurado. E-mail que seria enviado a {}:\n{}\n{}", para, assunto, texto);
            } else {
                log.warn("E-mail não configurado (SPRING_MAIL_HOST/MAIL_FROM): mensagem não enviada.");
            }
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
}
