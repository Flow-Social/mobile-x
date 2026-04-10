# Dev Alpha PR Plan

## Проблема

Текущий dirty tree относительно `dev-alpha` слишком большой для одного нормального коммита или одного вменяемого PR.

Подтвержденное состояние:
- база сравнения: ветка `dev-alpha`, созданная от `dev`
- diff относительно `dev-alpha`: около `263` файлов
- масштаб diff: примерно `+2493 / -36728`

Это не "один рефакторинг". Здесь смешано сразу несколько слоев:
- KMP foundation
- перенос `core` в source sets
- shared runtime и shell
- feature migration
- chats baseline recovery
- profile host thinning
- shell parity
- docs/audit

Если отправить это одним куском:
- review будет плохой
- rollback будет дорогой
- зависимые изменения будет тяжело обсуждать

Оценка идеи одного большого PR: `3/10`

## Рекомендация

Лучший вариант:
1. не один большой commit
2. не один большой PR
3. сделать stacked PRs по слоям migration

Если stacked PRs не подходят по процессу, тогда минимум:
- один branch `dev-alpha`
- внутри него серия маленьких осмысленных коммитов
- PR все равно собирать не "одним куском", а из уже разложенной истории

Оценка stacked PR strategy: `9/10`

## Целевой порядок PR

### PR 1. Docs and Migration Context

Смысл:
- сначала зафиксировать, что происходит
- не мешать narrative с кодом

Что включать:
- [plan-last-april.md](/Users/bogdan/Flow%20V1/Mobile/plan-last-april.md)
- [KMP-context.md](/Users/bogdan/Flow%20V1/Mobile/KMP-context.md)
- [kmp-2.0-tasks.md](/Users/bogdan/Flow%20V1/Mobile/kmp-2.0-tasks.md)
- [context.md](/Users/bogdan/Flow%20V1/Mobile/context.md)
- [tasks.md](/Users/bogdan/Flow%20V1/Mobile/tasks.md)
- [my-tasks-kmp.md](/Users/bogdan/Flow%20V1/Mobile/my-tasks-kmp.md)
- `docs/**`

Рекомендуемый commit:
- `docs: refresh kmp audit and migration context`

Оценка слоя: `9/10`

### PR 2. KMP Foundation and Build Wiring

Смысл:
- ввести KMP как платформенный фундамент
- не смешивать foundation с feature behavior

Что включать:
- [build.gradle.kts](/Users/bogdan/Flow%20V1/Mobile/build.gradle.kts)
- [settings.gradle.kts](/Users/bogdan/Flow%20V1/Mobile/settings.gradle.kts)
- [gradle.properties](/Users/bogdan/Flow%20V1/Mobile/gradle.properties)
- [gradle/libs.versions.toml](/Users/bogdan/Flow%20V1/Mobile/gradle/libs.versions.toml)
- [app/build.gradle.kts](/Users/bogdan/Flow%20V1/Mobile/app/build.gradle.kts)
- `core/*/build.gradle.kts`
- `feature/*/build.gradle.kts`
- новые source sets:
  - `core/api/src/commonMain/**`
  - `core/api/src/androidMain/**`
  - `core/api/src/wasmJsMain/**`
  - `core/auth/src/commonMain/**`
  - `core/auth/src/androidMain/**`
  - `core/data/src/commonMain/**`
  - `core/data/src/androidMain/**`
  - `core/uikit/src/commonMain/**`
  - `core/uikit/src/androidMain/**`
  - `core/uikit/src/wasmJsMain/**`
  - `feature/comments/src/commonMain/**`
  - `feature/comments/src/androidMain/**`
  - `feature/chatssearch/src/commonMain/**`
  - `feature/chatssearch/src/androidMain/**`
  - `feature/explore/src/commonMain/**`
  - `feature/explore/src/androidMain/**`
  - `feature/shared/src/commonMain/**`
  - `feature/shared/src/androidMain/**`
  - `feature/shared/src/wasmJsMain/**`

Что не включать:
- route-level behavior fixes
- profile thinning
- chats baseline recovery
- shell parity

Рекомендуемые commits:
- `build: add kmp source set layout`
- `build: wire feature and core modules for kmp`

Оценка слоя: `8.5/10`

### PR 3. Core Runtime Migration

