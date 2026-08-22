package com.ganjj.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ganjj.authorization.request.LoginRequest;
import com.ganjj.authorization.request.RegisterRequest;
import com.ganjj.authorization.response.TokenResponse;
import com.ganjj.authorization.service.user.AccountService;

/**
 * Um refresh token só pode ser usado uma vez, mesmo que vários pedidos cheguem
 * ao mesmo tempo.
 *
 * Conferir a revogação e gravar em seguida deixaria uma janela entre os dois
 * passos: dois pedidos simultâneos veriam "não revogado" antes de qualquer um
 * gravar, e ambos renovariam. Por isso a gravação é a própria disputa.
 */
@SpringBootTest
class RefreshConcorrenteTest {

    private static final int PEDIDOS_SIMULTANEOS = 8;

    @Autowired
    private AccountService accountService;

    @Test
    void apenasUmPedidoSimultaneoConsegueRenovar() throws Exception {
        accountService.register(new RegisterRequest("corrida@ganjj.com", "senhaSegura123"));
        TokenResponse inicial = accountService.login(
                new LoginRequest("corrida@ganjj.com", "senhaSegura123"));

        String refreshToken = inicial.refreshToken();

        // Todas as threads ficam presas na trava e são soltas juntas, para as
        // chamadas caírem no mesmo instante.
        CountDownLatch largada = new CountDownLatch(1);
        List<Callable<Boolean>> tarefas = new ArrayList<>();

        for (int i = 0; i < PEDIDOS_SIMULTANEOS; i++) {
            tarefas.add(() -> {
                largada.await();
                try {
                    accountService.refresh(refreshToken);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            });
        }

        ExecutorService executor = Executors.newFixedThreadPool(PEDIDOS_SIMULTANEOS);
        try {
            List<Future<Boolean>> resultados = new ArrayList<>();
            for (Callable<Boolean> tarefa : tarefas) {
                resultados.add(executor.submit(tarefa));
            }

            largada.countDown();

            int sucessos = 0;
            for (Future<Boolean> resultado : resultados) {
                if (resultado.get()) {
                    sucessos++;
                }
            }

            assertThat(sucessos)
                    .as("%d pedidos simultâneos com o mesmo refresh token", PEDIDOS_SIMULTANEOS)
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
