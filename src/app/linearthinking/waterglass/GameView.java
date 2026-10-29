package app.linearthinking.waterglass;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/**
 * Draws the glass (and, for the doubling round, a giant bowl around it) in world
 * coordinates measured in centimetres. y = 0 is the floor, y grows upwards.
 */
public class GameView extends View {

    public interface Listener {
        void onGuess(double guessMl);
        void onAnimationDone();
    }

    /** Glass capacity in millilitres. */
    public static final double CAP = 400.0;
    /** Inner radius of the glass in cm. */
    static final double GLASS_R = 3.0;
    /** Inner height of the glass in cm (cylinder that holds CAP ml). */
    static final double GLASS_H = CAP / (Math.PI * GLASS_R * GLASS_R);
    static final double WALL = 0.35;
    static final double PERSON_H = 175.0;

    private Listener listener;
    private boolean acceptTaps;

    private double baseMl;
    private double volumeMl;
    private double guessMl = Double.NaN;
    private double correctMarkMl = Double.NaN;

    // Doubling round state.
    private boolean bowlMode;
    private double bowlR, bowlH;
    private double zoom; // 0 = glass framing, 1 = bowl framing
    private int doublings;
    private double k; // current (continuous) number of doublings
    private boolean showCounter;

    private ValueAnimator animator;

    private final Paint glassFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint water = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint waterTop = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint floor = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guessPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint correctPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint counter = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint counterSub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bowlStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bowlFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint person = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float dp;

