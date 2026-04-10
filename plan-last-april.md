# Аудит Flow и план восстановления честного статуса KMP migration

## Проблема

Текущий `plan-last-april.md` и часть соседних markdown-файлов врут про статус проекта. Они описывают почти завершенную KMP migration, но фактическое состояние кода и compile baseline этому не соответствуют.

Подтвержденный факт на 2026-04-10:
- `:feature:shared:compileKotlinWasmJs` проходит
- `:feature:shared:compileDebugKotlinAndroid` проходит
- `:feature:chats:compileDebugKotlin` проходит
- `:app:compileProductionDebugKotlin` проходит
- compile baseline сейчас зеленый

Подтвержденный сдвиг после refresh:
- compile blocker в `feature:chats` снят
- replies path в Android переведен на shared-era contracts
- `ProfileRoute` уже частично истончен
- в `MainFlowShell` добавлена discard parity для `CreatePost` и `EditPost`

Итоговая оценка проекта сейчас: `8.7/10`.

## Что это за проект

`Flow` это Android-first социальный продукт со свайповой лентой, постами, комментариями, профилями, direct chats, replies inbox, поиском пользователей и explore.

Цель KMP migration:
- не делать второй web-клиент
- не писать отдельный web-only продуктовый сценарий
- вынести Android-каноничное поведение в shared KMP слой
- оставить платформам host/glue, transport, auth hooks, system APIs

Текущий центр тяжести работы:
- честная фиксация статуса migration
- восстановление compile baseline
- shell parity между Android и web/wasm
- cleanup hybrid flow, где owner уже shared, а orchestration еще живет в Android route

## Что уже реально сделано в коде

### Shared owner path уже существует

- `Feed`
  - shared owner: `SharedFeedRoute`, `SharedFeedStateHolder`
  - Android route уже тонкий: [feature/feed/src/main/java/me/floow/feed/ui/FeedRoute.kt](/Users/bogdan/Flow%20V1/Mobile/feature/feed/src/main/java/me/floow/feed/ui/FeedRoute.kt)
- `Chats List`
  - shared owner: `SharedChatsRoute`, `ChatsListStateHolder`
  - Android route тонкий: [feature/chats/src/main/java/me/floow/chats/ChatsRoute.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/ChatsRoute.kt)
- `Direct Chat`
  - shared owner: `SharedDirectChatRoute`, `DirectChatStateHolder`
  - Android route тонкий: [feature/chats/src/main/java/me/floow/chats/ChatRoute.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/ChatRoute.kt)
- `Comments`
  - shared owner: `SharedCommentsRoute`, `CommentsStateHolder`
  - Android host route: [feature/comments/src/androidMain/kotlin/me/floow/comments/CommentsRoute.kt](/Users/bogdan/Flow%20V1/Mobile/feature/comments/src/androidMain/kotlin/me/floow/comments/CommentsRoute.kt)
- `ChatsSearch`
  - shared owner: `SharedSearchUsersRoute`, `SearchUsersStateHolder`
  - Android host route: [feature/chatssearch/src/androidMain/kotlin/me/floow/chatssearch/ui/SearchUsersRoute.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chatssearch/src/androidMain/kotlin/me/floow/chatssearch/ui/SearchUsersRoute.kt)
- `Post View`
  - shared owner: `SharedPostRoute`
  - Android host route: [feature/post/src/main/java/me/floow/post/ui/PostRoute.kt](/Users/bogdan/Flow%20V1/Mobile/feature/post/src/main/java/me/floow/post/ui/PostRoute.kt)
- `Profile`, `Edit Profile`, `Create Post`, `Edit Post`
  - shared state/UI уже есть в `feature:shared`
  - Android route еще тяжелый: [feature/profile/src/main/java/me/floow/profile/ui/profile/ProfileRoute.kt](/Users/bogdan/Flow%20V1/Mobile/feature/profile/src/main/java/me/floow/profile/ui/profile/ProfileRoute.kt)
- `Login`, `Create Profile`
  - shared UI и state уже существуют в `feature:shared`
  - Android routes еще несут platform-specific glue

### Web/wasm host реально живой

- shared web entrypoint: [feature/shared/src/wasmJsMain/kotlin/Main.kt](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/wasmJsMain/kotlin/Main.kt)
- shared shell: [feature/shared/src/commonMain/kotlin/me/floow/shared/main/ui/MainFlowShell.kt](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/commonMain/kotlin/me/floow/shared/main/ui/MainFlowShell.kt)

