package com.studiy.backend.config;

import com.studiy.backend.security.CryptoService;
import org.springframework.stereotype.Component;

/** Entrega o CryptoService ao conversor JPA na subida da aplicação. */
@Component
public class CriptoConverterInit {
    public CriptoConverterInit(CryptoService cryptoService) {
        CriptoConverter.configurar(cryptoService);
    }
}
