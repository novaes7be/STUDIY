package com.studiy.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.studiy.backend.i18n.Idioma;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Envio de e-mails. Só envia se o SMTP estiver configurado (SPRING_MAIL_HOST + MAIL_FROM).
 * O envio é assíncrono para o tempo de resposta não revelar se o e-mail existe.
 * Tokens nunca vão para os logs, nem em dev: lá os e-mails aparecem no Mailpit (docker-compose.dev.yml).
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

    /** Assunto e corpo de cada e-mail por idioma. {link}, {login} e {recuperar} são trocados na hora. */
    private record Texto(String assunto, String corpo) {}

    private static final Map<String, Texto> VERIFICACAO = Map.of(
            Idioma.PT, new Texto("Studiy: confirme seu e-mail",
                    "Bem-vindo ao Studiy! Confirme seu e-mail para liberar a geração com IA e o plano Pro:\n\n{link}"
                            + "\n\nO link vale por 24 horas. Se não foi você, ignore esta mensagem."),
            Idioma.EN, new Texto("Studiy: confirm your email",
                    "Welcome to Studiy! Confirm your email to unlock AI generation and the Pro plan:\n\n{link}"
                            + "\n\nThe link is valid for 24 hours. If this wasn't you, ignore this message."),
            Idioma.ES, new Texto("Studiy: confirma tu correo",
                    "¡Te damos la bienvenida a Studiy! Confirma tu correo para activar la generación con IA y el plan Pro:\n\n{link}"
                            + "\n\nEl enlace es válido por 24 horas. Si no fuiste tú, ignora este mensaje."));

    private static final Map<String, Texto> CONTA_EXISTENTE = Map.of(
            Idioma.PT, new Texto("Studiy: tentativa de cadastro com seu e-mail",
                    "Alguém (talvez você) tentou criar uma conta no Studiy com este e-mail, mas ele já está cadastrado.\n\n"
                            + "Para entrar: {login}\nEsqueceu a senha? {recuperar}\n\n"
                            + "Se não foi você, pode ignorar esta mensagem: nada foi alterado na sua conta."),
            Idioma.EN, new Texto("Studiy: sign-up attempt with your email",
                    "Someone (maybe you) tried to create a Studiy account with this email, but it's already registered.\n\n"
                            + "To log in: {login}\nForgot your password? {recuperar}\n\n"
                            + "If this wasn't you, you can ignore this message: nothing was changed on your account."),
            Idioma.ES, new Texto("Studiy: intento de registro con tu correo",
                    "Alguien (quizás tú) intentó crear una cuenta en Studiy con este correo, pero ya está registrado.\n\n"
                            + "Para iniciar sesión: {login}\n¿Olvidaste tu contraseña? {recuperar}\n\n"
                            + "Si no fuiste tú, puedes ignorar este mensaje: no se cambió nada en tu cuenta."));

    private static final Map<String, Texto> RESET_SENHA = Map.of(
            Idioma.PT, new Texto("Studiy: redefinir sua senha",
                    "Recebemos um pedido para redefinir sua senha.\n\n{link}"
                            + "\n\nO link vale por 30 minutos e só pode ser usado uma vez.\nSe não foi você, ignore esta mensagem."),
            Idioma.EN, new Texto("Studiy: reset your password",
                    "We received a request to reset your password.\n\n{link}"
                            + "\n\nThe link is valid for 30 minutes and can only be used once.\nIf this wasn't you, ignore this message."),
            Idioma.ES, new Texto("Studiy: restablece tu contraseña",
                    "Recibimos una solicitud para restablecer tu contraseña.\n\n{link}"
                            + "\n\nEl enlace es válido por 30 minutos y solo se puede usar una vez.\nSi no fuiste tú, ignora este mensaje."));

    /** idioma: pt-BR, en ou es (Idioma); define o texto e a pasta do site nos links. */
    public void enviarVerificacao(String para, String token, String idioma) {
        enviar(para, VERIFICACAO, idioma, Map.of("link", pagina(idioma, "verificar-email.html#token=" + token)));
    }

    /** Alguém tentou cadastrar um e-mail que já tem conta: avisa o dono em vez de revelar isso na resposta da API. */
    public void enviarAvisoContaExistente(String para, String idioma) {
        enviar(para, CONTA_EXISTENTE, idioma,
                Map.of("login", pagina(idioma, "login.html"), "recuperar", pagina(idioma, "recuperar-senha.html")));
    }

    public void enviarResetSenha(String para, String token, String idioma) {
        enviar(para, RESET_SENHA, idioma, Map.of("link", pagina(idioma, "recuperar-senha.html#token=" + token)));
    }

    private String pagina(String idioma, String caminho) {
        return frontUrl + Idioma.pastaDoSite(idioma) + "/" + caminho;
    }

    private void enviar(String para, Map<String, Texto> modelos, String idioma, Map<String, String> valores) {
        Texto modelo = modelos.getOrDefault(idioma, modelos.get(Idioma.PT));
        String corpo = modelo.corpo();
        for (Map.Entry<String, String> v : valores.entrySet()) corpo = corpo.replace("{" + v.getKey() + "}", v.getValue());
        enviar(para, modelo.assunto(), corpo);
    }

    private void enviar(String para, String assunto, String texto) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || remetente.isBlank()) {
            if (modoDev) {
                // Nunca o corpo: ele leva o token do link. Para ver o e-mail completo em dev, use o Mailpit
                // do docker-compose.dev.yml (http://localhost:8025).
                log.warn("[DEV] SMTP não configurado. E-mail \"{}\" não enviado (veja no Mailpit).", assunto);
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