    public GameView(Context context) {
        super(context);
        dp = getResources().getDisplayMetrics().density;

        glassFill.setColor(0x22A0C8E8);
        glassStroke.setColor(0xFF3C465A);
        glassStroke.setStyle(Paint.Style.STROKE);
        glassStroke.setStrokeWidth(3 * dp);
        glassStroke.setStrokeCap(Paint.Cap.ROUND);
        glassStroke.setStrokeJoin(Paint.Join.ROUND);

        water.setColor(0xCC3D8FE0);
        waterTop.setColor(0xFF7DB8F0);
        waterTop.setStrokeWidth(2 * dp);

        floor.setColor(0xFFE4DED3);

        guessPaint.setColor(0xFFE03C3C);
        guessPaint.setStrokeWidth(3 * dp);
        guessPaint.setStyle(Paint.Style.STROKE);
        guessPaint.setPathEffect(new DashPathEffect(new float[]{10 * dp, 6 * dp}, 0));

        correctPaint.setColor(0xFF1E9E50);
        correctPaint.setStrokeWidth(3 * dp);

        label.setTextSize(14 * dp);
        label.setFakeBoldText(true);

        counter.setColor(0xFF1C2A40);
        counter.setTextSize(30 * dp);
        counter.setFakeBoldText(true);
        counter.setTextAlign(Paint.Align.CENTER);
        counterSub.setColor(0xFF4A5A70);
        counterSub.setTextSize(16 * dp);
        counterSub.setTextAlign(Paint.Align.CENTER);

        bowlStroke.setColor(0xFF6B5B4B);
        bowlStroke.setStyle(Paint.Style.STROKE);
        bowlStroke.setStrokeWidth(4 * dp);
        bowlStroke.setStrokeJoin(Paint.Join.ROUND);
        bowlFill.setColor(0x0A806040);

        person.setColor(0xFF555555);
        person.setStrokeWidth(3 * dp);
        person.setStrokeCap(Paint.Cap.ROUND);
        person.setStyle(Paint.Style.STROKE);

        hint.setColor(0x993C465A);
        hint.setTextSize(15 * dp);
        hint.setTextAlign(Paint.Align.CENTER);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** Reset to a glass with the start amount, waiting for a tap. */
    public void reset(double baseMl) {
        cancelAnim();
        this.baseMl = baseMl;
        volumeMl = baseMl;
        guessMl = Double.NaN;
        correctMarkMl = Double.NaN;
        bowlMode = false;
        zoom = 0;
        showCounter = false;
        acceptTaps = true;
        invalidate();
    }

    /** Linear round: fill from the start amount up to target. */
    public void animateLinear(final double targetMl) {
        cancelAnim();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1800);
        animator.setStartDelay(400);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float f = (Float) a.getAnimatedValue();
                volumeMl = baseMl + (targetMl - baseMl) * f;
                invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                volumeMl = targetMl;
                correctMarkMl = targetMl;
                invalidate();
                if (listener != null) listener.onAnimationDone();
            }
        });
        animator.start();
    }

    /** Doubling round: double the water n times, overflow into a giant bowl, zoom out. */
    public void animateDoubling(final int n) {
        cancelAnim();
        doublings = n;
        final double finalMl = baseMl * Math.pow(2, n);
        // A wide bowl (height = 0.55 * radius) that ends up about 70 % full.
        bowlR = Math.cbrt(finalMl / (0.70 * 0.55 * Math.PI));
        bowlR = Math.max(bowlR, GLASS_R * 6);
        bowlH = 0.55 * bowlR;
        bowlMode = true;
        showCounter = true;
        k = 0;

        final double kFull = Math.log(CAP / baseMl) / Math.log(2);
        final double zoomSpan = Math.max(1.5, Math.min(4.5, n - kFull - 2));

        animator = ValueAnimator.ofFloat(0f, (float) n);
        animator.setDuration(n * 600L);
        animator.setStartDelay(500);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                k = (Float) a.getAnimatedValue();
                volumeMl = baseMl * Math.pow(2, k);
                double z = (k - kFull) / zoomSpan;
                z = Math.max(0, Math.min(1, z));
                zoom = z * z * (3 - 2 * z); // smoothstep
                invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                k = doublings;
                volumeMl = finalMl;
                zoom = 1;
                invalidate();
                if (listener != null) listener.onAnimationDone();
            }
        });
        animator.start();
    }

    private void cancelAnim() {
        if (animator != null) {
            animator.removeAllListeners();
            animator.removeAllUpdateListeners();
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAnim();
        super.onDetachedFromWindow();
    }

    // ---- camera ----------------------------------------------------------------

    private float groundY() {
        return getHeight() * 0.90f;
    }

    private double scaleGlass() {
        double byH = getHeight() * 0.62 / GLASS_H;
        double byW = getWidth() * 0.42 / (2 * (GLASS_R + WALL));
        return Math.min(byH, byW);
    }

    private boolean showPerson() {
        return bowlMode && bowlH > 35;
    }

    private double scaleBowl() {
        double halfW = bowlR * 1.12;
        double needH = bowlH * 1.25;
        if (showPerson()) {
            halfW = Math.max(halfW, bowlR + 110);
            needH = Math.max(needH, PERSON_H * 1.15);
        }
        double byW = getWidth() * 0.96 / (2 * halfW);
        double byH = getHeight() * 0.62 / needH;
        return Math.min(byW, byH);
    }

    private double scale() {
        double a = scaleGlass();
        if (!bowlMode || zoom <= 0) return a;
        double b = scaleBowl();
        return Math.exp(Math.log(a) * (1 - zoom) + Math.log(b) * zoom);
    }

    private float sx(double x, double s) {
        return (float) (getWidth() / 2.0 + x * s);
    }

    private float sy(double y, double s) {
        return (float) (groundY() - y * s);
    }

    /** Height above the floor (cm) of a water surface holding ml millilitres in the glass. */
    static double yForMl(double ml) {
        return WALL + ml / CAP * (GLASS_H - WALL);
    }

    static double mlForY(double y) {
        return (y - WALL) / (GLASS_H - WALL) * CAP;
    }

    // ---- input ----------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!acceptTaps) return true;
        if (e.getAction() == MotionEvent.ACTION_UP) {
            double s = scaleGlass();
            double y = (groundY() - e.getY()) / s;
            if (y < 0) y = 0;
            // Taps above the rim count as "as if the glass were taller".
            double ml = Math.max(1, mlForY(y));
            guessMl = ml;
            acceptTaps = false;
            invalidate();
            if (listener != null) listener.onGuess(ml);
        }
        return true;
    }

    // ---- drawing --------------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xFFF7FBFF);
        double s = scale();
        float gy = groundY();
        c.drawRect(0, gy, getWidth(), getHeight(), floor);

        double bowlLevel = 0;
        if (bowlMode) {
            double inBowl = Math.max(0, volumeMl - CAP);
            bowlLevel = inBowl / (Math.PI * bowlR * bowlR);
            drawBowl(c, s, bowlLevel);
            if (showPerson()) drawPerson(c, s, bowlR + 55);
        }

        drawGlass(c, s, bowlLevel);

        if (!Double.isNaN(guessMl)) drawGuess(c, s);
        if (!Double.isNaN(correctMarkMl)) drawCorrect(c, s);

        if (acceptTaps) {
            c.drawText("tap a height", getWidth() / 2f, gy + 22 * dp, hint);
        }

        if (bowlMode && zoom > 0.6) {
            label.setColor(0xFF6B5B4B);
            label.setTextAlign(Paint.Align.CENTER);
            label.setAlpha((int) (255 * Math.min(1, (zoom - 0.6) / 0.4)));
            c.drawText("bowl: " + Fmt.length(2 * bowlR) + " wide, " + Fmt.length(bowlH) + " deep",
                    getWidth() / 2f, gy + 22 * dp, label);
            label.setAlpha(255);
        }

        if (showCounter) drawCounter(c);
    }

    private void drawGlass(Canvas c, double s, double bowlLevel) {
        float l = sx(-GLASS_R, s), r = sx(GLASS_R, s);
        float lo = sx(-GLASS_R - WALL, s), ro = sx(GLASS_R + WALL, s);
        float bottom = sy(0, s), top = sy(GLASS_H, s), inBottom = sy(WALL, s);

        rect.set(l, top, r, inBottom);
        c.drawRect(rect, glassFill);

        float wTop = sy(yForMl(Math.min(volumeMl, CAP)), s);
        if (volumeMl > 0) {
            rect.set(l, wTop, r, inBottom);
            c.drawRect(rect, water);
            c.drawLine(l, wTop, r, wTop, waterTop);
        }

        // Overflow: streams running down the outside of the glass.
        if (bowlMode && volumeMl > CAP && bowlLevel < GLASS_H) {
            float sw = (float) Math.max(2.5 * dp, 0.6 * s);
            float streamBottom = sy(bowlLevel, s);
            c.drawRect(lo - sw, top, lo, streamBottom, water);
            c.drawRect(ro, top, ro + sw, streamBottom, water);
            float bulge = (float) Math.max(2 * dp, 0.5 * s);
            rect.set(lo - sw, top - bulge, ro + sw, top + bulge);
            c.drawOval(rect, water);
        }

        float sw = (float) Math.max(1.2 * dp, Math.min(3 * dp, WALL * s));
        glassStroke.setStrokeWidth(sw);
        path.reset();
        path.moveTo(lo, top);
        path.lineTo(lo, bottom);
        path.lineTo(ro, bottom);
        path.lineTo(ro, top);
        c.drawPath(path, glassStroke);
    }

    private void drawBowl(Canvas c, double s, double level) {
        float l = sx(-bowlR, s), r = sx(bowlR, s);
        float bottom = sy(0, s), top = sy(bowlH, s);
        float corner = (float) (bowlR * 0.12 * s);

        rect.set(l, top, r, bottom);
        c.drawRect(rect, bowlFill);

        if (level > 0) {
            float wt = sy(Math.min(level, bowlH), s);
            c.save();
            path.reset();
            path.moveTo(l, top);
            path.lineTo(l, bottom - corner);
            path.quadTo(l, bottom, l + corner, bottom);
            path.lineTo(r - corner, bottom);
            path.quadTo(r, bottom, r, bottom - corner);
            path.lineTo(r, top);
            path.close();
            c.clipPath(path);
            c.drawRect(l, wt, r, bottom, water);
            c.drawLine(l, wt, r, wt, waterTop);
            c.restore();
        }

        path.reset();
        path.moveTo(l - 6 * dp, top);
        path.lineTo(l, top);
        path.lineTo(l, bottom - corner);
        path.quadTo(l, bottom, l + corner, bottom);
        path.lineTo(r - corner, bottom);
        path.quadTo(r, bottom, r, bottom - corner);
        path.lineTo(r, top);
        path.lineTo(r + 6 * dp, top);
        c.drawPath(path, bowlStroke);
    }

    private void drawPerson(Canvas c, double s, double x) {
        double h = PERSON_H;
        double head = h * 0.065;
        float cx = sx(x, s);
        float sw = (float) Math.max(1.5 * dp, Math.min(4 * dp, 3.0 * s));
        person.setStrokeWidth(sw);
        c.drawCircle(cx, sy(h - head, s), (float) (head * s), person);
        c.drawLine(cx, sy(h - 2 * head, s), cx, sy(h * 0.47, s), person);
        c.drawLine(cx, sy(h * 0.47, s), sx(x - h * 0.08, s), sy(0, s), person);
        c.drawLine(cx, sy(h * 0.47, s), sx(x + h * 0.08, s), sy(0, s), person);
        c.drawLine(sx(x - h * 0.12, s), sy(h * 0.55, s), cx, sy(h * 0.78, s), person);
        c.drawLine(sx(x + h * 0.12, s), sy(h * 0.55, s), cx, sy(h * 0.78, s), person);
        label.setColor(0xFF555555);
        label.setTextAlign(Paint.Align.CENTER);
        c.drawText("1.75 m", cx, sy(h, s) - 8 * dp, label);
    }

    private void drawGuess(Canvas c, double s) {
        double gs = scaleGlass();
        // The guess is always relative to the glass, so draw it in the glass frame scaled with the camera.
        double y = yForMl(guessMl);
        float yy = sy(y, s);
        float x0 = sx(-GLASS_R - WALL, s) - 14 * dp, x1 = sx(GLASS_R + WALL, s) + 14 * dp;
        c.drawLine(x0, yy, x1, yy, guessPaint);
        label.setColor(0xFFE03C3C);
        label.setTextAlign(Paint.Align.LEFT);
        if (s >= gs * 0.5) {
            c.drawText("your guess", x1 + 6 * dp, yy + 5 * dp, label);
        }
    }

    private void drawCorrect(Canvas c, double s) {
        double y = yForMl(Math.min(correctMarkMl, CAP));
        float yy = sy(y, s);
        float x0 = sx(-GLASS_R - WALL, s) - 14 * dp, x1 = sx(GLASS_R + WALL, s) + 14 * dp;
        c.drawLine(x0, yy, x1, yy, correctPaint);
        label.setColor(0xFF1E9E50);
        label.setTextAlign(Paint.Align.RIGHT);
        c.drawText("correct", x0 - 6 * dp, yy + 5 * dp, label);
    }

    private void drawCounter(Canvas c) {
        int step = (int) Math.floor(k + 1e-6);
        double shown = baseMl * Math.pow(2, step);
        float y = 40 * dp;
        c.drawText(Fmt.pow2(step) + " × " + Fmt.ml(baseMl), getWidth() / 2f, y, counter);
        c.drawText("= " + Fmt.volume(shown) + "  (" + Fmt.glasses(shown / CAP) + ")",
                getWidth() / 2f, y + 26 * dp, counterSub);
    }
}
