package com.studiy.backend.config;

import com.studiy.backend.i18n.TradutorMensagens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Respostas de erro padronizadas: sem stack trace, sem detalhes internos, no idioma do pedido (Accept-Language). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final TradutorMensagens tradutor;

    public GlobalExceptionHandler(TradutorMensagens tradutor) {
        this.tradutor = tradutor;
    }

    private Map<String, Object> erro(String mensagem) {
        return Map.of("erro", tradutor.traduzir(mensagem));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacao(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> campos.putIfAbsent(e.getField(), tradutor.traduzir(e.getDefaultMessage())));
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("erro", tradutor.traduzir("Dados inválidos"));
        corpo.put("campos", campos);
        return ResponseEntity.badRequest().body(corpo);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestPartException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> requisicaoInvalida(Exception ex) {
        return ResponseEntity.badRequest().body(erro("Requisição inválida"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> autenticacao(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(erro("Credenciais inválidas"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tamanho(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(erro("Arquivo muito grande (máximo 10MB)"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(ResponseStatusException ex) {
        String msg = ex.getReason() != null ? ex.getReason() : "Erro na requisição";
        return ResponseEntity.status(ex.getStatusCode()).body(erro(msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> inesperado(Exception ex) {
        // 404/405/415 etc. do próprio Spring: devolve o status correto, sem detalhes.
        if (ex instanceof org.springframework.web.ErrorResponse er) {
            return ResponseEntity.status(er.getStatusCode()).body(erro("Requisição inválida"));
        }
        log.error("Erro inesperado", ex); // detalhes só no log do servidor
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(erro("Erro interno. Tente novamente em instantes."));
    }
}