### KMP feature-модули по факту

- `feature:shared`
- `feature:comments`
- `feature:explore`
- `feature:chatssearch`

## Что не сходится с текущими markdown

### `plan-last-april.md`

Старый файл был неверным:
- заявлял `~95%` готовности
- называл baseline почти законченным
- не отражал реальный compile baseline
- переоценивал степень завершенности `Profile` и shell parity

### `context.md`

Файл устарел:
- опирается на уже несуществующие артефакты
- содержит тезисы, которые уже не совпадают с фактическим кодом

### `kmp-2.0-tasks.md`

Файл полезен только как cleanup backlog:
- не является актуальной картой migration
- не отражает уже закрытый `feature:chats` blocker и следующие реальные долги
- часть задач уже закрыта или устарела

## Repo Health

### Compile baseline

Проверено командой:

```bash
./gradlew :feature:shared:compileKotlinWasmJs \
  :feature:shared:compileDebugKotlinAndroid \
  :feature:chats:compileDebugKotlin \
  :app:compileProductionDebugKotlin
```

Результат:
- `:feature:shared:compileKotlinWasmJs` — `OK`
- `:feature:shared:compileDebugKotlinAndroid` — `OK`
- `:feature:chats:compileDebugKotlin` — `OK`
- `:app:compileProductionDebugKotlin` — `OK`

### Что уже закрыто

Закрытые точки:
- [feature/chats/src/main/java/me/floow/chats/RepliesOverlayRoute.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/RepliesOverlayRoute.kt) больше не держит старый `RepliesOverlayViewModel`
- [feature/chats/src/main/java/me/floow/chats/di/chatsModule.kt](/Users/bogdan/Flow%20V1/Mobile/feature/chats/src/main/java/me/floow/chats/di/chatsModule.kt) больше не держит мертвые Android-only bindings
- Android shared-era adapters добавлены для chats/replies
- [feature/profile/src/main/java/me/floow/profile/ui/profile/ProfileRoute.kt](/Users/bogdan/Flow%20V1/Mobile/feature/profile/src/main/java/me/floow/profile/ui/profile/ProfileRoute.kt) уже разделен на route, edit-profile host helpers и bump host helper
- [feature/shared/src/commonMain/kotlin/me/floow/shared/main/ui/MainFlowShell.kt](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/commonMain/kotlin/me/floow/shared/main/ui/MainFlowShell.kt) больше не теряет draft/change state при закрытии `CreatePost` и `EditPost`

Формулировка для аудита:

> Compile baseline восстановлен, но shell parity и hybrid orchestration все еще не доведены до Android-канона. Теперь главный риск не красная сборка, а behavioural drift между `FlowNavHost` и `MainFlowShell`.

Оценка блока: `8.5/10`

## Реальная migration matrix

### Done / близко к done

#### Feed
- owner логики сейчас: `shared`
- shared route/state holder: есть
- android-only glue: lifecycle, auth observer, callbacks
- готовность для wasm/web: высокая
- что уже сделано: owner path и route thinning
- что мешает считать migrated: packaging еще Android-only
- следующий шаг: оставить как reference-good path
- оценка: `9/10`

#### Chats List
- owner логики сейчас: `shared`
- shared route/state holder: есть
- android-only glue: route hosting
- готовность для wasm/web: высокая
- что уже сделано: shared list path
- что мешает считать migrated: overlay reuse/back parity еще не выровнены
- следующий шаг: shell-level parity поверх уже зеленого baseline
- оценка: `9/10`

#### Direct Chat
- owner логики сейчас: `shared`
- shared route/state holder: есть
- android-only glue: status bar, clipboard, DI
- готовность для wasm/web: высокая
- что уже сделано: shared runtime и wasm wiring
- что мешает считать migrated: overlay/back policy еще проще Android host
- следующий шаг: shell-level parity для direct chat transitions
- оценка: `8.5/10`

#### Comments
- owner логики сейчас: `shared`
- shared route/state holder: есть
- android-only glue: clipboard, status bar, DI
- готовность для wasm/web: высокая
- что уже сделано: KMP feature module
- что мешает считать migrated: shell parity вокруг comments overlay
- следующий шаг: включить в shell parity matrix
- оценка: `8.5/10`

