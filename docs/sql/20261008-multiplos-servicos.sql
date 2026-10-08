-- Executar no banco da aplicação ANTES de iniciar a nova versão.
-- Aditiva e idempotente; não reescreve reservas nem sessões existentes.
BEGIN;
ALTER TABLE barbeiros ADD COLUMN IF NOT EXISTS segmento varchar(80);
CREATE TABLE IF NOT EXISTS agendamento_itens (
    agendamento_id bigint NOT NULL REFERENCES agendamentos(id),
    ordem integer NOT NULL,
    servico_id bigint NOT NULL,
    nome varchar(100) NOT NULL,
    preco numeric(19,2) NOT NULL,
    duracao_minutos integer NOT NULL,
    PRIMARY KEY (agendamento_id, ordem)
);
CREATE TABLE IF NOT EXISTS sessao_servicos (
    sessao_id bigint NOT NULL REFERENCES sessoes_conversa(id),
    ordem integer NOT NULL,
    servico_id bigint NOT NULL,
    nome varchar(100) NOT NULL,
    preco numeric(19,2) NOT NULL,
    duracao_minutos integer NOT NULL,
    PRIMARY KEY (sessao_id, ordem)
);
-- Hibernate ddl-auto=update não expande necessariamente CHECKs de enums já existentes.
DO $$
DECLARE restricao record;
BEGIN
    FOR restricao IN SELECT conname FROM pg_constraint
        WHERE conrelid = 'sessoes_conversa'::regclass AND contype = 'c'
          AND pg_get_constraintdef(oid) ~ '\metapa\M'
    LOOP
        EXECUTE format('ALTER TABLE sessoes_conversa DROP CONSTRAINT %I', restricao.conname);
    END LOOP;
END $$;
ALTER TABLE sessoes_conversa ADD CONSTRAINT ck_sessoes_conversa_etapa CHECK (etapa IN (
    'MENU', 'ESCOLHENDO_SERVICO', 'REVISANDO_SERVICOS', 'ESCOLHENDO_DATA', 'ESCOLHENDO_HORARIO',
    'CONFIRMANDO', 'CADASTRANDO_SERVICO_NOME', 'CADASTRANDO_SERVICO_PRECO',
    'CADASTRANDO_SERVICO_DURACAO', 'CONFIRMANDO_CADASTRO_SERVICO', 'ESCOLHENDO_SERVICO_EDICAO',
    'EDITANDO_SERVICO_NOME', 'EDITANDO_SERVICO_PRECO', 'EDITANDO_SERVICO_DURACAO', 'CONFIRMANDO_EDICAO_SERVICO'
));
COMMIT;
