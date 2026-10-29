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
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the glass in world coordinates measured in centimetres. y = 0 is the floor,
 * y grows upwards. In the doubling round the glass is endlessly tall and the player
 * can zoom out (pinch or slider) past landmarks before tapping a height.
 */
public class GameView extends View {

    public interface Listener {
        void onGuess(double guessMl);
        void onAnimationDone();
        void onZoomChanged(double zoom);
    }

    /** Glass capacity in millilitres. */
    public static final double CAP = 400.0;
    /** Inner radius of the glass in cm. */
    static final double GLASS_R = 3.0;
    /** Inner height of the glass in cm (cylinder that holds CAP ml). */
    static final double GLASS_H = CAP / (Math.PI * GLASS_R * GLASS_R);
    static final double WALL = 0.35;
    /** Highest point the camera can show when fully zoomed out (cm). */
    static final double MAX_VIEW_CM = 1.2e6;

    private static final int PERSON = 0, HOUSE = 1, TOWER = 2, EIFFEL = 3, MOUNTAIN = 4;

    private static final class Landmark {
        final String name;
        final double heightCm;
        final int shape;

        Landmark(String name, double heightCm, int shape) {
            this.name = name;
            this.heightCm = heightCm;
            this.shape = shape;
        }
    }

    private static final Landmark[] LANDMARKS = {
            new Landmark("Person", 175, PERSON),
            new Landmark("House", 1000, HOUSE),
            new Landmark("Statue of Liberty", 9300, TOWER),
            new Landmark("Eiffel Tower", 33000, EIFFEL),
            new Landmark("Burj Khalifa", 82800, TOWER),
            new Landmark("Mont Blanc", 480800, MOUNTAIN),
            new Landmark("Mt Everest", 884900, MOUNTAIN),
    };

    private Listener listener;
    private boolean acceptTaps;

    private double baseMl;
    private double volumeMl;
    private double guessMl = Double.NaN;
    private double correctMarkMl = Double.NaN;

    /** Doubling round: the glass goes on forever and the camera can zoom out. */
    private boolean endless;
    /** 1 = glass framing; larger values show more height. */
    private double zoom = 1;
    private int doublings;
    private double k; // current (continuous) number of doublings
    private boolean showCounter;

    private ValueAnimator animator;
    private final ScaleGestureDetector scaleDetector;
    private boolean gestureWasPinch;
    private float downX, downY;

    private final Paint glassFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassDashed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint water = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint waterTop = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint floor = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guessPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint correctPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint counter = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint counterSub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ruler = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rulerText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint landmarkLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint landmarkFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint landmarkText = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        glassDashed.setColor(0x883C465A);
        glassDashed.setStyle(Paint.Style.STROKE);
        glassDashed.setStrokeWidth(1.5f * dp);
        glassDashed.setPathEffect(new DashPathEffect(new float[]{6 * dp, 6 * dp}, 0));

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
        counter.setTextSize(28 * dp);
        counter.setFakeBoldText(true);
        counter.setTextAlign(Paint.Align.CENTER);
        counterSub.setColor(0xFF4A5A70);
        counterSub.setTextSize(16 * dp);
        counterSub.setTextAlign(Paint.Align.CENTER);

        ruler.setColor(0x553C465A);
        ruler.setStrokeWidth(1 * dp);
        rulerText.setColor(0x993C465A);
        rulerText.setTextSize(11 * dp);

        landmarkLine.setColor(0xFF6E6A64);
        landmarkLine.setStyle(Paint.Style.STROKE);
        landmarkLine.setStrokeWidth(2 * dp);
        landmarkLine.setStrokeCap(Paint.Cap.ROUND);
        landmarkLine.setStrokeJoin(Paint.Join.ROUND);
        landmarkFill.setColor(0x33A09888);
        landmarkText.setColor(0xFF5A5650);
        landmarkText.setTextSize(12 * dp);
        landmarkText.setTextAlign(Paint.Align.CENTER);