Смысл:
- перенести shared runtime и abstractions
- убрать старые Android-only core реализации

Что включать:
- `core/api/**`
- `core/auth/**`
- `core/data/**`
- `core/domain/**`

Что осторожно включать:
- `core/uikit/**`

Почему осторожно:
- `core/uikit` очень шумный
- там много удалений старого Android-only UI слоя
- если смешать его с остальным `core`, PR станет хуже

Лучше делить на два PR:
- `PR 3a: core-domain-data-api-auth`
- `PR 3b: core-uikit kmp split`

Рекомендуемые commits:
- `core: migrate api auth data domain to kmp source sets`
- `uikit: split shared ui infrastructure from android-only implementation`

Оценка слоя:
- без деления: `5/10`
- с делением: `8/10`

### PR 4. Shared Runtime and Shell

Смысл:
- зафиксировать появление общего runtime
- не мешать это с Android host cleanup

Что включать:
- [feature/shared/build.gradle.kts](/Users/bogdan/Flow%20V1/Mobile/feature/shared/build.gradle.kts)
- `feature/shared/src/commonMain/**`
- `feature/shared/src/androidMain/**`
- `feature/shared/src/wasmJsMain/**`
- `feature/shared/src/commonTest/**`

Что включать выборочно:
- только те app wiring changes, которые реально нужны для поднятия shared runtime

Что не включать:
- [FlowNavHost.kt](/Users/bogdan/Flow%20V1/Mobile/app/src/main/java/me/floow/app/navigation/FlowNavHost.kt) целиком, если там mixed behavior cleanup

Рекомендуемые commits:
- `shared: introduce main shell and shared feature owners`
- `shared: add wasm host entrypoint and platform adapters`

Оценка слоя: `8.5/10`

### PR 5. Feature Migration: Simple and Medium Flows

Смысл:
- мигрировать фичи, где shared owner path уже очевиден
- не тащить туда chats/profile сложность

Что включать:
- `feature/feed/**`
- `feature/comments/**`
- `feature/chatssearch/**`
- `feature/explore/**`
- `feature/login/**`
- `feature/post/**`

Как лучше резать:
- `PR 5a: feed comments post`
- `PR 5b: login chatssearch explore`

Почему:
- по домену так review проще
- comments/post/feed связаны shell и media transitions
- login/chatssearch/explore слабее связаны и проще

Рекомендуемые commits:
- `feed: switch android host to shared owner path`
- `comments: move route ownership to kmp module`
- `post: align android host with shared post route`
- `login: move login and create-profile onto shared holders`
- `search: migrate chats search to shared route`
- `explore: convert explore module to kmp packaging`

Оценка слоя:
- одним PR: `6/10`
- двумя PR: `8/10`

### PR 6. Chats Baseline Recovery and Shared-era Contracts

Смысл:
- chats это не просто migration
- это отдельный runtime-critical recovery слой

Что включать:
- [feature/chats/src/main/java/me/floow/chats/RepliesOverlayRoute.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/RepliesOverlayRoute.kt)
- [feature/chats/src/main/java/me/floow/chats/di/chatsModule.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/di/chatsModule.kt)
- [feature/chats/src/main/java/me/floow/chats/uilogic/chats/AndroidChatsListRepository.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/uilogic/chats/AndroidChatsListRepository.kt)
- [feature/chats/src/main/java/me/floow/chats/uilogic/chat/AndroidChatThreadRepository.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/uilogic/chat/AndroidChatThreadRepository.kt)
- [feature/chats/src/main/java/me/floow/chats/uilogic/chat/AndroidChatRealtimeContract.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/uilogic/chat/AndroidChatRealtimeContract.kt)
- [feature/chats/src/main/java/me/floow/chats/uilogic/chat/AndroidChatPresenceContract.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/uilogic/chat/AndroidChatPresenceContract.kt)
- [feature/chats/src/main/java/me/floow/chats/uilogic/replies/AndroidRepliesRepository.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/uilogic/replies/AndroidRepliesRepository.kt)
- связанные tests в `feature/chats/src/test/**`

Что не включать:
- случайные большие cleanup в chats, если они не нужны для baseline

Рекомендуемые commits:
- `chats: replace legacy replies viewmodel with shared state holder`
- `chats: add android adapters for shared-era contracts`
- `chats: restore compile baseline and update module wiring`

