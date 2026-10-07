# Central administrativa no WhatsApp

Uma linha dedicada da Zalura recebe comandos de todos os barbeiros. O remetente é associado aos cadastros em `numero_whatsapp_administrador`, nunca à barbearia que possui a linha central.

## Uso

- O barbeiro envia **Minha agenda** do próprio WhatsApp para a central.
- Se administra uma barbearia, o menu abre diretamente, informando o nome da agenda.
- Se administra várias, escolhe a agenda numa lista paginada. **Trocar barbearia** abre novamente a seleção.
- O menu existente permite consultar reservas, bloquear/liberar datas, cadastrar/editar/ativar/desativar serviços e pausar/reativar o robô da barbearia selecionada.
- A seleção fica no banco por central + remetente. Depois de reiniciar, comandos de texto continuam na agenda selecionada, com a permissão consultada novamente.
- Número não cadastrado recebe uma resposta sem acesso administrativo. Selecionar um ID não autorizado, usar botão de outra barbearia, botão anterior à troca de agenda ou perder a permissão não permite executar a ação.
- A pausa de uma barbearia não desliga a central ou o atendimento das outras.

O número da central deve ser diferente dos números dos barbeiros que receberão os avisos. Cada barbeiro pode usar seu próprio número conectado à API para enviar comandos à central; mensagens vindas da central são ignoradas como atendimento de cliente na linha dele, evitando ciclos entre os bots. Essa proteção usa o número físico da central; por isso são necessários tanto o ID da Meta quanto o telefone.

## Configuração antes do deploy

1. Escolha a linha que será dedicada à central. Ela deve estar registrada em `barbeiros.whatsapp_phone_number_id`, com o token correspondente salvo pelo fluxo atual de conexão. Essa linha deixa de abrir agendamento para clientes enquanto a central estiver habilitada.
2. No painel de cada barbearia, configure:
   - **WhatsApp do administrador**: número real remetente, com país e DDD, apenas dígitos, exatamente como o webhook o recebe.
   - **WhatsApp para notificações**: número que deve receber os avisos; pode ser o mesmo número do administrador/da API dessa barbearia, desde que diferente da central.
3. No `/opt/barbearia-git/.env`, configure os dois valores:

   ```dotenv
   CENTRAL_WHATSAPP_PHONE_NUMBER_ID=ID_DA_LINHA_CENTRAL_NA_META
   CENTRAL_WHATSAPP_NUMERO=55DDDNUMERO_DA_CENTRAL
   ```

   O primeiro valor é o ID numérico da Meta, não o telefone. O segundo é o telefone físico da mesma linha, com país + DDD, sem espaços ou sinais. Não cadastre o telefone de outro estabelecimento nesse campo: ele é usado para ignorar mensagens de retorno da central e impedir loops. A aplicação rejeita configuração incompleta ou valores inválidos na inicialização.

   Para identificar as linhas registradas sem mostrar tokens:

   ```bash
   docker compose -p barbearia exec -T db psql -U barbearia -d barbearia -c "SELECT id, nome, whatsapp_phone_number_id, numero_whatsapp_administrador, numero_whats_app_notificacao FROM barbeiros ORDER BY id;"
   ```

4. Os avisos `novo_agendamento_barbeiro` e `agendamento_cancelado_barbeiro` passam a sair pela central usando suas credenciais. Os templates devem estar disponíveis/aprovados na WABA da linha central, em `pt_BR`, com os cinco parâmetros atuais: cliente, serviço, barbeiro, data e horário. Templates em outra WABA não são transferidos por esta PR.

## Deploy

Após CI verde, mescle a feature em develop e develop em main. No VPS:

```bash
cd /opt/barbearia-git
git switch main
git pull --ff-only origin main
docker compose -p barbearia up -d --build app
docker compose -p barbearia logs --tail=80 app
```

O `ddl-auto=update` cria `sessoes_central_whatsapp`; os cadastros e vínculos existentes permanecem. Não é necessário trocar o HTML da Hostinger. Com as duas variáveis vazias, o comportamento anterior é mantido; isso permite desabilitar a central e reconstruir o container sem apagar dados.

## Validação após deploy

1. Cada um dos cinco administradores envia Minha agenda à central e recebe somente sua agenda.
2. Pause uma barbearia pela central. Um cliente dessa barbearia deixa de receber respostas; clientes das outras continuam atendidos. Reative pela central.
3. Faça um agendamento completo numa linha de cliente. O número de notificações recebe o aviso vindo da central. A linha do barbeiro não responde automaticamente ao aviso.
4. Se um remetente administra várias barbearias, alterne a agenda e tente um botão do menu anterior: a operação deve ser rejeitada.
5. Envie Minha agenda de um número sem cadastro: não deve haver acesso a agenda, cadastro de clientes ou início de agendamento na central.

## Diagnóstico de avisos

```bash
docker compose -p barbearia logs --since=10m --tail=200 app
```

O aceite da API inclui o ID da mensagem e a linha usada. Isso confirma o aceite, não a entrega. Falhas assíncronas informadas pela Meta agora aparecem nos logs e continuam armazenadas em `status_mensagens_recebidos`. Uma falha de configuração ou envio do aviso não desfaz o agendamento.

Avisos ao barbeiro são enviados após commit, pelo mecanismo atual, sem fila durável ou reenvio automático nesta PR. Avisos aos clientes por bloqueio continuam usando a linha da própria barbearia e a fila existente. O estado da central não cria entrega retroativa de avisos antigos.

## Testes

`./gradlew build` valida os fluxos existentes e os testes novos: cinco administradores, isolamento de agendas/serviços/bloqueios, IDs forjados, revogação de permissão, troca de agenda, contexto dos botões capturado antes do commit, credenciais da central, paginação, configuração desabilitada/inválida e prevenção de loops. O CI com PostgreSQL verifica também a criação da nova tabela e a seleção preservada após reiniciar o SessionFactory.
