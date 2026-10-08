# Bloqueio com mensagem opcional e avisos aos clientes

## Pelo WhatsApp do barbeiro

1. Envie `Minha agenda` à central e escolha a barbearia, se administrar várias.
2. Envie `Bloquear 12/10/2026`, substituindo pela data desejada. A prévia mostra as reservas futuras afetadas e não altera nenhuma reserva.
3. Escreva uma mensagem de até 255 caracteres, como `Não atenderei neste dia por uma consulta médica.`. A mensagem é salva na prévia por 10 minutos. Escrever outro texto substitui a mensagem anterior.
4. Use **Confirmar bloqueio** para confirmar com a mensagem salva. Sem escrever nada, o mesmo botão confirma com o aviso padrão. **Sem mensagem** confirma descartando o texto anterior. **Cancelar bloqueio** descarta a prévia sem alterar reservas.

Ao confirmar, somente as reservas futuras CONFIRMADAS daquela barbearia e data são canceladas. Cada reserva afetada recebe um aviso na fila, com seu cliente, serviço, barbeiro, data, horário e mensagem opcional. Se as reservas mudaram desde a prévia, nenhum bloqueio ou cancelamento é realizado: consulte novamente. Confirmações repetidas não duplicam avisos.

A mensagem do bloqueio também aparece quando alguém seleciona essa data, usa um horário antigo ou tenta confirmar um horário escolhido antes do bloqueio. Sem mensagem, aparece o aviso padrão. Liberar o dia remove esse aviso e não restaura reservas canceladas.

## Pelo painel

O campo **Mensagem aos clientes (opcional)** aceita até 255 caracteres. Antes de criar o bloqueio, o painel consulta os afetados e pede confirmação com a data, quantidade de reservas, clientes e mensagem. Desistir preserva o rascunho e não cancela nada. O servidor confere a lista novamente sob a mesma trava usada para agendar.

- `POST /api/admin/barbeiros/{id}/bloqueios/previa`: recebe `{data}` e retorna as reservas futuras afetadas, sem alterar dados.
- `POST /api/admin/barbeiros/{id}/bloqueios`: recebe `{data,motivo,idsConfirmados}`. `motivo` pode ser omitido, vazio ou nulo. Reservas exigem os IDs da prévia. Clientes antigos do painel podem continuar bloqueando dias vazios sem esse campo.
- Editar um bloqueio altera o aviso para futuras tentativas. Não reenvia avisos antigos. Mover um bloqueio para uma data com reservas continua sendo recusado; crie um novo bloqueio com prévia para confirmar esses cancelamentos.

As rotas exigem a autenticação ADMIN e CSRF do painel. No WhatsApp, a prévia e seus botões continuam vinculados ao barbeiro e ao administrador autorizado.

## Templates na Meta antes do deploy

O aviso ao cliente sai pela **linha da própria barbearia**, com as credenciais desse cadastro, e não pela central. Assim o cliente identifica quem cancelou e pode remarcar na mesma conversa.

- Sem mensagem: mantém `cancelamento_por_bloqueio_cliente`, `pt_BR`, com 5 parâmetros: cliente, serviço, barbeiro, data e horário. Avisos antigos permanecem compatíveis com esse template.
- Com mensagem: usa `cancelamento_por_bloqueio_cliente_motivo`, `pt_BR`, com 6 parâmetros na mesma ordem, adicionando a mensagem. O modelo pronto para submissão está em `docs/templates/cancelamento_por_bloqueio_cliente_motivo.json`.

Os dois templates precisam estar aprovados e disponíveis na WABA das linhas que atendem os clientes. Ter templates de aviso ao barbeiro aprovados na central não garante que esses templates de aviso ao cliente existam na WABA dele. Não altere o template antigo de 5 parâmetros para 6: os avisos já enfileirados continuam usando o formato antigo. Quebras de linha na mensagem ficam salvas e são convertidas em espaços no parâmetro do template.

Para identificar a WABA das linhas sem expor tokens:

```bash
docker compose -p barbearia exec -T db psql -U barbearia -d barbearia -c "SELECT id,nome,whatsapp_phone_number_id,whatsapp_waba_id FROM barbeiros ORDER BY id;"
```

Cadastre o modelo pelo WhatsApp Manager ou envie seu JSON ao endpoint de criação de templates usando o token e o ID da WABA correspondente. Esta PR não cria nem aprova templates na conta Meta. Enquanto não houver aprovação, avisos com mensagem personalizada podem falhar; não use um texto livre como substituto de envio fora da janela de conversa.

## Deploy e validação

Depois de aprovar os templates e mesclar a PR em develop e depois em main:

```bash
cd /opt/barbearia-git
git switch main
git pull --ff-only origin main
docker compose -p barbearia up -d --build app
docker compose -p barbearia logs --tail=100 app
```

Os campos opcionais `previas_bloqueio.motivo` e `notificacoes_pendentes.motivo_bloqueio` são criados pelo `ddl-auto=update`; dados anteriores continuam válidos. A configuração da central no `.env` permanece a mesma.

Para usar o novo fluxo do painel, substitua o HTML da Hostinger pelo `docs/frontend/painel-zalura.html` desta PR. O fluxo pelo WhatsApp já funciona depois do deploy da API.

Teste uma data com duas reservas de clientes de teste, confirme o bloqueio com mensagem e confira os dois avisos. Verifique também uma data sem mensagem e uma nova tentativa de agendamento na data bloqueada. Liberar a data deve remover o aviso, mantendo os cancelamentos anteriores.

## Diagnóstico de entrega

A criação do aviso, o aceite da API e a entrega são estados diferentes. O scheduler consulta até 20 avisos por lote; avisos de robôs pausados permanecem pendentes até reativar, sem impedir os avisos de outras barbearias. Não há reenvio automático de falhas definitivas nesta alteração.

```bash
docker compose -p barbearia exec -T db psql -U barbearia -d barbearia -c "SELECT n.id,a.barbeiro_id,n.status,n.tentativas,n.ultimo_erro FROM notificacoes_pendentes n JOIN agendamentos a ON a.id=n.agendamento_id ORDER BY n.id DESC LIMIT 20;"
docker compose -p barbearia logs --since=10m --tail=200 app
```

`PENDENTE`: ainda aguarda processamento ou reativação do robô. `ACEITA_PELA_API`: há ID da mensagem, sem entrega confirmada ainda. `ENTREGUE`: webhook confirmou entrega/leitura. `FALHA`: confira `ultimo_erro` e os logs, incluindo aprovação do template, WABA e credenciais. Uma falha de envio não desfaz o bloqueio ou o cancelamento. Os logs de produção são necessários para determinar por que um aviso anterior não foi entregue; os testes não confirmam a configuração da conta Meta.

## Validação automatizada

`./gradlew build` cobre mensagem persistida, ausência/descarte de mensagem, todas as reservas afetadas, isolamento entre barbearias e datas, expiração/autorização, desistência, botão repetido, mudança de reservas após a prévia, mensagem exibida ao cliente e parâmetros de envio. O CI usa PostgreSQL para verificar atualização de tabelas existentes e persistência após reiniciar, e executa o teste DOM do painel com jsdom 26.1.0.
