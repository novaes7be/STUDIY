package com.studiy.backend.config;

import com.studiy.backend.security.CryptoService;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Cifra/decifra automaticamente campos de texto marcados com @Convert(converter = CriptoConverter.class). */
@Converter
public class CriptoConverter implements AttributeConverter<String, String> {

    private static volatile CryptoService crypto;

    static void configurar(CryptoService servico) {
        crypto = servico;
    }

    @Override
    public String convertToDatabaseColumn(String atributo) {
        return atributo == null ? null : crypto.cifrar(atributo);
    }

    @Override
    public String convertToEntityAttribute(String dado) {
        return dado == null ? null : crypto.decifrar(dado);
    }
}
