# VoidRP Battle Pass

Paper 1.21.1 плагин — сезонная система Battle Pass с заданиями, наградами, Premium подпиской и интеграцией с бэкендом VoidRP.

## Возможности

- Сезонная система с настраиваемыми датами
- Бесплатный и Premium трек наград
- Задания из пула (квесты, убийства, добыча, крафт и т.д.)
- GUI через NPC («Хранитель Сезона»)
- Синхронизация прогресса и Premium статуса с backend VoidRP через RCON
- Soft-depend на VoidRpDailyQuests (расширенный пул заданий)

## Требования

- Paper / Purpur 1.21.1
- Java 21
- (опционально) Vault, LuckPerms, VoidRpDailyQuests

## Сборка

```bash
cd voidrp_battlepass
./gradlew shadowJar
# → build/libs/voidrp-battlepass-*.jar
```

## Установка

1. Положить jar в `plugins/`
2. Перезапустить сервер
3. Настроить `plugins/VoidRpBattlePass/config.yml`

## Конфигурация (`config.yml`)

```yaml
season-name: "Сезон 1 - Летний"
season-start: "2026-05-01"
season-end:   "2026-07-31"
reset-hour: 0

battlepass-npc-names:
  - "§6§lХранитель Сезона"

backend-url: "https://api.void-rp.ru"
game-auth-secret: "your-secret"
```

## Команды

| Команда | Описание |
|---|---|
| `/bp` | Открыть Battle Pass GUI |
| `/bpadmin premium give <nick> <days>` | Выдать Premium |
| `/bpadmin premium revoke <nick>` | Отозвать Premium |
| `/bpadmin premium sync <nick> <expiry_ms>` | Обновить кэш Premium (только локально, без backend-вызова) |
| `/bpadmin season reload` | Перезагрузить конфиг сезона |

**Права:** `voidrp.battlepass.admin`

## Backend интеграция

При выдаче/отзыве Premium через `/bpadmin premium give|revoke` плагин:
1. Обновляет локальный кэш через `sync` команду (без петли backend→RCON)
2. Прогресс игрока периодически пушится на `POST /api/v1/battlepass/progress`
3. Premium статус при входе игрока проверяется через `GET /api/v1/battlepass/premium/{uuid}`

Все вызовы — best-effort: при недоступности backend используются локальные данные.

## Структура данных

Данные хранятся в `plugins/VoidRpBattlePass/`:
- `premium_data.yml` — локальный кэш Premium expiry по UUID
- `progress/` — прогресс игроков по сезонам
- `seasons/` — награды и задания сезона
