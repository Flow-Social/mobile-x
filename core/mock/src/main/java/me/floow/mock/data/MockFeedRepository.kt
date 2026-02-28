package me.floow.mock.data

import kotlinx.coroutines.delay
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.FeedRepository
import me.floow.domain.data.repos.UserProfileRepository
import me.floow.domain.models.*
import me.floow.domain.utils.BehaviorAnalyzer
import me.floow.domain.utils.WeightCalculator
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.domain.values.util.RawValueObjectCreate
import kotlin.random.Random

class MockFeedRepository(
    private val userProfileRepository: UserProfileRepository
) : FeedRepository {
    
    @OptIn(RawValueObjectCreate::class)
    private val mockPosts = run {
        val posts = mutableListOf<Post>()
        var idCounter = 1

        // Helper to create posts
        fun addPost(
            cat: PostCategory,
            name: String,
            username: String,
            desc: String,
            imgSeed: String
        ) {
            posts.add(
                Post(
                    id = "${cat.lowercase()}_${idCounter++}",
                    author = PostAuthor(
                        id = "user_$username",
                        name = ProfileName.createRaw(name),
                        username = ProfileUsername.createRaw(username),
                        avatarUrl = "https://picsum.photos/seed/$username/100/100"
                    ),
					content = PostContent(
						imageUrls = listOf("https://picsum.photos/seed/$imgSeed/400/600"),
						description = desc
					),
                    category = cat,
                    createdAt = System.currentTimeMillis() - Random.nextLong(10000000)
                )
            )
        }

        // --- NATURE ---
        addPost(PostCategories.NATURE, "Анна Котова", "anna_cats", "Невероятный закат, который я поймала вчера на берегу моря. Цвета были просто фантастическими, небо горело огнем! 🌅 #природа #закат #море", "nature1")
        addPost(PostCategories.NATURE, "Петр", "petr_travel", "Тихое горное озеро рано утром. Вода как зеркало, отражает вершины. Идеальное место для медитации ⛰️ #горы #озеро #тишина", "nature2")
        addPost(PostCategories.NATURE, "Forest Life", "forest_lover", "Прогулка по туманному лесу. Воздух такой свежий, что кружится голова. Люблю такие моменты единения с природой 🌲 #лес #туман #природа", "nature3")
        addPost(PostCategories.NATURE, "Ocean Soul", "ocean_soul", "Мощь океана во время шторма. Волны разбиваются о скалы с невероятной силой. Завораживающее зрелище 🌊 #океан #шторм #стихия", "nature4")
        addPost(PostCategories.NATURE, "Macro World", "macro_world", "Капля росы на утреннем цветке. Макросъемка открывает целый новый мир, который мы обычно не замечаем 🌸 #макро #цветы #утро", "nature5")
        addPost(PostCategories.NATURE, "Wildlife", "wild_photo", "Встретил этого красавца в заповеднике. Олени очень осторожны, пришлось долго ждать ради кадра 🦌 #животные #дикаяприрода", "nature6")
        addPost(PostCategories.NATURE, "Sky Watcher", "sky_watcher", "Северное сияние этой ночью было просто космическим! Зеленые всполохи танцевали по всему небу ✨ #северноесияние #небо #космос", "nature7")
        addPost(PostCategories.NATURE, "Desert Wind", "desert_wind", "Бескрайние пески пустыни на рассвете. Тишина, покой и вечность. Песок меняет цвет каждую минуту 🏜️ #пустыня #песок #путешествия", "nature8")

        // --- ART ---
        addPost(PostCategories.ART, "Мария", "maria_art", "Закончила свою новую картину маслом. Работала над ней две недели, стараясь передать эмоции через цвет 🎨 #живопись #масло #искусство", "art1")
        addPost(PostCategories.ART, "Алексей", "alex_sculptor", "Моя новая скульптура из глины. Попытка запечатлеть движение в статичном материале. Как вам результат? 🗿 #скульптура #глина #творчество", "art2")
        addPost(PostCategories.ART, "Urban Sketcher", "urban_sketch", "Быстрый скетч в метро. Люблю рисовать случайных прохожих, у каждого своя история и характер ✏️ #скетч #город #люди", "art3")
        addPost(PostCategories.ART, "Digital Dreams", "digi_art", "Концепт-арт для новой игры в жанре киберпанк. Неон, дождь и высокие технологии. Будущее уже здесь 🌃 #диджитал #киберпанк #арт", "art4")
        addPost(PostCategories.ART, "Classic Soul", "classic_art", "Посетил выставку импрессионистов. Вдохновляюсь великими мастерами прошлого. Свет и цвет решают всё 🖼️ #музей #искусство #вдохновение", "art5")
        addPost(PostCategories.ART, "Street Vibe", "street_art", "Новое граффити в центре города. Уличное искусство делает серые стены живыми и говорящими 🏙️ #стритарт #граффити #город", "art6")
        addPost(PostCategories.ART, "Abstract Mind", "abstract_art", "Абстракция — это свобода. Каждый видит в этих линиях что-то свое. Что видите вы? 🎭 #абстракция #свобода #творчество", "art7")
        addPost(PostCategories.ART, "Photo Art", "photo_art", "Эксперименты со светом и длинной выдержкой. Фотография тоже может быть живописью, если правильно настроить камеру 📷 #фотоарт #свет #эксперимент", "art8")

        // --- TRAVEL ---
        addPost(PostCategories.TRAVEL, "Света", "sveta_blogger", "Париж прекрасен в любую погоду! Прогулки по Монмартру, кофе с круассаном и вид на Эйфелеву башню 🗼 #париж #франция #путешествия", "travel1")
        addPost(PostCategories.TRAVEL, "Иван", "ivan_travel", "Бали — это рай на земле. Рисовые террасы, водопады и невероятные закаты. Хочется остаться здесь навсегда 🏝️ #бали #индонезия #рай", "travel2")
        addPost(PostCategories.TRAVEL, "Mountain Hike", "hike_life", "Поднялись на высоту 3000 метров! Вид отсюда открывается просто сумасшедший. Усталость сразу прошла 🏔️ #горы #хайкинг #вершина", "travel3")
        addPost(PostCategories.TRAVEL, "City Explorer", "city_walker", "Токио ночью — это нечто! Неоновые вывески, толпы людей и атмосфера киберпанка. Город, который никогда не спит 🇯🇵 #токио #япония #город", "travel4")
        addPost(PostCategories.TRAVEL, "Desert Trip", "sahara_guide", "Ночевка в пустыне Сахара под открытым небом. Столько звезд я не видел никогда в жизни! 🐪 #сахара #пустыня #звезды", "travel5")
        addPost(PostCategories.TRAVEL, "Italy Lover", "pizza_pasta", "Улочки Рима можно исследовать бесконечно. История на каждом шагу, а какая тут еда! 🇮🇹 #рим #италия #история", "travel6")
        addPost(PostCategories.TRAVEL, "Nordic Vibes", "nordic_trip", "Фьорды Норвегии поражают своим величием. Плыть на корабле между скал — незабываемое впечатление 🚢 #норвегия #фьорды #скандинавия", "travel7")
        addPost(PostCategories.TRAVEL, "Island Life", "maldives_dream", "Мальдивы. Прозрачная вода, белый песок и полная тишина. Идеальное место для перезагрузки 🏖️ #мальдивы #море #отдых", "travel8")

        // --- TECH ---
        addPost(PostCategories.TECH, "Дима", "dima_dev", "Новый MacBook Pro просто зверь! Компилирует проект за считанные секунды. Для разработчика — идеальный инструмент 💻 #apple #macbook #dev", "tech1")
        addPost(PostCategories.TECH, "Олег", "oleg_pc", "Собрал новый ПК с кастомным водяным охлаждением. Температуры низкие, выглядит как космический корабль 🖥️ #pcbuild #gaming #tech", "tech2")
        addPost(PostCategories.TECH, "AI News", "ai_future", "Искусственный интеллект пишет код, рисует картины и сочиняет музыку. Будущее наступило быстрее, чем мы думали 🤖 #ai #chatgpt #будущее", "tech3")
        addPost(PostCategories.TECH, "Gadget Review", "tech_review", "Обзор новых умных очков. Дополненная реальность становится частью нашей жизни. Удобно для навигации! 👓 #ar #gadgets #технологии", "tech4")
        addPost(PostCategories.TECH, "Crypto World", "crypto_fan", "Блокчейн технологии меняют финансовый мир. Децентрализация — это свобода и безопасность 🔗 #crypto #blockchain #finance", "tech5")
        addPost(PostCategories.TECH, "Robot Lab", "robotics_eng", "Наш робот научился делать сальто! Динамика движений поражает. Робототехника развивается семимильными шагами 🦾 #роботы #инженерия #наука", "tech6")
        addPost(PostCategories.TECH, "Space X", "mars_colonist", "Запуск новой ракеты прошел успешно! Человечество все ближе к колонизации Марса. Вперед к звездам! 🚀 #космос #spacex #марс", "tech7")
        addPost(PostCategories.TECH, "Smart Home", "iot_master", "Настроил умный дом: свет, климат и музыка управляются голосом. Жить стало намного комфортнее 🏠 #умныйдом #iot #комфорт", "tech8")

        // --- FOOD ---
        addPost(PostCategories.FOOD, "Шеф", "chef_ivan", "Итальянская паста карбонара по классическому рецепту. Никаких сливок, только желтки и пекорино! Buon appetito! 🍝 #паста #италия #рецепт", "food1")
        addPost(PostCategories.FOOD, "Катя", "katya_sweets", "Приготовила шоколадный торт с вишней. Получился очень сочным и нежным. Кто хочет кусочек? 🎂 #торт #шоколад #десерт", "food2")
        addPost(PostCategories.FOOD, "Burger King", "burger_lover", "Самый сочный бургер в городе! Две котлеты, бекон и секретный соус. Это просто взрыв вкуса 🍔 #бургер #фастфуд #вкусно", "food3")
        addPost(PostCategories.FOOD, "Healthy Bowl", "green_life", "Поке с лососем и авокадо. Вкусно, полезно и красиво. Идеальный обед для продуктивного дня 🥗 #поке #зож #пп", "food4")
        addPost(PostCategories.FOOD, "Coffee Time", "barista_pro", "Латте-арт — это тоже искусство. Хороший кофе поднимает настроение на весь день. Всем бодрости! ☕ #кофе #латте #утро", "food5")
        addPost(PostCategories.FOOD, "Sushi Master", "sushi_roll", "Сет роллов Филадельфия и Калифорния. Свежая рыба и правильный рис — залог успеха 🍣 #суши #роллы #япония", "food6")
        addPost(PostCategories.FOOD, "Steak House", "meat_eater", "Стейк Рибай прожарки Medium Rare. Мясо тает во рту. Для настоящих ценителей 🥩 #стейк #мясо #ужин", "food7")
        addPost(PostCategories.FOOD, "Vegan Vibe", "vegan_cook", "Веганский карри с нутом и кокосовым молоком. Пряно, сытно и ни одно животное не пострадало 🌱 #веган #карри #этично", "food8")

        // --- MEMES ---
        addPost(PostCategories.MEMES, "Мемес", "memes_king", "Когда пытаешься объяснить бабушке, кем ты работаешь в IT 😂 Типичный семейный ужин", "memes1")
        addPost(PostCategories.MEMES, "Office Life", "work_memes", "Понедельник день тяжелый... Мое лицо на утреннем созвоне в 9:00 😴 #работа #офис #понедельник", "memes2")
        addPost(PostCategories.MEMES, "Cat Logic", "cat_memes", "Кот: *игнорирует дорогую лежанку*. Также кот: *спит в коробке из-под нее*. Логика вышла из чата 🐈 #коты #смешно #жиза", "memes3")
        addPost(PostCategories.MEMES, "Programmer", "dev_humor", "Исправил один баг — появилось пять новых. Программирование — это боль и страдания (и немного магии) 🐛 #код #баги #ит", "memes4")
        addPost(PostCategories.MEMES, "Student", "study_hard", "Ночь перед экзаменом: *пытаюсь выучить весь семестр за 5 часов*. Миссия невыполнима 📚 #учеба #экзамен #студент", "memes5")
        addPost(PostCategories.MEMES, "Gym Fail", "gym_memes", "Ожидание: буду качком через месяц. Реальность: все болит, не могу встать с кровати 💪 #зал #спорт #боль", "memes6")
        addPost(PostCategories.MEMES, "Introvert", "social_awkward", "Когда гости ушли и ты наконец-то можешь побыть один в тишине. Счастье есть! 🏠 #интроверт #дом #покой", "memes7")
        addPost(PostCategories.MEMES, "Shopping", "money_bye", "Зарплата пришла! ... Зарплата ушла. Куда делись деньги? Загадка века 💸 #деньги #шопинг #грустно", "memes8")

        // --- LIFESTYLE (New!) ---
        addPost(PostCategories.LIFESTYLE, "Yoga Girl", "yoga_life", "Утренняя йога на пляже — лучший способ начать день. Приветствие солнцу заряжает энергией тела и духа 🧘‍♀️ #йога #утро #баланс", "lifestyle1")
        addPost(PostCategories.LIFESTYLE, "Book Worm", "read_more", "Прочитала 50 книг за этот год! Чтение развивает воображение и позволяет прожить тысячи жизней 📚 #книги #чтение #саморазвитие", "lifestyle2")
        addPost(PostCategories.LIFESTYLE, "Minimalist", "simple_life", "Расхламил квартиру — расхламил голову. Минимализм помогает сосредоточиться на важном и убрать лишний шум 🧹 #минимализм #порядок #дом", "lifestyle3")
        addPost(PostCategories.LIFESTYLE, "Fitness Bro", "gym_rat", "День ног — это святое. Никогда не пропускайте тренировки, дисциплина бьет класс! Вперед к цели 🏋️‍♂️ #спорт #зал #мотивация", "lifestyle4")
        addPost(PostCategories.LIFESTYLE, "Meditation", "mindfulness", "10 минут медитации в день меняют жизнь. Спокойствие ума помогает принимать правильные решения 🧠 #медитация #осознанность #дзен", "lifestyle5")
        addPost(PostCategories.LIFESTYLE, "Fashionista", "style_icon", "Мой лук на сегодня. Сочетание винтажа и современных брендов. Стиль — это способ сказать, кто ты есть, не говоря ни слова 👗 #мода #стиль #аутфит", "lifestyle6")
        addPost(PostCategories.LIFESTYLE, "Productivity", "time_manage", "Техника Pomodoro реально работает! Успеваю сделать в два раза больше за то же время. Тайм-менеджмент рулит ⏱️ #продуктивность #работа #время", "lifestyle7")
        addPost(PostCategories.LIFESTYLE, "Car Lover", "auto_moto", "Ночная поездка по пустому городу под любимую музыку. Машина — это не просто транспорт, это свобода 🚗 #авто #драйв #ночь", "lifestyle8")

        posts
    }

    private val recentlyShownIds = mutableListOf<String>()
    
    override suspend fun getNextPost(): GetDataResponse<FeedPost> {
        delay(300)
        
        val profileResponse = userProfileRepository.getUserProfile()
        val profile = (profileResponse as? GetDataResponse.Success<UserProfile>)?.data ?: UserProfile()
        
        // 1. Фильтрация заблокированных
        val pool = mockPosts.filter { post ->
            val authorName = post.author.username?.value ?: ""
            post.id !in recentlyShownIds && 
            authorName !in profile.blockedAuthors.keys && 
            post.category !in profile.blockedCategories.keys
        }.ifEmpty { 
            recentlyShownIds.clear()
            // Recalculate pool after clearing history to ensure we return valid posts immediately
            mockPosts.filter { post ->
                val authorName = post.author.username?.value ?: ""
                authorName !in profile.blockedAuthors.keys && 
                post.category !in profile.blockedCategories.keys
            }
        }

        // 2. Ранжирование по весам (простая реализация для мока)
        val selectedPost = pool.maxByOrNull { post ->
            val catWeight = profile.categoryScores[post.category] ?: 0f
            val authorWeight = profile.authorScores[post.author.username?.value ?: ""] ?: 0f
            catWeight + (authorWeight * 1.5f) + Random.nextFloat() * 0.2f
        } ?: pool.random()
        
        recentlyShownIds.add(selectedPost.id)
        // Removed the limit of 10 items to allow full cycle through all mock posts
        // if (recentlyShownIds.size > 10) recentlyShownIds.removeAt(0)
        
        val reason = when {
            (profile.authorScores[selectedPost.author.username?.value ?: ""] ?: 0f) > 0.8f -> "FAVORITE"
            (profile.categoryScores[selectedPost.category] ?: 0f) > 0.6f -> "FAVORITE"
            else -> "MOCK"
        }
        
        return GetDataResponse.Success(data = FeedPost(post = selectedPost, reason = reason))
    }

    override suspend fun recordSwipe(postId: String, isLiked: Boolean): UpdateDataResponse {
        val post = mockPosts.find { it.id == postId } ?: return UpdateDataResponse.Success
        val profileResponse = userProfileRepository.getUserProfile()
        val profile = (profileResponse as? GetDataResponse.Success<UserProfile>)?.data ?: return UpdateDataResponse.Success
        
        val authorName = post.author.username?.value ?: ""
        
        // Обновление весов
        val delta = if (isLiked) WeightCalculator.LIKE_DELTA else WeightCalculator.SKIP_DELTA
        
        val newCatWeight = WeightCalculator.applyWeightLimits(
            (profile.categoryScores[post.category] ?: 0f) + WeightCalculator.calculateDelta(profile.categoryScores[post.category] ?: 0f, delta),
            false
        )
        val newAuthorWeight = WeightCalculator.applyWeightLimits(
            (profile.authorScores[authorName] ?: 0f) + WeightCalculator.calculateDelta(profile.authorScores[authorName] ?: 0f, delta),
            true
        )
        
        val updatedCategoryScores = profile.categoryScores.toMutableMap().also { it[post.category] = newCatWeight }
        val updatedAuthorScores = profile.authorScores.toMutableMap().also { it[authorName] = newAuthorWeight }
        
        // Запись рекорда
        val newRecord = SwipeRecord(
            postId = postId,
            authorUsername = authorName,
            category = post.category,
            isLiked = isLiked,
            timestamp = System.currentTimeMillis()
        )
        val newRecentSwipes = (profile.recentSwipes + newRecord).takeLast(20)
        
        // Умный анализ
        val analyzer = BehaviorAnalyzer(
            getCurrentCategoryWeight = { updatedCategoryScores[it] ?: 0f },
            getCurrentAuthorWeight = { updatedAuthorScores[it] ?: 0f }
        )
        
        val analysisResult = analyzer.analyzeRecentBehavior(newRecentSwipes)
        val newAnalysisHistory = if (analysisResult != null) {
            profile.analysisHistory + analysisResult
        } else profile.analysisHistory
        
        // Применение корректировок анализа (если есть)
        if (analysisResult != null) {
            analysisResult.corrections.forEach { (key, corr) ->
                if (key.startsWith("category_")) {
                    val catName = key.removePrefix("category_")
                    updatedCategoryScores[catName] =
                        WeightCalculator.applyWeightLimits((updatedCategoryScores[catName] ?: 0f) + corr, false)
                } else {
                    updatedAuthorScores[key] = WeightCalculator.applyWeightLimits((updatedAuthorScores[key] ?: 0f) + corr, true)
                }
            }
        }

        val updatedProfile = profile.copy(
            categoryScores = updatedCategoryScores,
            authorScores = updatedAuthorScores,
            recentSwipes = newRecentSwipes,
            totalSwipes = profile.totalSwipes + 1,
            seenCategories = profile.seenCategories + post.category,
            analysisHistory = newAnalysisHistory.takeLast(10)
        )
        
        userProfileRepository.updateUserProfile(updatedProfile)
        
        return UpdateDataResponse.Success
    }

	override suspend fun undoSwipe(postId: String): UpdateDataResponse {
		return UpdateDataResponse.Success
	}

	override fun clearSession() {
		recentlyShownIds.clear()
	}
}
