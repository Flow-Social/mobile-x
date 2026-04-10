# Tasks

## Done

- `audit-screen-migration` — migration audit экранов и flow'ов завершен
- `stabilize-shared-boundaries` — границы Android canon / shared / platform glue зафиксированы
- `migrate-core-contracts` — core-модули переведены на KMP shape, а `core:api`/`core:data`/часть `core:auth` уже получили реальный `commonMain` слой

## In progress

 - `finish-shared-screen-coverage`
  - done: `feature:comments` переведен на KMP module shape
  - done: `Comments` owner logic/state helpers вынесены в `commonMain`
  - done: Android route/viewmodel/resources `Comments` очищены в `androidMain` layout без legacy `src/main/java`
  - done: `CommentsViewModel` деградирован до Android lifecycle-wrapper, а основной owner logic вынесен в `commonMain` `CommentsStateHolder`
  - done: создан реальный shared route для `Comments` в `commonMain`; Android `CommentsRoute` теперь thin adapter c DI / clipboard / status bar
  - done: default chat context-menu policy в `core:uikit` вынесен в `commonMain`
  - done: `feature:chatssearch` переведен на KMP module shape; route/screen/state holder/UI вынесены в `commonMain`, Android route/viewmodel стали thin adapter'ами
  - done: `SearchTopBar` в `core:uikit` вынесен в `commonMain`, чтобы shared search screens не зависели от Android-only top bar слоя
  - done: `feature:explore` переведен на KMP module shape; shared route/screen вынесены в `commonMain`, Android route оставлен как thin host adapter для resources / bell icon / navigation bar
  - done: `MainShellDefaults` / `MainShellBackground` / `MainContentContainer` вынесены из Android-only `core:uikit` слоя в `commonMain` без изменения Android UX
  - done: `ViewerTopBar` вынесен из Android-only `core:uikit` слоя в `commonMain` без изменения Android viewer behavior
  - done: завершен focused audit по `chat` / `chats` / `profile` с проверкой Android-vs-shared ownership и wasm inheritance
  - done: Android `ChatRoute` переведен на shared `SharedDirectChatRoute` + `DirectChatStateHolder`; Android оставляет у себя только host glue (`snackbar`, `clipboard`, `status bars`, profile-open/navigation), а shared graph получает platform adapters `ChatThreadRepository` / `ChatRealtimeContract` / `ChatPresenceContract`
  - done: Android `ChatsRoute` переведен на shared `SharedChatsRoute` + `ChatsListStateHolder`; старый `ChatsScreenViewModel` больше не участвует в runtime path, а Android Koin теперь отдает `ChatsListRepository`
  - done: Android `RepliesOverlayRoute` переведен на shared `SharedRepliesRoute` + `RepliesStateHolder`; `RepliesOverlayViewModel` больше не нужен для production runtime path, а Android route оставляет у себя только snackbar/status-bar/profile/thread navigation glue
  - finding: `Profile` уже имеет usable shared/web screen/state path, но именно здесь остается главный ownership drift: Android `ProfileRoute` все еще владеет permissions / share / message CTA / edit-profile sheet / bump orchestration
  - done: для `Profile` выделен первый shared action contract (`ProfileActionContext`) для edit/message/share/open-post wiring; Android `ProfileRoute` и wasm `MainFlowShell` переведены на него без изменения Android UX
  - done: wasm `Profile` больше не держит пустые callbacks для message/share profile — message CTA теперь открывает shared direct chat overlay, а share profile копирует реальный profile URL через host-level builder
  - done: wasm `Post` share внутри profile/post flow больше не заглушка — `MainFlowShell` теперь копирует реальный post URL через host-level builder; lookup post для edit внутри shell переведен на shared helper вместо локального ad-hoc поиска
  - done: wasm `Post` delete внутри profile/post flow переведен с локального удаления на server-backed shared path через `ProfileRepository.deletePost()` и `ProfileStateHolder.deletePost()`
  - done: в wasm `MainFlowShell` profile-tag click и post-link click больше не упираются в snackbar — они маршрутизируются в shared profile/post flow через `ViewProfile` и `ViewProfilePost`
  - done: исправлен drift в wasm profile share path — shell теперь копирует полный URL через host builder, а не только slug
  - done: исправлен еще один wasm parity-bug в `ViewPost` — клик по автору поста больше не выбрасывает в self `Profile` tab, а открывает реальный profile overlay автора
  - done: `ViewPost -> comments` в wasm больше не snackbar-заглушка — `MainFlowShell` открывает реальный `SharedCommentsRoute`, передает `CommentsRouteInitialData` и media handoff token
  - done: для web comments runtime добавлены wasm platform bindings (`WasmLogger`, `WasmAuthenticationManager`, `WasmCommentsReadCursorStore`, `WasmPostMediaTransferStore`) и comments graph в wasm Koin bootstrap
  - done: `core:domain` получил JVM target для старых JVM consumers (`core:mock`) без отката KMP migration
  - done: `core:api` / `core:data` / `core:domain` выровнены по KMP source-set layout так, чтобы `src/main/java` жил в `commonMain`, а Android graph продолжал собираться
  - done: в `core:data` сняты еще несколько wasm blockers (`Dispatchers.IO`, `@Volatile`, `synchronized`) в realtime/presence repositories
  - done: `RepliesInbox -> thread open` в wasm теперь повторяет Android path — shell резолвит target comment ids, поднимает post через `PostsRepository` и открывает `SharedCommentsRoute`
   - done: `chat search` в wasm больше не snackbar-заглушка — `MainFlowShell` открывает реальный `SharedSearchUsersRoute`, а `feature:chatssearch` получил wasm target и common-safe cleanup
   - done: `core:uikit.overlayHorizontalSwipeZone` вынесен в expect/actual seam, чтобы shared search UI и wasm собирались без Android-only util drift
   - done: residual Android-owned `PostRoute` detail orchestration схлопнут в shared `SharedPostRoute`; media handoff/model assembly/share/delete/cache-sync теперь живут в `feature:shared`, а Android `PostRoute` стал thin DI/status-bar wrapper
   - done: replies/activity unread sync поджат еще сильнее — `see all` теперь refresh'ит chats unread surface и в Android `FlowNavHost`, и в wasm `MainFlowShell`, а не только локальный replies holder/badge state
   - finding: явные missing-flow parity gaps вокруг `profile/chats` закрыты; следующий слой работы — residual `Profile / media / shell` ownership и hardening
   - done: Android chats area больше не держит отдельный production owner layer для `ChatsRoute` / `ChatRoute` / `RepliesOverlayRoute`; legacy `ChatsScreenViewModel` / `ChatScreenViewModel` / `RepliesOverlayViewModel` остаются только как source/test debt
   - done: Android `CreateProfileRoute` больше не держит отдельный production owner VM path; route использует shared `CreateProfileStateHolder`, а host сохраняет только UI/presentation glue (`toast`, `haptics`, avatar picker placeholder, navigation on complete)
   - следующий шаг: дожимать residual shell/media/DI gaps и Android-route thinning вне chats graph, не меняя Android UX

