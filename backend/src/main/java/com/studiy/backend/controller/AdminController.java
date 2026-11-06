package com.studiy.backend.controller;

import com.studiy.backend.dto.AdminDtos;
import com.studiy.backend.service.AdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Painel admin (somente leitura). Duas camadas: a regra de URL /api/admin/** no SecurityConfig e
 * o @PreAuthorize aqui. ROLE_ADMIN só existe em sessão que passou pelo TOTP (JwtAuthFilter).
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/usuarios")
    public AdminDtos.Pagina<AdminDtos.UsuarioItem> usuarios(@RequestParam(defaultValue = "0") int pagina) {
        return adminService.usuarios(pagina);
    }

    @GetMapping("/pedidos")
    public AdminDtos.Pagina<AdminDtos.PedidoItem> pedidos(@RequestParam(defaultValue = "0") int pagina) {
        return adminService.pedidos(pagina);
    }

    @GetMapping("/uso-ia")
    public AdminDtos.UsoIa usoIa(@RequestParam(defaultValue = "30") int dias) {
        return adminService.usoIa(dias);
    }
}
