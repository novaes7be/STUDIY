package com.studiy.backend.service;

import com.studiy.backend.dto.AdminDtos;
import com.studiy.backend.model.Pedido;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.IaChamadaRepository;
import com.studiy.backend.repository.PedidoRepository;
import com.studiy.backend.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.List;

/** Consultas do painel admin (somente leitura). Valores já formatados para exibição. */
@Service
@Transactional(readOnly = true)
public class AdminService {

    private static final int TAMANHO = 50;

    private final UsuarioRepository usuarios;
    private final PedidoRepository pedidos;
    private final IaChamadaRepository iaChamadas;
    private final BloqueioLoginService bloqueio;

    public AdminService(UsuarioRepository usuarios, PedidoRepository pedidos, IaChamadaRepository iaChamadas,
                        BloqueioLoginService bloqueio) {
        this.usuarios = usuarios;
        this.pedidos = pedidos;
        this.iaChamadas = iaChamadas;
        this.bloqueio = bloqueio;
    }

    public AdminDtos.Pagina<AdminDtos.UsuarioItem> usuarios(int pagina) {
        Page<Usuario> p = usuarios.findAll(PageRequest.of(Math.max(0, pagina), TAMANHO, Sort.by(Sort.Direction.DESC, "id")));
        return new AdminDtos.Pagina<>(p.map(u -> new AdminDtos.UsuarioItem(u.getId(), u.getNome(), u.getEmail(), u.getPapel(),
                u.planoEfetivo(), u.isEmailVerificado(), u.isMfaAtivo(), bloqueio.bloqueado(u), u.getCriadoEm())).getContent(),
                p.getNumber(), p.getTotalPages(), p.getTotalElements());
    }

    public AdminDtos.Pagina<AdminDtos.PedidoItem> pedidos(int pagina) {
        Page<Pedido> p = pedidos.findAll(PageRequest.of(Math.max(0, pagina), TAMANHO, Sort.by(Sort.Direction.DESC, "criadoEm")));
        return new AdminDtos.Pagina<>(p.map(x -> new AdminDtos.PedidoItem(x.getId(),
                x.getUsuario() == null ? null : x.getUsuario().getEmail(), x.getMetodo(), x.getStatus(),
                PagamentoService.formatarBrl(x.getValorCentavos()), x.getCriadoEm(), x.getPagoEm())).getContent(),
                p.getNumber(), p.getTotalPages(), p.getTotalElements());
    }

    public AdminDtos.UsoIa usoIa(int dias) {
        int d = Math.min(Math.max(dias, 1), 365);
        LocalDateTime desde = LocalDateTime.now().minusDays(d);
        List<Object[]> linhas = iaChamadas.resumoPorDia(desde);
        List<AdminDtos.UsoIaDia> porDia = linhas.stream().map(r -> new AdminDtos.UsoIaDia(
                ((Date) r[0]).toLocalDate(), n(r[1]), n(r[2]), n(r[3]), n(r[4]), usd(n(r[5])))).toList();
        List<AdminDtos.UsoIaConsumidor> top = iaChamadas.maioresConsumidores(desde).stream()
                .map(r -> new AdminDtos.UsoIaConsumidor((String) r[0], n(r[1]), usd(n(r[2])))).toList();
        long chamadas = linhas.stream().mapToLong(r -> n(r[1])).sum();
        long custo = linhas.stream().mapToLong(r -> n(r[5])).sum();
        return new AdminDtos.UsoIa(d, usd(custo), chamadas, porDia, top);
    }

    private static long n(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    /** micro-US$ → "US$ 0,0123" */
    static String usd(long micro) {
        BigDecimal v = BigDecimal.valueOf(micro).movePointLeft(6).setScale(4, RoundingMode.HALF_UP);
        return "US$ " + v.toPlainString().replace('.', ',');
    }
}
