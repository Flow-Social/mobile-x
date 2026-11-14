# Architecture — Инфраструктура и связи компонентов

## 1. Схема размещения

Все серверные компоненты развернуты на сервере:
- **IP**: `31.77.9.57`
- **SSH alias**: `ssh flow`
- **Публичный SSL-домен**: `31.77.9.57.nip.io`

### На сервере работают:
1. **Flow Backend (Go)**:
   - Systemd сервис: `flow-backend.service`
   - Локальный порт: `127.0.0.1:8080`
   - Директория: `/var/www/flow-backend/`
   - База данных: PostgreSQL (`flow_db`, пользователь `flow_user`)
   - Кеш: Redis `127.0.0.1:6379`
   - Хранилище медиа: Cloud.ru S3 (`bucket-96aa19`, регион `ru-central-1`)
2. **Flow Admin Web (Next.js 14, репозиторий `Backend/adminpanel/`)**:
   - PM2 сервис: `flow-admin`
   - Локальный порт: `127.0.0.1:3000`
   - Публичный порт: `https://31.77.9.57.nip.io:8081/`
   - Директория на сервере: `/var/www/flow-admin-web/`
3. **Соседний проект девушки (`prostranstvo`)**:
   - PM2 сервис: `prostranstvo`
   - Локальный порт: `127.0.0.1:5173`
   - Публичный доступ: `http://31.77.9.57/` (порт 80, корень `/`)
   - База данных: PostgreSQL (`prostranstvo_db`, изолирована)

---

## 2. Сетевая топология и Nginx

Вся маршрутизация идет через единый Nginx:
- **Конфигурация Flow**: `/etc/nginx/conf.d/flow.conf` (защищена от перезаписи сторонними деплоями).
- **Порт 80 & 443 (`31.77.9.57.nip.io`)**:
  - `/api/v1/` -> `http://127.0.0.1:8080/api/v1/` (Flow REST + WebSocket)
  - `/api/v2/` -> `http://127.0.0.1:8080/api/v2/` (Flow REST + Realtime Chats)
  - `/api/events` -> `http://127.0.0.1:5173` (SSE для `prostranstvo`)
  - `/` -> `http://127.0.0.1:5173` (Фронтенд `prostranstvo`)
- **Порт 8081 SSL (`31.77.9.57.nip.io:8081`)**:
  - Проксирует на `http://127.0.0.1:3000` (Flow Admin Web).
  - Защищен HTTP Basic Auth (`auth_basic "Flow Admin"`, пароли в `/etc/nginx/.htpasswd`). Доступ открыт только по логину/паролю администратора.