- `consolidate-di-runtime`
  - done: создан первый реальный `sharedCommonModule` с общими shared-owned bindings (`SharedChatSessionCache`, `LoginViewModel`, `ProfileStateHolder`)
  - done: Android `flowModules()` и wasm `Main.kt` теперь подключают один и тот же shared DI module вместо дублирования этих bindings по разным runtime path
  - done: `ChatsListStateHolder` и `RepliesStateHolder` тоже подняты в `sharedCommonModule`, а wasm bootstrap перестал собирать эти shared owner objects вручную
  - done: wasm `Main.kt` выровнен вокруг общих provider/builder-лямбд для `ProfileStateHolder`, `RepliesStateHolder`, profile share URL и post share URL
  - done: `DirectChatStateHolder` поднят в parameterized shared Koin factory, и shell больше не конструирует direct-chat owner вручную
  - done: `CommentsStateHolder` и `SearchUsersStateHolder` теперь тоже резолвятся из общего shared DI graph; Android `CommentsViewModel` и `SearchUsersScreenViewModel` перестали вручную собирать shared owner objects
  - done: Android shared adapters `AndroidPostComposerRepository` и `AndroidProfileEditorRepository` добавлены в `feature:shared/androidMain`, чтобы shared create/edit profile/post holders могли опираться на тот же Android data graph без wasm-only repo implementations
  - done: в `feature:shared/androidMain` добавлен `AndroidProfileRepository`, чтобы shared `ProfileStateHolder` мог резолвиться и на Android поверх существующих `ProfileRepository` / `PostsRepository` / `PresenceRepository`, а не оставался wasm-only DI веткой
  - done: Android chats DI тоже начал сходиться с shared graph — `ChatsListRepository` теперь биндинг platform adapter для shared `ChatsListStateHolder`, а не отдельный Android `ChatsScreenViewModel` owner path
  - done: Android direct-chat/replies runtime тоже переведен на shared composition — `ChatThreadRepository`, `ChatRealtimeContract`, `ChatPresenceContract` и `RepliesRepository` теперь приходят как platform bindings для shared holders вместо отдельных Android owner VM path
  - done: shared factory path для `CreatePostStateHolder` / `EditPostStateHolder` / `EditProfileStateHolder` параметризован через `PlatformImagePicker`, поэтому и wasm shell, и Android routes теперь резолвят эти shared holders через один `sharedCommonModule`, а не конструируют их вручную
  - следующий шаг: продолжать выносить в shared composition только те зависимости, которые действительно являются common owner path, не смешивая их с platform bindings