#### ChatsSearch
- owner логики сейчас: `shared`
- shared route/state holder: есть
- android-only glue: status/nav bar, DI
- готовность для wasm/web: высокая
- что уже сделано: KMP feature module
- что мешает считать migrated: зависит от shell-level parity
- следующий шаг: проверить overlay/back behavior
- оценка: `8.5/10`

#### Post View
- owner логики сейчас: `shared`
- shared route/state holder: shared route есть
- android-only glue: repository wiring, share hooks
- готовность для wasm/web: высокая
- что уже сделано: shared post owner path
- что мешает считать migrated: модуль еще Android-only packaging-wise
- следующий шаг: оставить как hybrid, не называть fully migrated
- оценка: `8/10`

### Partial

#### Login
- owner логики сейчас: в основном `shared`
- shared route/state holder: shared UI и VM есть
- android-only glue: toast, navigation, auth resume hooks
- готовность для wasm/web: средняя/высокая
- что уже сделано: shared login screen и auth flow
- что мешает считать migrated: модуль `feature:login` еще Android-only
- следующий шаг: зафиксировать как hybrid flow, не как finished migration
- оценка: `7.5/10`

#### Create Profile
- owner логики сейчас: в основном `shared`
- shared route/state holder: есть
- android-only glue: haptics, toast, lifecycle
- готовность для wasm/web: средняя/высокая
- что уже сделано: shared state holder и shared screen
- что мешает считать migrated: platform UX glue разнесен
- следующий шаг: проверить parity Android vs wasm host
- оценка: `7.5/10`

#### Replies
- owner логики сейчас: `shared/hybrid`, baseline уже восстановлен
- shared route/state holder: shared replies path существует
- android-only glue: route hosting и deeplink/open-mode glue
- готовность для wasm/web: неплохая
- что уже сделано: Android route больше не держит удаленный VM, baseline зеленый
- что мешает считать migrated: replies/comments/profile transitions еще не доведены до parity
- следующий шаг: пройти overlay transition matrix
- оценка: `7.5/10`

#### Profile
- owner логики сейчас: `hybrid`
- shared route/state holder: shared owner path уже сильный
- android-only glue: permissions, image pickers, bump, modal hosting, orchestration
- готовность для wasm/web: средняя
- что уже сделано: shared state и shared UI сегменты, route уже частично истончен
- что мешает считать migrated: shell/open rules и часть host orchestration еще не выровнены
- следующий шаг: добить overlay/back parity вокруг profile transitions
- оценка: `8/10`

#### Edit Profile
- owner логики сейчас: `hybrid`
- shared route/state holder: есть
- android-only glue: picker hooks, sheet hosting
- готовность для wasm/web: средняя
- что уже сделано: shared state holder и screen
- что мешает считать migrated: modal/back policy между hosts еще не полностью унифицирована
- следующий шаг: проверить overlay close/back semantics в shared shell
- оценка: `7.8/10`

#### Create Post / Edit Post
- owner логики сейчас: `hybrid`
- shared route/state holder: есть
- android-only glue: modal behavior, picker hooks, discard handling
- готовность для wasm/web: средняя
- что уже сделано: shared form/state path
- что уже сделано дополнительно: discard dialog parity уже подтянута в `MainFlowShell`
- что мешает считать migrated: overlay reuse/back semantics и visual shell parity еще не унифицированы
- следующий шаг: закрыть overlay stack behavior
- оценка: `8/10`

#### Main shell / overlays / bottom navigation / media viewer
- owner логики сейчас: расходится между Android и web
- shared route/state holder: web shell shared, Android shell host-specific
- android-only glue: deep links, permissions, overlay behavior, dialogs
- готовность для wasm/web: частичная
- что уже сделано: рабочий `MainFlowShell`
- что уже сделано дополнительно: discard dialogs для create/edit post уже выровнены с Android host
- что мешает считать migrated: Android `FlowNavHost` все еще богаче по reuse/back/parallax/snackbar policy
- следующий шаг: capability-level parity matrix
- оценка: `7.2/10`

### Weak indicator

#### Explore
- owner логики сейчас: KMP UI path есть
- shared route/state holder: есть только легкий shared route/UI
- android-only glue: strings, host styling
- готовность для wasm/web: технически есть
- что уже сделано: модуль KMP
- что мешает считать strong migrated flow: экран слишком тонкий и не подтверждает сложную parity
- следующий шаг: пометить как weak evidence, а не как migration success story
- оценка: `4/10`