        hint.setColor(0x993C465A);
        hint.setTextSize(15 * dp);
        hint.setTextAlign(Paint.Align.CENTER);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                gestureWasPinch = true;
                setZoom(zoom / d.getScaleFactor());
                return true;
            }
        });
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** Reset to a glass with the start amount, waiting for a tap. */
    public void reset(double baseMl, boolean endless) {
        cancelAnim();
        this.baseMl = baseMl;
        this.endless = endless;
        volumeMl = baseMl;
        guessMl = Double.NaN;
        correctMarkMl = Double.NaN;
        showCounter = false;
        acceptTaps = true;
        setZoom(1);
    }

    public double maxZoom() {
        return Math.max(1, MAX_VIEW_CM / viewTopAtZoom1());
    }

    public void setZoom(double z) {
        z = Math.max(1, Math.min(maxZoom(), z));
        if (z == zoom) return;
        zoom = z;
        invalidate();
        if (listener != null) listener.onZoomChanged(zoom);
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

    /** Doubling round: double the water n times; the camera follows the column up. */
    public void animateDoubling(final int n) {
        cancelAnim();
        doublings = n;
        final double finalMl = baseMl * Math.pow(2, n);
        final double vt1 = viewTopAtZoom1();
        final double guessZoom = yForMl(guessMl) * 1.25 / vt1;
        showCounter = true;
        k = 0;

        animator = ValueAnimator.ofFloat(0f, (float) n);
        animator.setDuration(n * 600L);
        animator.setStartDelay(500);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                k = (Float) a.getAnimatedValue();
                volumeMl = baseMl * Math.pow(2, k);
                // Follow the water; over the last doublings also bring the guess into view.
                double waterZoom = yForMl(volumeMl) * 1.3 / vt1;
                double t = Math.max(0, (k - 0.8 * doublings) / (0.2 * doublings));
                t = Math.min(1, t);
                double z = Math.max(waterZoom, guessZoom * t * t * (3 - 2 * t));
                zoomNoClamp(Math.max(1, z));
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                k = doublings;
                volumeMl = finalMl;
                correctMarkMl = finalMl;
                zoomNoClamp(Math.max(1, Math.max(yForMl(finalMl) * 1.3 / vt1, guessZoom)));
                if (listener != null) listener.onAnimationDone();
            }
        });
        animator.start();
    }

    private void zoomNoClamp(double z) {
        zoom = z;
        invalidate();
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

    private float topY() {
        return 84 * dp;
    }

    /** Pixels per cm at zoom 1 (the glass framing). */
    private double scaleGlass() {
        double byH = (groundY() - topY()) / (GLASS_H * 1.35);
        double byW = getWidth() * 0.30 / (2 * (GLASS_R + WALL));
        return Math.min(byH, byW);
    }

    private double viewTopAtZoom1() {
        return getHeight() == 0 ? GLASS_H * 1.35 : (groundY() - topY()) / scaleGlass();
    }

    private double scale() {
        return scaleGlass() / zoom;
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
        if (endless) scaleDetector.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                gestureWasPinch = false;
                downX = e.getX();
                downY = e.getY();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                gestureWasPinch = true;
                break;
            case MotionEvent.ACTION_UP:
                float moved = Math.abs(e.getX() - downX) + Math.abs(e.getY() - downY);
                if (gestureWasPinch || moved > 16 * dp) break;
                double y = (groundY() - e.getY()) / scale();
                if (y < 0) y = 0;
                // Above the rim counts "as if the glass were taller".
                guessMl = Math.max(1, mlForY(y));
                acceptTaps = false;
                invalidate();
                if (listener != null) listener.onGuess(guessMl);
                break;
            default:
                break;
        }
        return true;
    }

    // ---- drawing --------------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xFFF7FBFF);
        double s = scale();
        float gy = groundY();

        if (endless) {
            drawRuler(c, s);
            drawLandmarks(c, s);
        }
        c.drawRect(0, gy, getWidth(), getHeight(), floor);

        drawGlass(c, s);

        if (!Double.isNaN(guessMl)) drawMark(c, s, yForMl(guessMl), guessPaint, 0xFFE03C3C, "your guess", true);
        if (!Double.isNaN(correctMarkMl)) {
            double y = yForMl(endless ? correctMarkMl : Math.min(correctMarkMl, CAP));
            drawMark(c, s, y, correctPaint, 0xFF1E9E50, "correct", false);
        }

        if (acceptTaps) {
            c.drawText(endless ? "pinch or slide to zoom out, then tap a height" : "tap a height",
                    getWidth() / 2f, gy + 22 * dp, hint);
        }

        if (showCounter) drawCounter(c);
    }

    /** Glass edges in screen x, widened to stay visible when zoomed far out. */
    private float[] glassX(double s) {
        float cx = getWidth() / 2f;
        float half = (float) Math.max(GLASS_R * s, 3 * dp);
        float wall = (float) Math.max(WALL * s, 1.2 * dp);
        return new float[]{cx - half, cx + half, cx - half - wall, cx + half + wall};
    }

    private void drawGlass(Canvas c, double s) {
        float[] gx = glassX(s);
        float l = gx[0], r = gx[1], lo = gx[2], ro = gx[3];
        float bottom = sy(0, s), top = sy(GLASS_H, s);
        float inBottom = Math.min(sy(WALL, s), bottom - 1 * dp);

        rect.set(l, top, r, inBottom);
        c.drawRect(rect, glassFill);

        if (endless) {
            // The imagined, endlessly tall part of the glass.
            c.drawRect(l, 0, r, top, glassFill);
            c.drawLine(lo, top, lo, 0, glassDashed);
            c.drawLine(ro, top, ro, 0, glassDashed);
        }

        double ml = endless ? volumeMl : Math.min(volumeMl, CAP);
        float wTop = Math.min(sy(yForMl(ml), s), inBottom - 1 * dp);
        if (volumeMl > 0) {
            c.drawRect(l, wTop, r, inBottom, water);
            c.drawLine(l, wTop, r, wTop, waterTop);
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

    private void drawMark(Canvas c, double s, double y, Paint line, int color, String text, boolean right) {
        float yy = sy(y, s);
        float[] gx = glassX(s);
        float x0 = gx[2] - 14 * dp, x1 = gx[3] + 14 * dp;
        c.drawLine(x0, yy, x1, yy, line);
        label.setColor(color);
        String t = endless ? text + " · " + Fmt.length(y) : text;
        if (right) {
            label.setTextAlign(Paint.Align.LEFT);
            c.drawText(t, x1 + 6 * dp, yy + 5 * dp, label);
        } else {
            label.setTextAlign(Paint.Align.RIGHT);
            c.drawText(t, x0 - 6 * dp, yy + 5 * dp, label);
        }
    }

    private void drawRuler(Canvas c, double s) {
        double visible = (groundY() - topY()) / s;
        double raw = visible / 6;
        double mag = Math.pow(10, Math.floor(Math.log10(raw)));
        double step = mag;
        if (raw / mag > 5) step = 10 * mag;
        else if (raw / mag > 2) step = 5 * mag;
        else if (raw / mag > 1) step = 2 * mag;
        float x0 = 8 * dp, x1 = 18 * dp;
        rulerText.setTextAlign(Paint.Align.LEFT);
        for (double y = step; y <= visible * 1.1; y += step) {
            float yy = sy(y, s);
            c.drawLine(x0, yy, getWidth(), yy, ruler);
            c.drawText(Fmt.length(y), x1, yy - 3 * dp, rulerText);
        }
    }

    private void drawLandmarks(Canvas c, double s) {
        float minPx = 28 * dp;
        float maxPx = (groundY() - topY()) * 3f;
        List<Landmark> shown = new ArrayList<Landmark>();
        for (Landmark m : LANDMARKS) {
            double px = m.heightCm * s;
            if (px >= minPx && px <= maxPx) shown.add(m);
        }
        if (shown.isEmpty()) return;
        float left = getWidth() * 0.60f, right = getWidth() * 0.96f;
        float slot = (right - left) / shown.size();
        for (int i = 0; i < shown.size(); i++) {
            Landmark m = shown.get(i);
            float cx = left + slot * (i + 0.5f);
            float base = groundY();
            float h = (float) (m.heightCm * s);
            float top = base - h;
            drawShape(c, m.shape, cx, base, top, h, slot * 0.9f);
            float ty = Math.max(top - 6 * dp, topY() + 12 * dp);
            c.drawText(m.name, cx, ty, landmarkText);
            c.drawText(Fmt.length(m.heightCm), cx, ty + 14 * dp, landmarkText);
        }
    }

    private void drawShape(Canvas c, int shape, float cx, float base, float top, float h, float maxW) {
        path.reset();
        switch (shape) {
            case PERSON: {
                float head = h * 0.065f;
                c.drawCircle(cx, top + head, head, landmarkLine);
                c.drawLine(cx, top + 2 * head, cx, base - h * 0.47f, landmarkLine);
                c.drawLine(cx, base - h * 0.47f, cx - h * 0.08f, base, landmarkLine);
                c.drawLine(cx, base - h * 0.47f, cx + h * 0.08f, base, landmarkLine);
                c.drawLine(cx - h * 0.12f, base - h * 0.55f, cx, base - h * 0.78f, landmarkLine);
                c.drawLine(cx + h * 0.12f, base - h * 0.55f, cx, base - h * 0.78f, landmarkLine);
                return;
            }
            case HOUSE: {
                float w = Math.min(h * 0.9f, maxW) / 2;
                float eave = top + h * 0.4f;
                path.moveTo(cx - w, base);
                path.lineTo(cx - w, eave);
                path.lineTo(cx, top);
                path.lineTo(cx + w, eave);
                path.lineTo(cx + w, base);
                break;
            }
            case TOWER: {
                float w = Math.min(h * 0.12f, maxW) / 2;
                path.moveTo(cx - w, base);
                path.lineTo(cx - w * 0.5f, top + h * 0.1f);
                path.lineTo(cx, top);
                path.lineTo(cx + w * 0.5f, top + h * 0.1f);
                path.lineTo(cx + w, base);
                break;
            }
            case EIFFEL: {
                float w = Math.min(h * 0.38f, maxW) / 2;
                path.moveTo(cx - w, base);
                path.quadTo(cx - w * 0.25f, base - h * 0.45f, cx - w * 0.06f, top + h * 0.08f);
                path.lineTo(cx, top);
                path.lineTo(cx + w * 0.06f, top + h * 0.08f);
                path.quadTo(cx + w * 0.25f, base - h * 0.45f, cx + w, base);
                break;
            }
            default: { // MOUNTAIN
                float w = Math.min(h * 1.6f, maxW) / 2;
                path.moveTo(cx - w, base);
                path.lineTo(cx - w * 0.35f, top + h * 0.35f);
                path.lineTo(cx - w * 0.1f, top + h * 0.2f);
                path.lineTo(cx, top);
                path.lineTo(cx + w * 0.4f, top + h * 0.4f);
                path.lineTo(cx + w, base);
                break;
            }
        }
        path.close();
        c.drawPath(path, landmarkFill);
        c.drawPath(path, landmarkLine);
    }

    private void drawCounter(Canvas c) {
        int step = (int) Math.floor(k + 1e-6);
        double shown = baseMl * Math.pow(2, step);
        float y = 34 * dp;
        c.drawText(Fmt.pow2(step) + " × " + Fmt.ml(baseMl), getWidth() / 2f, y, counter);
        c.drawText("= " + Fmt.volume(shown) + " · " + Fmt.length(yForMl(shown)) + " high",
                getWidth() / 2f, y + 24 * dp, counterSub);
    }
}
