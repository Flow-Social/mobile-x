package me.floow.uikit.components.swipe

import androidx.compose.animation.core.Spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Конфигурация свайпов в стиле "Mademe".
 * Позволяет тонко настроить физику и визуальные эффекты стопки.
 */
data class SwipeConfig(
    val swipeThreshold: Float = 0.3f,         // Порог срабатывания свайпа (30% ширины)
    val swipeThresholdDown: Float = 0.2f,     // Порог срабатывания вниз (чуть меньше, чтобы легче пропустить)
    val swipeThresholdUp: Float = 0.2f,      // Порог срабатывания вверх (чуть легче, чем в бока)
    val maxRotation: Float = 15f,            // Максимальный угол наклона
    val dismissDistance: Float = 1500f,      // Дистанция, на которую улетает карточка
    
    // Пружина для возврата карточки (если не дотянул)
    val springDamping: Float = Spring.DampingRatioLowBouncy,
    val springStiffness: Float = Spring.StiffnessMediumLow,

    // Пружина для улета (эффект выстрела)
    val dismissDuration: Int = 400,          // Длительность улета в мс (если использовать tween)
    
    // Настройки стопки под основной карточкой
    val stackSpacing: Dp = 20.dp,
    val stackScaleMultiplier: Float = 0.05f,
    val stackAlphaMultiplier: Float = 0.2f,
    val maxVisibleItems: Int = 3,
    
    // Индивидуальные скейлы для каждой карточки в стопке (index 1, 2, 3...)
    val stackCardScales: List<Float> = listOf(0.95f, 0.85f, 0.75f),

    // Индивидуальные альфа-значения (затенение) для каждой карточки (index 1, 2, 3...)
    // Если список пуст, используется stackAlphaMultiplier
    val stackCardAlphas: List<Float> = emptyList(),
    
    // Индивидуальные повороты для каждой карточки в стопке (в градусах)
    val stackCardRotations: List<Float> = listOf(-1f, 3f, -1f),

    // Индивидуальные отступы для каждой карточки (index 1, 2, 3...)
    // Если список пуст, используется index * stackSpacing
    val stackCardOffsets: List<Dp> = emptyList(),
    
    val hapticEnabled: Boolean = true,

    // Физика сопротивления (1.0 - свободный ход, 0.1 - очень тяжело)
    val resistanceHorizontal: Float = 0.8f,
    val resistanceVerticalUp: Float = 0.6f,
    val resistanceVerticalDown: Float = 0.4f 
)

object SwipePresets {
    val Juicy = SwipeConfig()
    
    val Performance = SwipeConfig(
        springDamping = Spring.DampingRatioNoBouncy,
        stackAlphaMultiplier = 0.2f,
        maxVisibleItems = 3
    )
    
    val BottomStack = SwipeConfig(
        stackSpacing = 40.dp,                    // Расстояние между карточками
        stackScaleMultiplier = 0.12f,            // Увеличиваем уменьшение размера для более заметной разницы
        stackAlphaMultiplier = 0f,               // НЕ используем прозрачность (можно настроить в stackCardAlphas)
        maxVisibleItems = 3,
        
        // Индивидуальные скейлы для каждой карточки
        stackCardScales = listOf(
            0.90f,  // Карточка 1 (средняя) - 92% от основной
            0.80f   // Карточка 2 (нижняя) - 84% от основной
        ),
        
        // Индивидуальные повороты для каждой карточки
        stackCardRotations = listOf(
            -0.1f,  // Карточка 1 (средняя) - очень легкий поворот влево
            0.0f    // Карточка 2 (нижняя) - очень легкий поворот вправо
        ),
        
        // Индивидуальные отступы (сделали первый отступ чуть больше - 36dp вместо 30dp)
        stackCardOffsets = listOf(
            70.dp, 
            120.dp
        ),

        // Индивидуальная прозрачность (если нужно затемнение)
        stackCardAlphas = listOf(
            0.1f,
            0.2f
        ),
        
        springDamping = Spring.DampingRatioLowBouncy,
        springStiffness = Spring.StiffnessMediumLow,
        
        // Настройки свайпа остаются стандартными
        swipeThreshold = 0.3f,
        maxRotation = 15f
    )
}