## Статус задач из `kmp-2.0-tasks.md`

### Done

- старого `FeedScreen.kt` в `feature/shared/.../main/ui` уже нет
- `feature/feed` уже выглядит как нормальный thin host route

### Partial

- legacy thinning в `profile`
- shell policy unification
- final parity hardening

### Stale / outdated

- предположение, что главная проблема это пару старых заглушек удалить
- трактовка migration как почти завершенной

### Closed after refresh

- `feature:chats` cleanup доведен до зеленой сборки
- replies path в Android больше не ссылается на удаленные реализации

## Главный технический долг: shell parity

Разрыв нужно описывать между:
- [app/src/main/java/me/floow/app/navigation/FlowNavHost.kt](/Users/bogdan/Flow%20V1/Mobile/app/src/main/java/me/floow/app/navigation/FlowNavHost.kt)
- [feature/shared/src/commonMain/kotlin/me/floow/shared/main/ui/MainFlowShell.kt](/Users/bogdan/Flow%20V1/Mobile/feature/shared/src/commonMain/kotlin/me/floow/shared/main/ui/MainFlowShell.kt)

Что надо довести до Android-канона:
- overlay open/reuse rules
- back behavior
- discard dialogs для create/edit post уже закрыты
- snackbar/message behavior
- profile/post/comments/chat overlay policy
- fullscreen/media viewer behavior

Оценка блока: `7.2/10`

## Roadmap

### Этап 0. Rebaseline и фиксация правды
Оценка после этапа: `8.1/10`

- полностью заменить optimistic April plan на этот audit
- перестать использовать старые проценты готовности
- зафиксировать Android как канон поведения
- явно обозначить web/wasm как shared-host стратегию

### Этап 1. Зафиксировать baseline как закрытый
Оценка после этапа: `8.5/10`

- `feature:chats` compile blocker уже закрыт
- `RepliesOverlayRoute.kt` и `chatsModule.kt` уже очищены от мертвых ссылок
- baseline green и больше не должен фигурировать как текущий blocker
- дальше все roadmap-решения строить уже поверх зеленой сборки

### Этап 2. Зафиксировать ownership map по всем flow
Оценка после этапа: `8.5/10`

- для каждого flow использовать один и тот же шаблон из этого документа
- разделять:
  - `shared owner`
  - `hybrid`
  - `android-only glue`
  - `KMP packaging`

### Этап 3. Закрыть shell parity
Оценка после этапа: `9/10`

- описать capability matrix Android shell vs web shell
- подтянуть shared shell к Android-канону
- не считать discard parity финалом: это только один закрытый capability gap
- не прятать platform-specific вещи в reusable UI

### Этап 4. Hybrid flow cleanup
Оценка после этапа: `9.2/10`

- thinning `ProfileRoute`
- cleanup create/edit post ownership
- parity login/create-profile
- media viewer / transfer consistency

### Этап 5. Acceptance и final status
Оценка после этапа: `9.4/10`

Документ и migration считаются приведенными в порядок только если:
- compile baseline зеленый
- ни один flow не назван migrated без owner-level доказательства
- каждый partial flow имеет конкретный blocker
- shell divergence отдельно обозначен как риск
- итоговая оценка не завышена

## Test Plan

Минимальная compile-проверка:

```bash
./gradlew :feature:shared:compileKotlinWasmJs \
  :feature:shared:compileDebugKotlinAndroid \
  :feature:chats:compileDebugKotlin \
  :app:compileProductionDebugKotlin
```

После refresh baseline:
- targeted tests для shared state holders
- chats/replies tests
- comments tests
- shell/navigation tests

Ручные сценарии:
- login -> create profile -> main shell
- feed -> post -> comments -> profile
- chats list -> direct chat -> replies
- profile -> create/edit post
- overlay open/close/back/discard
- wasm shell startup и базовая навигация

## Assumptions

- этот файл должен заменить старый `plan-last-april.md`, а не дополнять его
- Android остается каноном поведения
- `Explore` нельзя использовать как сильное доказательство migration readiness
- главные риски сейчас:
  - `overlay reuse/back parity`
  - `Replies / chats coherence`
  - `Profile`
  - `shell/overlays`
  - завышенные status docs
