# Посредник ассистента FitFat

Маленький сервер на Cloudflare Workers между приложением и Claude API. Ключ Anthropic хранится только здесь, в секрете
Worker'а, — в приложении и репозитории его нет. Запросы принимаются только с токеном входа Google от почт из `ALLOWED_EMAILS`.

## Развернуть (один раз)

1. **Ключ Anthropic.** Зайди на console.anthropic.com → API keys → Create key. Пополни баланс в Billing (хватит $5 на старт).
   Там же в Limits можно поставить месячный лимит расходов.
2. **Cloudflare.** Зарегистрируйся на dash.cloudflare.com (бесплатный план).
3. В этой папке:
   ```
   npm install
   npx wrangler login
   ```
4. В `wrangler.toml` впиши свою почту Google в `ALLOWED_EMAILS` (через запятую, если несколько).
5. Положи ключ секретом — wrangler спросит его и не покажет:
   ```
   npx wrangler secret put ANTHROPIC_API_KEY
   ```
6. Разверни:
   ```
   npx wrangler deploy
   ```
   В конце будет адрес вида `https://fitfat-assistant.<имя>.workers.dev`.
7. Впиши адрес в `gradle.properties` проекта: `assistantUrl=https://fitfat-assistant.<имя>.workers.dev` и пересобери приложение.

## Хранилища (один раз)

Пробные недели и отчёты о сбоях лежат в Cloudflare KV (бесплатно). В этой папке:
```
npx wrangler kv namespace create ACCESS
npx wrangler kv namespace create REPORTS
```
Каждая команда напечатает `id` — впиши их в `wrangler.toml` вместо `REPLACE_WITH_…` и сделай `npx wrangler deploy`.

## Отчёты о сбоях

Список: `npx wrangler kv key list --binding REPORTS --remote`
Один отчёт: `npx wrangler kv key get --binding REPORTS --remote "<ключ из списка>"`
Ключи вида `crash:2026-11-09T…` (падение) и `feedback:…` (сообщение из «Настроек»). Хранятся 90 дней.

## Бесплатный доступ

`ALLOWED_EMAILS` в `wrangler.toml` — почты с полным доступом без подписки. Пробная неделя привязана к телефону:
сбросить её для телефона — `npx wrangler kv key delete --binding ACCESS --remote "trial:<устройство>"`.

## Поменять

- Модель — `MODEL` в `wrangler.toml`, потом `npx wrangler deploy`. По умолчанию `claude-sonnet-5-5` — выше Sonnet не поднимать, Opus для этих задач избыточен и дорог. `claude-haiku-5-5` ещё дешевле, но оценки сложных блюд у неё грубее.
- Кто может пользоваться — `ALLOWED_EMAILS`, потом `npx wrangler deploy`.
- Логи в реальном времени — `npx wrangler tail`.

## Проверить локально

`npx wrangler dev` поднимет сервер на `http://127.0.0.1:8787`. Для локального ключа создай файл `.dev.vars` со строкой
`ANTHROPIC_API_KEY=...` — он в `.gitignore` и в репозиторий не попадёт.