Оценка слоя: `9/10`

### PR 7. Profile Migration and Android Host Thinning

Смысл:
- `Profile` уже не foundation
- это отдельный hybrid flow cleanup

Что включать:
- [feature/profile/src/main/java/me/floow/profile/ui/profile/ProfileRoute.kt](/Users/bogdan/Flow%20V1/Mobile/feature/profile/src/main/java/me/floow/profile/ui/profile/ProfileRoute.kt)
- связанные profile UI/shared glue файлы
- edit profile/create post/edit post host files, если они реально часть одного profile migration слоя

Как лучше резать:
- `PR 7a: shared profile ownership`
- `PR 7b: android host thinning`

Рекомендуемые commits:
- `profile: move route to shared state holders`
- `profile: extract edit-profile host orchestration`
- `profile: isolate bump android host logic`

Оценка слоя:
- одним PR: `6/10`
- двумя PR: `8/10`

### PR 8. Shell Parity and Android Host Cleanup

Смысл:
- это уже не migration foundation
- это выравнивание behavior между Android host и shared shell

Что включать:
- [feature/shared/src/commonMain/kotlin/me/floow/shared/main/ui/MainFlowShell.kt](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/commonMain/kotlin/me/floow/shared/main/ui/MainFlowShell.kt)
- [app/src/main/java/me/floow/app/navigation/FlowNavHost.kt](/Users/bogdan/Flow%20V1/Mobile/app/src/main/java/me/floow/app/navigation/FlowNavHost.kt)
- связанные shell/overlay helpers

Что уже закрыто:
- discard parity для `CreatePost` и `EditPost`

Что еще должно войти:
- overlay reuse rules
- back policy parity
- snackbar/message parity
- overlay stack growth rules

Рекомендуемые commits:
- `shared-shell: add discard parity for create and edit post`
- `shell: align overlay reuse and back policy with android host`
- `android-host: simplify FlowNavHost after shared migration`

Оценка слоя: `8/10`

## Что делать прямо сейчас с текущим dirty tree

Правильный путь:
1. не делать PR из всего текущего dirty tree
2. сначала разрезать изменения по PR-слоям выше
3. внутри каждого слоя делать маленькие commits

Если нужен быстрый практический вариант без stacked branches:
- оставить `dev-alpha` как интеграционную ветку
- из нее нарезать логические ветки:
  - `/docs-kmp-audit`
  - `/kmp-foundation`
  - `/core-kmp-runtime`
  - `/shared-shell-runtime`
  - `/feature-migration-a`
  - `/chats-baseline-recovery`
  - `/profile-host-thinning`
  - `/shell-parity`

Это лучше, чем один PR из `dev-alpha`.

## Что не надо смешивать в один commit

Нельзя смешивать:
- docs и build changes
- build wiring и feature behavior
- chats baseline recovery и profile thinning
- shared shell parity и `FlowNavHost` большой cleanup
- `core/uikit` giant delete/move и мелкие feature fixes

Оценка смешивания таких слоев: `2/10`

## Минимально адекватная commit-структура, если PR пока один

Если процесс не дает stacked PRs, то хотя бы такие commits:

1. `docs: refresh kmp audit and migration context`
2. `build: add kmp source sets and module wiring`
3. `core: migrate api auth data domain to kmp`
4. `uikit: split shared ui infrastructure into kmp source sets`
5. `shared: add common shell and wasm host entrypoint`
6. `features: migrate feed comments post to shared owners`
7. `features: migrate login search explore to kmp packaging`
8. `chats: restore baseline with shared-era contracts`
9. `profile: thin android route around shared profile flow`
10. `shell: add discard parity and start overlay policy alignment`

Это все еще хуже, чем stacked PRs, но уже не помойка.

Оценка такого варианта: `6.5/10`

## Рекомендуемое решение

Лучший рабочий вариант для этого репозитория:
- `dev-alpha` использовать как integration branch
- не открывать из нее один giant PR сразу
- сначала нарезать stacked PRs по слоям

Финальная оценка стратегии:
- giant commit: `1/10`
- giant PR: `3/10`
- один PR, но с хорошими коммитами: `6.5/10`
- stacked PRs от `dev-alpha`: `9/10`
