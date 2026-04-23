# Telegram Video Circle Reference — Extracted Code

## 1. Tap Logic (ChatMessageCell.java:3106-3113)

```java
// On ACTION_UP for round video:
if (documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND) {
    if (!MediaController.getInstance().isPlayingMessage(currentMessageObject) 
        || MediaController.getInstance().isMessagePaused()) {
        // NOT playing OR paused → play (resume or start)
        delegate.needPlayMessage(this, currentMessageObject, false);
    } else {
        // Playing and NOT paused → pause
        MediaController.getInstance().pauseMessage(currentMessageObject);
    }
}
```

**Key insight**: Telegram does NOT have an "expanded" state. Tap toggles play/pause.
There is no size change on tap — the circle is always the same size.
The only visual change is the progress ring + pause icon overlay.

## 2. Pause Progress Drawing (ChatMessageCell.java:13866-13941)

```java
public void drawRoundProgress(Canvas canvas) {
    float inset = isPlayingRound ? dp(4) : 0;
    boolean drawPause = MediaController.getInstance().isPlayingMessage(currentMessageObject) 
        && MediaController.getInstance().isMessagePaused();
    boolean drawTouchedSeekbar = drawPause && roundSeekbarTouched == 1;

    // Pause fade-in: 220ms (roundToPauseProgress += 16/220f)
    if (drawPause && roundToPauseProgress != 1f) {
        roundToPauseProgress += 16 / 220f;
        if (roundToPauseProgress > 1f) roundToPauseProgress = 1f;
        else invalidate();
    } 
    // Pause fade-out: 150ms (roundToPauseProgress -= 16/150f)
    else if (!drawPause && roundToPauseProgress != 0f) {
        roundToPauseProgress -= 16 / 150f;
        if (roundToPauseProgress < 0f) roundToPauseProgress = 0f;
        else invalidate();
    }

    // Overshoot interpolation for pause appearance
    float pauseProgress = drawPause 
        ? AndroidUtilities.overshootInterpolator.getInterpolation(roundToPauseProgress) 
        : roundToPauseProgress;

    // Inset grows by 16dp when paused (ring shrinks inward)
    inset += dp(16) * pauseProgress;

    // Shadow behind ring when pausing
    if (roundToPauseProgress > 0) {
        float r = photoImage.getImageWidth() / 2f;
        Theme.getRadialSeekbarShadowDrawable().draw(canvas, 
            photoImage.getCenterX(), photoImage.getCenterY(), r, roundToPauseProgress);
    }

    // Progress ring rect (shrinks with inset when paused)
    rect.set(photoImage.getImageX() + dp(1.5f) + inset, 
             photoImage.getImageY() + dp(1.5f) + inset,
             photoImage.getImageX2() - dp(1.5f) - inset, 
             photoImage.getImageY2() - dp(1.5f) - inset);

    // When paused: dim background circle + seek knob
    if (pauseProgress > 0) {
        float radius = rect.width() / 2f;
        // Thicker stroke when paused
        paint.setStrokeWidth(paintWidth + paintWidth * 0.5f * roundToPauseProgress);
        // Dim 30% alpha background circle
        paint.setAlpha((int) (paintAlpha * roundToPauseProgress * 0.3f));
        canvas.drawCircle(rect.centerX(), rect.centerY(), radius, paint);
        paint.setAlpha(paintAlpha);

        // Seek knob position based on audio progress
        seekbarRoundX = (float) (rect.centerX() + Math.sin(Math.toRadians(-360 * audioProgress + 180)) * radius);
        seekbarRoundY = (float) (rect.centerY() + Math.cos(Math.toRadians(-360 * audioProgress + 180)) * radius);
        // White dot: 3dp base + 5dp * pauseProgress + 3dp * seekbarTouched
        canvas.drawCircle(seekbarRoundX, seekbarRoundY, 
            dp(3) + dp(5) * pauseProgress + dp(3) * roundToPauseProgress2, seekbarPaint);
    }
}
```

**Key UI behaviors**:
- When paused: ring **shrinks inward by 16dp** (inset grows)
- When paused: **dim 30% alpha background circle** appears behind the ring
- When paused: **seek knob** (white dot) appears on the ring at current progress position
- When paused: ring stroke gets **50% thicker**
- Pause animation uses **overshoot interpolator** (bouncy entrance)
- No separate "pause icon" (❚❚) — the dim background + seek knob IS the pause indicator

## 3. Autoplay Logic (ChatActivity.java)

```java
// Only ONE round video autoplays at a time (muted)
// When a round video scrolls into view and no other is playing:
//   - Start muted autoplay loop
//   - Show progress ring
// When user taps: switch to expanded play with sound
// When user taps again: pause (show dim + seek knob)
// When user taps again: resume with sound
```

## 4. Round Video Size

```java
// Telegram: roundMessageSize = 52dp (static, never changes)
// Our app: 216dp normal, 276dp expanded — this is WRONG for Telegram parity
// Telegram circles are ALWAYS the same size, no expand/collapse animation
```

## 5. Playing Indicator (RoundVideoPlayingDrawable.java)

```java
// 3 animated bars (like an equalizer) shown at bottom-right of circle
// Each bar oscillates at slightly different speed (300ms, 310ms, 320ms)
// Only shown when video is actively playing (not paused)
// Bars are 2dp wide, 8dp tall, spaced 3dp apart
```

## Summary of What We're Doing Wrong

1. **No expand/collapse** — Telegram circles are always the same size (52dp). Our 216→276dp expand is wrong.
2. **Pause UI is wrong** — We show a ❚❚ icon. Telegram shows: dim background circle + seek knob on ring + ring shrinks inward.
3. **Missing seek knob** — Telegram has a draggable white dot on the progress ring that shows current position.
4. **Missing dim background** — When paused, Telegram draws a 30% alpha circle behind the ring.
5. **Missing ring shrink** — When paused, the progress ring inset grows by 16dp.
6. **Missing overshoot** — Pause entrance should use overshoot interpolation.
7. **Missing playing indicator** — 3 animated bars at bottom-right when playing.
