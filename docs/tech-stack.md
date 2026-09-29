# Технологический стек

## Бэкенд

- Java 21 + Spring Boot (Web MVC, Security, Data JPA). 
- PostgreSQL. 
- Docker Compose 

## Фронтенд

- Vue 3 + TypeScript (Composition API), сборка Vite.
- Drag-n-drop: vue-draggable-plus или vuedraggable (обёртки над Sortable.js)
- UI-кит (date picker, таблицы для List view): Element Plus / Ant Design Vue / Naive UI. 

## Инфраструктура и качество

- API: REST + JSON, документация — OpenAPI (springdoc).
- Soft delete: колонка `deleted_at`. Корзина — отдельный режим выдачи удалённых записей, а не глобальный фильтр по таблице (видимость корзины уточняем у заказчика, вопрос 12).
- Тесты: JUnit 5 + Spring Boot Test + Testcontainers (интеграционные тесты на PostgreSQL); e2e на Playwright после выбора фронта.
- CI: GitHub Actions — сборка и тесты на каждый pull request.
- Качество кода: Spotless/Checkstyle на бэкенде, ESLint/Prettier на фронтенде.

## Открытые технологические вопросы

- Обновление доски в реальном времени: WebSocket/SSE или polling — зависит от ответа заказчика про совместную работу (вопрос 17).
- Полнотекстовый поиск: на текущих масштабах достаточно PostgreSQL FTS, отдельный движок не закладываем.
- Хранение вложений (если войдут в скоуп): локальная файловая система или S3-совместимое хранилище.
