# Atendimento humano e cancelamentos

## Instalação

1. Faça backup do PostgreSQL.
2. Aplique `docs/sql/20261011-atendimento-humano.sql` antes de subir a aplicação atualizada. A migração preserva os registros existentes e amplia o CHECK do tipo de notificação.
3. Recrie a aplicação e publique `docs/frontend/painel-zalura.html` na Hostinger.
4. Cadastre/aprove o template `atendimento_humano_barbeiro`, em `pt_BR`, na WABA da linha que envia os avisos. Se CENTRAL_WHATSAPP_PHONE_NUMBER_ID estiver configurado, o template deve existir na conta da central. Não basta estar aprovado na conta do cliente.

Corpo sugerido (três parâmetros de texto, nesta ordem):

> Um cliente solicitou atendimento humano no estabelecimento {{3}}. Nome do cliente: {{1}}. Telefone para identificação: {{2}}. Acesse o painel da ZaluraTech para assumir e responder à conversa. O atendimento automático desse cliente ficará pausado até o encerramento manual.

A aprovação/categoria final depende da Meta. O nome pode ser alterado em `whatsapp.atendimento.template`. Sem aprovação, a solicitação funciona no painel e o aviso registra falha. O painel suporta mensagens de texto; áudio, imagens e outros anexos aparecem identificados, sem download/visualização.

## Operação

- Menu mantém Agendar / Meus horários / Cancelar e ganha um segundo cartão com Falar com atendente, respeitando o limite de três botões por cartão.
- Também aceita “atendente”, “falar com alguém” e “quero falar com um atendente”.
- A pausa persiste por profissional + telefone, independentemente da sessão de agendamento. Não expira automaticamente.
- O pedido informa a próxima abertura, considerando semana, intervalos e bloqueios nos próximos 14 dias. A abertura não é promessa de resposta imediata.
- Painel → Atendimentos → Abrir conversa → Assumir atendimento → Enviar resposta → Finalizar atendimento.
- “Retomar robô” encerra manualmente uma solicitação que ainda está aguardando.
- A sessão anterior é preservada. O cliente pode enviar “oi” para recomeçar; confirmar uma opção antiga continua sujeito à validação de disponibilidade, preço e duração da reserva.
- A janela de 24h usa o timestamp da última mensagem recebida da Meta. Fora dela, o painel exige uma nova mensagem do cliente.
- As permissões seguem o painel existente: administrador global. Esta alteração não cria contas individuais por estabelecimento.
- Respostas enviadas diretamente no aplicativo WhatsApp Business não são sincronizadas por esta implementação. Para histórico completo use o painel.
- Até 100 conversas recentes e as últimas 100 mensagens da conversa são mostradas; o banco preserva todo o histórico.

## Cancelamento que não chegou ao barbeiro

Antes, o aviso era uma tentativa após commit, com erro somente em log. O bloqueio de data também não disparava aviso ao profissional. Agora o cancelamento pelo cliente e o cancelamento por bloqueio gravam `CANCELAMENTO_BARBEIRO` na fila na mesma transação da reserva. A restrição por agendamento + tipo impede duplicar o aviso.

O scheduler usa obrigatoriamente `agendamento_cancelado_barbeiro` / `pt_BR` com cinco parâmetros: nome do cliente, serviços, profissional, data e hora. Os avisos ao profissional são enviados mesmo com o robô global pausado. A linha central é respeitada.

Painel → Atendimentos → Notificações de cancelamento mostra PENDENTE, ACEITA_PELA_API, ENTREGUE ou FALHA, tentativas e erro. Os webhooks de entrega/falha da Meta atualizam o registro. Depois de corrigir o cadastro/template, use Reenviar aviso. Não há reenvio automático de falhas, pois um timeout pode ocorrer depois de a Meta ter recebido a mensagem.

Não é possível atribuir a falha específica observada em produção sem consultar logs/retorno da Meta. A aprovação do template, isoladamente, não verifica remetente, conta, idioma, parâmetros ou entrega.

Os cancelamentos antigos, anteriores à fila, não são reenviados automaticamente. Confirme primeiro o que o barbeiro já recebeu para evitar avisos duplicados ou desatualizados.

## Validação após publicação

- Solicite atendimento pelo botão e texto; envie mensagem e botão antigos durante a pausa; só o pedido deve avançar, sem resposta do fluxo de agendamento.
- Verifique isolamento de outro cliente e outro estabelecimento, atualização do painel e encerramento manual.
- Assuma, responda e finalize no painel; confirme a resposta no celular.
- Cancele uma reserva futura e acompanhe o aviso até ENTREGUE. Repita o clique e confirme que não surgiu outro aviso.
- Confira bloqueio de data, template da central e erros visíveis. Aceite da API não significa entrega.