- `media-host-boundaries`
  - done: `CreatePostStateHolder`, `EditPostStateHolder` и `EditProfileStateHolder` подняты в shared Koin graph
  - done: wasm shell больше не собирает create/edit profile/post owner logic вручную из platform adapters; вместо этого он получает их через общий DI contract
  - done: Android `ProfileRoute` теперь использует общий `ProfileActionContext` -> `EditProfileOverlayData` pipeline вместо локального дублирования edit-profile action data
  - done: Android `EditProfileRoute` и `EditProfileViewModel` теперь тоже принимают общий `EditProfileOverlayData`; локальный carrier `EditProfileRouteInitialData` удален
  - done: production Android `CreatePostOverlayRoute` больше не живет на отдельном owner VM path; route использует shared `CreatePostStateHolder`, а Android оставляет у себя только ActivityResult/image-picker glue и host draft-close behavior
  - done: standalone Android `EditProfileRoute` больше не зависит от `EditProfileViewModel`; route использует shared `EditProfileStateHolder`, а Android оставляет у себя только single-image picker glue, snackbar/system bars и host close behavior
  - done: `ProfileRoute`-встроенный edit-profile bottom sheet тоже переведен на shared `EditProfileStateHolder`; Android route обновляет header/local cache через `ProfileScreenViewModel` и больше не резолвит отдельный `EditProfileViewModel`
  - done: Android `EditPostOverlayRoute` теперь живет на shared `EditPostStateHolder`, а старый `EditPostViewModel` больше не участвует в host DI/runtime path
  - done: Android create/edit profile/post routes больше не собирают shared holders вручную — route-local picker передается в parameterized shared Koin factory, так что media host glue остается platform-side, а owner creation выровнен с wasm
  - done: shared `ProfileStateHolder` больше не заблокирован на Android отсутствующим repository binding — теперь Android graph отдает `me.floow.shared.profile.uilogic.ProfileRepository` через `AndroidProfileRepository`
  - finding: это не решает `comments` для wasm, потому что там блокер уже ниже — в отсутствии wasm target/runtime у `core:api` и всего comments graph
  - следующий шаг: продолжать сужать ручную host wiring только там, где platform bindings уже существуют и не тянут Android-only transport в shared

## Next

- `finish-shared-screen-coverage`
  - residual `Profile` / media / shell gaps

- `consolidate-di-runtime`
  - свести Android DI и wasm bootstrap к одной shared composition model

- `execute-parity-hardening`
  - убрать drift и временные заглушки
  - дожать wasm parity до Android behavior

## Key tasks now

1. `profile-route-shared-cutover`
   - перевести production `ProfileRoute` с Android `ProfileScreenViewModel` на shared `ProfileStateHolder`
   - оставить в route только host glue: bump / permissions / share / system bars / modal wiring

