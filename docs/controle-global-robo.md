# Controle global do robô por barbearia

O administrador cadastrado em `numero_whatsapp_administrador` envia **Minha agenda**, escolhe **Controle do robô** e vê o estado atual:

- **Pausar robô** abre uma confirmação. **Sim, pausar** desliga o atendimento aos clientes dessa barbearia.
- **Voltar** mantém o estado e retorna ao controle.
- **Reativar robô** habilita o atendimento imediatamente, sem segunda confirmação.

A pausa não tem expiração automática. Apenas o administrador do estabelecimento pode alterar o estado. O administrador continua acessando Minha agenda, consultas e configurações pelo WhatsApp durante a pausa.

## Comportamento durante a pausa

- Textos, botões e listas enviados por clientes são ignorados antes de cadastrar clientes ou alterar a sessão/agendamento. Não é enviada resposta informando que o robô está pausado.
- Mensagens ignoradas são marcadas como processadas pelo webhook. Elas não são reproduzidas quando o robô volta; o cliente precisa enviar uma nova mensagem ou interação.
- Sessões existentes são preservadas. A reativação não envia um menu em massa; o próximo contato segue as validações atuais do fluxo, inclusive disponibilidade e validade da confirmação.
- Avisos de cancelamento por bloqueio aos clientes permanecem pendentes, sem consumir tentativas. O scheduler retoma os envios após a reativação.
- Avisos administrativos ao barbeiro, recebimento de status da Meta, painel e demais barbearias continuam funcionando.
- Mensagens já aceitas pela Meta, ou cujo envio já começou antes da pausa, não podem ser recolhidas.

## Deploy

1. Mesclar a PR em `develop` e depois `develop` em `main`, após o CI ficar verde.
2. No VPS, usar o procedimento atual:

   ```bash
   cd /opt/barbearia-git
   git pull origin main
   docker compose -p barbearia up -d --build app
   ```

3. O `spring.jpa.hibernate.ddl-auto=update` atual adiciona `barbeiros.robo_ativo boolean default true not null`. Cadastros existentes continuam ativos; cadastros novos também começam ativos. A pausa é persistida e continua valendo após reiniciar a aplicação. Nenhuma variável de ambiente ou template novo da Meta é necessário.
4. Pelo número administrador, testar Minha agenda → Controle do robô → Pausar robô → Voltar; o estado continua ativo. Repetir e confirmar a pausa. Com outro número, enviar texto e clicar em uma opção antiga; o bot não deve responder nem criar reserva. Pelo administrador, entrar novamente em Minha agenda e reativar. O próximo contato do cliente deve receber o atendimento normal.

Não é necessário trocar o HTML da Hostinger: este controle é feito no menu administrativo do WhatsApp.

## Validação automatizada

`./gradlew build` executa a suíte existente e os testes novos de autorização, confirmação, pausa, reativação, isolamento, idempotência, preservação de notificações e persistência. O CI roda em PRs para main e develop e publica os relatórios.

O CI também sobe PostgreSQL 16 e testa a atualização de uma tabela de barbeiros existente pelo Hibernate, o default para registros antigos e novos e a preservação da pausa após recriar o SessionFactory. Localmente, esse teste só roda quando `TEST_POSTGRES_URL`, `TEST_POSTGRES_USERNAME` e `TEST_POSTGRES_PASSWORD` apontam para um PostgreSQL de testes em que o usuário possa criar schemas.