2. `comments-web-parity`
   - выровнять web comments относительно mobile:
     - передавать полные initial post данные
     - синкать read cursor parity
     - убрать holder lifetime drift
     - проверить replies -> comments anchor/openMode path

3. `shell-policy-unification`
   - выровнять `FlowNavHost` и wasm shell по overlay open/reuse/handoff policy
   - убрать product-level routing drift между платформами

4. `remaining-screen-migration`
   - следующие крупные поверхности:
     - `Feed`
     - `PostRoute`
     - `PostDeepLinkRoute`
     - `Notifications/activity`
     - `CreateProfile/registration`

5. `final-parity-hardening`
   - auth/session
   - realtime/presence/read cursor
   - overlays/fullscreen/media
   - keyboard/viewport/mobile web

## What is now

- `chat/chats/replies/comments/search` уже на shared owner path
- `create/edit profile/post` owner paths уже shared и подтянуты к общему DI
- `CreateProfile` registration flow уже сидит на shared owner path: Android route больше не зависит от `CreateProfileViewModel`, а shared holder владеет validation/save state
- `PostDeepLinkRoute` уже сидит на shared loading/validation path, Android wrapper для post detail стал тоньше
- `PostRoute` теперь тоже сидит на shared detail orchestration path: `SharedPostRoute` владеет media handoff/model assembly/share/delete/cache sync, а Android wrapper оставляет DI/status bar glue
- production `ProfileRoute` и profile entry points уже сидят на shared `ProfileStateHolder`, Android route оставляет в основном host glue
- comments web path уже выровнен по полным initial post данным, holder lifecycle и read cursor sync
- direct chat reply parity gap закрыт: shared projection снова протягивает reply metadata и reply preview рендерится как в Android
- replies/activity path поджат к replies-only policy: Android overlay теперь использует явный shared holder и real read/badge callback, shared shell открывает inbox с тем же `FROM_LAST_SEEN` policy, а `see all` refresh'ит unread surfaces на обеих платформах
- основной remaining structural drift сейчас в residual shell policy, не-shared экранах `Feed/Post/Notifications` и в отсутствии полноценного shared/web surface для registration UI

## What will be after these tasks

- Android и wasm будут открывать и вести основные flows через один shared graph без ad-hoc web обходов
- Android routes станут в основном thin host adapters
- structural KMP migration закончится, а хвост станет обычным parity/stabilization backlog

## Current focus

1. закрывать residual `Profile` / media / shell / DI gaps после comments/chatssearch/explore/chat cutover
2. делать это строго в Android-first режиме: web следует за Android, а не наоборот
3. не переписывать Android `chat/chats/profile`, а сжимать Android routes до thin adapters поверх уже существующих shared owner paths там, где это безопасно; `chat/chats/replies` уже на shared path, следующий крупный хвост — residual `Profile` / shell ownership
4. параллельно сводить DI/runtime к shared composition только для реально shared-owned зависимостей

## Current blocker

- критичный blocker с `core:uikit` снят
- warning по `feature:shared` Android-style layout снят
- `core:data`, `core:auth`, `core:database` больше не упираются в старый module setup
- `core:api` уже имеет первый реальный `commonMain` слой, но transport implementations все еще Android-first
- `core:data` уже имеет расширенный `commonMain` слой, но realtime/presence/chat-storage части остаются Android-first
- `Comments` уже имеет shared route/state path; оставшиеся Android-specific хвосты там теперь host-level (`clipboard`, `status bar`)
- `ChatsSearch` уже имеет shared route/state path; оставшиеся host-level хвосты там сведены к system bars и Android resource bridge
- `Explore` уже имеет shared route/screen path; следующий structural blocker сместился на residual shell/media/profile хвосты, а не на сами route entry points
- compile path для Android + shared + wasm сейчас проходит, так что текущая проблема не в build graph, а в ownership/parity
- отдельный хвост: часть unit tests в `feature:chats` устарела после перехода моделей на `createdAtMillis` / `dayStartMillis`; это test debt, а не признак поломки runtime path
- правило на следующий этап: не менять Android UX/flow ради web; извлекать shared только из уже существующего Android канона
- безопасные shell/media primitives уже начали выноситься в `commonMain`; дальше имеет смысл брать только такие же neutral pieces или явно host-only contracts
