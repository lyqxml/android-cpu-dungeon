package com.lyq.cpudungeon.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.lyq.cpudungeon.Settings;
import com.lyq.cpudungeon.engine.Enemy;
import com.lyq.cpudungeon.engine.Pickup;
import com.lyq.cpudungeon.engine3d.Game3D;
import com.lyq.cpudungeon.engine3d.Level3D;

import java.util.ArrayList;

/**
 * Software-canvas HUD: health, cores, minimap (height-shaded), crosshair,
 * procedural blaster and touch controls including jump/crouch.
 */
public final class Hud {
    public static final int MODE_PLAY = 0;
    public static final int MODE_SETTINGS = 1;
    public static final int MODE_LAYOUT = 2;

    private static final int TEAL = 0xFF25E0C8;
    private static final int TEAL_SOFT = 0xFF8CF0E2;
    private static final int MAGENTA = 0xFFFF3E8A;
    private static final int AMBER = 0xFFFFB020;
    private static final int RED = 0xFFFF3E5E;
    private static final int TEXT = 0xFFE6F7FF;
    private static final int DIM = 0xFF8FB6C8;
    private static final int PANEL = 0x99070C14;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private Settings settings;

    public final RectF settingsRect = new RectF();
    public final RectF sensMinus = new RectF();
    public final RectF sensPlus = new RectF();
    public final RectF layoutBtn = new RectF();
    public final RectF closeBtn = new RectF();
    public final RectF doneBtn = new RectF();
    public final RectF resetBtn = new RectF();
    private final RectF panel = new RectF();

    private Shader damageShader;
    private int shaderW = -1;
    private int shaderH = -1;
    private final ArrayList<Popup> popups = new ArrayList<>();

    private static final class Popup {
        String text;
        int color;
        float x;
        float y;
        float speed;
        float age;
        float life;
    }

    private Bitmap mapCache;
    private Level3D cacheLevel;
    private int cacheSize = -1;
    private int screenW = 1920;
    private int screenH = 1080;
    private float d = 3f;

    public float joyCx;
    public float joyCy;
    public float joyR;
    public float fireCx;
    public float fireCy;
    public float fireR;
    public float jumpCx;
    public float jumpCy;
    public float jumpR;
    public float crouchCx;
    public float crouchCy;
    public float crouchR;
    public float knobX;
    public float knobY;

    public Hud() {
        textPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        shadowPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    public void layout(int w, int h, float density) {
        screenW = w;
        screenH = h;
        d = density;
        float fr = settings != null ? settings.fireRight : Settings.DEFAULT_FIRE_RIGHT;
        float fb = settings != null ? settings.fireBottom : Settings.DEFAULT_FIRE_BOTTOM;
        float jr = settings != null ? settings.jumpRight : Settings.DEFAULT_JUMP_RIGHT;
        float jb = settings != null ? settings.jumpBottom : Settings.DEFAULT_JUMP_BOTTOM;
        float cr = settings != null ? settings.crouchRight : Settings.DEFAULT_CROUCH_RIGHT;
        float cb = settings != null ? settings.crouchBottom : Settings.DEFAULT_CROUCH_BOTTOM;

        joyR = 62f * d;
        joyCx = 82f * d;
        joyCy = h - 82f * d;
        fireR = 44f * d;
        fireCx = clamp(w - fr * d, fireR + 12f * d, w - fireR - 12f * d);
        fireCy = clamp(h - fb * d, fireR + 12f * d, h - fireR - 12f * d);
        jumpR = 32f * d;
        jumpCx = clamp(w - jr * d, jumpR + 12f * d, w - jumpR - 12f * d);
        jumpCy = clamp(h - jb * d, jumpR + 12f * d, h - jumpR - 12f * d);
        crouchR = 29f * d;
        crouchCx = clamp(w - cr * d, crouchR + 12f * d, w - crouchR - 12f * d);
        crouchCy = clamp(h - cb * d, crouchR + 12f * d, h - crouchR - 12f * d);

        settingsRect.set(20f * d, 96f * d, 82f * d, 124f * d);

        float pw = Math.min(w * 0.72f, 640f * d);
        float ph = Math.min(h * 0.74f, 300f * d);
        float left = w * 0.5f - pw * 0.5f;
        float top = h * 0.5f - ph * 0.5f;
        panel.set(left, top, left + pw, top + ph);

        float sensY = top + ph * 0.30f;
        float minusCx = left + pw - 158f * d;
        float plusCx = left + pw - 44f * d;
        sensMinus.set(minusCx - 22f * d, sensY - 22f * d, minusCx + 22f * d, sensY + 22f * d);
        sensPlus.set(plusCx - 22f * d, sensY - 22f * d, plusCx + 22f * d, sensY + 22f * d);

        float layoutY = top + ph * 0.48f;
        layoutBtn.set(w * 0.5f - 110f * d, layoutY - 20f * d,
                w * 0.5f + 110f * d, layoutY + 20f * d);

        float bottomY = top + ph - ph * 0.12f;
        closeBtn.set(w * 0.5f - 76f * d, bottomY - 18f * d,
                w * 0.5f + 76f * d, bottomY + 18f * d);
        resetBtn.set(left + 24f * d, bottomY - 18f * d,
                left + 148f * d, bottomY + 18f * d);
        doneBtn.set(left + pw - 148f * d, bottomY - 18f * d,
                left + pw - 24f * d, bottomY + 18f * d);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    public void draw(Canvas c, Game3D g, float fps, int fbW, int fbH, int best, int mode) {
        drawDamageFlash(c, g);
        drawWeapon(c, g);
        drawStatus(c, g);
        drawPerf(c, g, fps, fbW, fbH, best);
        drawMinimap(c, g);
        drawJoystick(c, g);
        drawJump(c, g, mode);
        drawCrouch(c, g);
        drawFireButton(c, g);
        drawCrosshair(c, g);
        drawMessages(c, g);
        drawPopups(c);
        drawOverlay(c, g, best);
        if (mode == MODE_PLAY) {
            drawSettingsButton(c);
        } else if (mode == MODE_SETTINGS) {
            drawSettingsPanel(c, g);
        } else {
            drawLayoutMode(c);
        }
    }

    private void drawDamageFlash(Canvas c, Game3D g) {
        if (g.player.damageFlash <= 0f) {
            return;
        }
        if (damageShader == null || shaderW != screenW || shaderH != screenH) {
            float radius = Math.max(screenW, screenH) * 0.72f;
            damageShader = new RadialGradient(screenW * 0.5f, screenH * 0.5f, radius,
                    new int[]{0x00FF1030, 0x00FF1030, 0x55FF1030, 0xCCFF1030},
                    new float[]{0f, 0.52f, 0.82f, 1f}, Shader.TileMode.CLAMP);
            shaderW = screenW;
            shaderH = screenH;
        }
        fill.setStyle(Paint.Style.FILL);
        fill.setShader(damageShader);
        fill.setAlpha((int) (255f * Math.min(1f, g.player.damageFlash)));
        c.drawRect(0, 0, screenW, screenH, fill);
        fill.setShader(null);
        fill.setAlpha(255);
    }

    public void addPopup(String text, int color) {
        Popup p = new Popup();
        p.text = text;
        p.color = color;
        p.x = screenW * 0.5f + ((float) Math.random() - 0.5f) * 90f * d;
        p.y = screenH * 0.47f + ((float) Math.random() - 0.5f) * 26f * d;
        p.speed = 42f * d;
        p.life = 1.0f;
        popups.add(p);
        if (popups.size() > 24) {
            popups.remove(0);
        }
    }

    public void update(float dt) {
        for (int i = popups.size() - 1; i >= 0; i--) {
            Popup p = popups.get(i);
            p.age += dt;
            p.y -= p.speed * dt;
            p.speed *= 0.94f;
            if (p.age >= p.life) {
                popups.remove(i);
            }
        }
    }

    private void drawPopups(Canvas c) {
        for (Popup p : popups) {
            float t = p.age / p.life;
            float alpha = t < 0.65f ? 1f : 1f - (t - 0.65f) / 0.35f;
            float size = (1f + 0.18f * (1f - Math.min(1f, t * 4f))) * 17f * d;
            label(c, p.text, p.x, p.y, size, colorWithAlpha(p.color, alpha), Paint.Align.CENTER);
        }
    }

    private void drawWeapon(Canvas c, Game3D g) {
        float bob = (float) Math.sin(g.player.bob) * 6f * d * g.player.moveSpeed;
        float recoil = g.player.recoil;
        float scale = Math.min(1.0f, screenH / (440f * d));

        c.save();
        c.translate(screenW * 0.5f + bob, screenH + 6f * d + recoil * 14f * d);
        c.scale(scale, scale);

        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0x2A25E0C8);
        c.drawCircle(0f, -96f * d, 44f * d, fill);

        path.reset();
        path.moveTo(-46f * d, -6f * d);
        path.lineTo(-86f * d, -38f * d);
        path.lineTo(-56f * d, -66f * d);
        path.lineTo(-38f * d, -40f * d);
        path.close();
        fill.setColor(0xFF111A24);
        c.drawPath(path, fill);
        path.reset();
        path.moveTo(46f * d, -6f * d);
        path.lineTo(86f * d, -38f * d);
        path.lineTo(56f * d, -66f * d);
        path.lineTo(38f * d, -40f * d);
        path.close();
        c.drawPath(path, fill);

        path.reset();
        path.moveTo(-44f * d, 4f * d);
        path.lineTo(-38f * d, -72f * d);
        path.lineTo(-22f * d, -106f * d);
        path.lineTo(-15f * d, -152f * d);
        path.lineTo(15f * d, -152f * d);
        path.lineTo(22f * d, -106f * d);
        path.lineTo(38f * d, -72f * d);
        path.lineTo(44f * d, 4f * d);
        path.close();
        fill.setColor(0xFF18222E);
        c.drawPath(path, fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.6f * d);
        stroke.setColor(0xFF3A546C);
        c.drawPath(path, stroke);

        rect.set(-13f * d, -176f * d, 13f * d, -140f * d);
        fill.setColor(0xFF0D151E);
        c.drawRoundRect(rect, 3f * d, 3f * d, fill);
        stroke.setColor(0xFF2C4256);
        c.drawRoundRect(rect, 3f * d, 3f * d, stroke);
        rect.set(-17f * d, -152f * d, 17f * d, -146f * d);
        fill.setColor(0xFF253646);
        c.drawRect(rect, fill);

        rect.set(-7f * d, -136f * d, 7f * d, -76f * d);
        fill.setColor(0xFF0A2C2C);
        c.drawRoundRect(rect, 3f * d, 3f * d, fill);
        rect.set(-4.5f * d, -132f * d, 4.5f * d, -80f * d);
        fill.setColor(g.cheat ? MAGENTA : TEAL);
        c.drawRoundRect(rect, 2.5f * d, 2.5f * d, fill);
        rect.set(-1.8f * d, -128f * d, 1.8f * d, -84f * d);
        fill.setColor(0xFFEAFFFB);
        c.drawRoundRect(rect, 1f * d, 1f * d, fill);

        stroke.setColor(g.cheat ? AMBER : MAGENTA);
        stroke.setStrokeWidth(2f * d);
        c.drawLine(-30f * d, -58f * d, -16f * d, -70f * d, stroke);
        c.drawLine(30f * d, -58f * d, 16f * d, -70f * d, stroke);

        float muzzle = g.player.muzzle;
        if (muzzle > 0.02f) {
            int a1 = (int) (120 * muzzle);
            fill.setColor((a1 << 24) | 0x0025E0C8);
            c.drawCircle(0f, -186f * d, 40f * d * muzzle, fill);
            int a2 = (int) (220 * muzzle);
            fill.setColor((a2 << 24) | 0x00EAFFFB);
            c.drawCircle(0f, -186f * d, 15f * d * muzzle, fill);
            stroke.setColor(((int) (200 * muzzle) << 24) | 0x007CE7FF);
            stroke.setStrokeWidth(2f * d);
            c.drawLine(-30f * d * muzzle, -186f * d, 30f * d * muzzle, -186f * d, stroke);
            c.drawLine(0f, -216f * d * muzzle, 0f, -156f * d, stroke);
        }
        c.restore();
    }

    private void drawStatus(Canvas c, Game3D g) {
        float x = 20f * d;
        float y = 28f * d;
        label(c, "INTEGRITY", x, y, 10f * d, DIM, Paint.Align.LEFT);

        float hpF = Math.max(0f, Math.min(1f, g.player.health / g.player.maxHealth));
        float barW = 146f * d;
        float barH = 8f * d;
        float barY = y + 8f * d;
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(PANEL);
        c.drawRoundRect(x, barY, x + barW, barY + barH, 4f * d, 4f * d, fill);
        int hpColor = hpF > 0.55f ? TEAL : (hpF > 0.3f ? AMBER : RED);
        fill.setColor(hpColor);
        c.drawRoundRect(x, barY, x + Math.max(barH, barW * hpF), barY + barH, 4f * d, 4f * d, fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1f * d);
        stroke.setColor(0x5525E0C8);
        c.drawRoundRect(x, barY, x + barW, barY + barH, 4f * d, 4f * d, stroke);
        label(c, Integer.toString((int) Math.ceil(g.player.health)), x + barW + 12f * d,
                barY + barH, 12f * d, TEXT, Paint.Align.LEFT);

        float coreY = barY + 26f * d;
        for (int i = 0; i < g.coresTotal; i++) {
            float cx = x + 7f * d + i * 16f * d;
            path.reset();
            path.moveTo(cx, coreY - 5f * d);
            path.lineTo(cx + 5f * d, coreY);
            path.lineTo(cx, coreY + 5f * d);
            path.lineTo(cx - 5f * d, coreY);
            path.close();
            fill.setColor(i < g.coresGot ? TEAL : 0x55345060);
            c.drawPath(path, fill);
            if (i < g.coresGot) {
                fill.setColor(0x88EAFFFB);
                c.drawCircle(cx, coreY, 1.6f * d, fill);
            }
        }
        label(c, "CORES " + g.coresGot + "/" + g.coresTotal, x, coreY + 22f * d,
                10f * d, g.coresGot >= g.coresTotal ? TEAL : DIM, Paint.Align.LEFT);
    }

    private void drawPerf(Canvas c, Game3D g, float fps, int fbW, int fbH, int best) {
        label(c, "SECTOR " + two(g.levelIndex), screenW * 0.5f, 30f * d, 15f * d, TEXT, Paint.Align.CENTER);
        label(c, "SCORE " + g.score + "    BEST " + Math.max(best, g.score), screenW * 0.5f,
                50f * d, 10f * d, DIM, Paint.Align.CENTER);
        label(c, "CPU " + fbW + "x" + fbH + "  " + Math.round(fps) + " FPS  [720p FIXED]",
                screenW * 0.5f, 66f * d, 9f * d, 0x9925E0C8, Paint.Align.CENTER);
        if (g.cheat) {
            float pulse = 0.6f + 0.4f * (float) Math.abs(Math.sin(g.time * 6f));
            label(c, "OVERCLOCK // XRAY // SPRINT", screenW * 0.5f, 86f * d, 12f * d,
                    colorWithAlpha(MAGENTA, pulse), Paint.Align.CENTER);
        }
    }

    private void drawMinimap(Canvas c, Game3D g) {
        float size = Math.min(112f * d, Math.min(screenW, screenH) * 0.30f);
        int px = Math.max(32, (int) size);
        float left = screenW - 16f * d - px;
        float top = 16f * d;
        ensureMapCache(g, px);

        fill.setStyle(Paint.Style.FILL);
        fill.setColor(PANEL);
        c.drawRoundRect(left - 5f * d, top - 5f * d, left + px + 5f * d, top + px + 5f * d,
                6f * d, 6f * d, fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.4f * d);
        stroke.setColor(0x6625E0C8);
        c.drawRoundRect(left - 5f * d, top - 5f * d, left + px + 5f * d, top + px + 5f * d,
                6f * d, 6f * d, stroke);

        if (mapCache != null) {
            c.drawBitmap(mapCache, left, top, null);
        }
        float cell = px / (float) g.level.w;
        c.save();
        c.clipRect(left, top, left + px, top + px);

        fill.setStyle(Paint.Style.FILL);
        for (Pickup p : g.pickups) {
            if (p.taken) {
                continue;
            }
            fill.setColor(0xDD25E0C8);
            c.drawCircle(left + p.x * cell, top + p.y * cell, 2.1f * d, fill);
        }
        for (Enemy e : g.enemies) {
            if (e.dead) {
                continue;
            }
            fill.setColor(e.alert > 0.3f ? 0xFFFF3E5E : 0xAAFF8A9E);
            c.drawCircle(left + e.x * cell, top + e.y * cell, 2.3f * d, fill);
        }
        int exitColor = g.exitActive ? TEAL : 0xFFB26BFF;
        fill.setColor(exitColor);
        float ex = left + (g.level.exitX + 0.5f) * cell;
        float ey = top + (g.level.exitY + 0.5f) * cell;
        c.drawRect(ex - 3f * d, ey - 3f * d, ex + 3f * d, ey + 3f * d, fill);

        c.save();
        c.translate(left + g.player.x * cell, top + g.player.y * cell);
        c.rotate((float) Math.toDegrees(g.player.yaw));
        path.reset();
        path.moveTo(6f * d, 0f);
        path.lineTo(-4f * d, 3.6f * d);
        path.lineTo(-4f * d, -3.6f * d);
        path.close();
        fill.setColor(0xFFB8F7FF);
        c.drawPath(path, fill);
        c.restore();
        c.restore();
    }

    private void ensureMapCache(Game3D g, int size) {
        if (mapCache != null && cacheLevel == g.level && cacheSize == size) {
            return;
        }
        if (mapCache != null) {
            mapCache.recycle();
        }
        mapCache = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas mc = new Canvas(mapCache);
        float cell = size / (float) g.level.w;
        fill.setStyle(Paint.Style.FILL);
        for (int y = 0; y < g.level.h; y++) {
            for (int x = 0; x < g.level.w; x++) {
                float x0 = x * cell;
                float y0 = y * cell;
                if (g.level.solid(x, y)) {
                    fill.setColor(0xAA31485E);
                } else {
                    // higher floors glow a little brighter
                    int height = Math.round(g.level.floorZ[y * g.level.w + x] * 4f);
                    int alpha = 0x18 + Math.min(0x28, height * 0x0A);
                    fill.setColor((alpha << 24) | 0x0025E0C8);
                }
                mc.drawRect(x0, y0, x0 + cell + 0.4f, y0 + cell + 0.4f, fill);
            }
        }
        cacheLevel = g.level;
        cacheSize = size;
    }

    private void drawJoystick(Canvas c, Game3D g) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0x2A101828);
        c.drawCircle(joyCx, joyCy, joyR, fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.6f * d);
        stroke.setColor(0x5525E0C8);
        c.drawCircle(joyCx, joyCy, joyR, stroke);
        stroke.setStrokeWidth(1f * d);
        stroke.setColor(0x3325E0C8);
        c.drawLine(joyCx - joyR, joyCy, joyCx - joyR * 0.7f, joyCy, stroke);
        c.drawLine(joyCx + joyR, joyCy, joyCx + joyR * 0.7f, joyCy, stroke);
        c.drawLine(joyCx, joyCy - joyR, joyCx, joyCy - joyR * 0.7f, stroke);
        c.drawLine(joyCx, joyCy + joyR, joyCx, joyCy + joyR * 0.7f, stroke);

        float kx = joyCx + knobX * joyR * 0.60f;
        float ky = joyCy + knobY * joyR * 0.60f;
        fill.setColor(0xCC123A42);
        c.drawCircle(kx, ky, joyR * 0.34f, fill);
        stroke.setStrokeWidth(2f * d);
        stroke.setColor(0xAA7CE7FF);
        c.drawCircle(kx, ky, joyR * 0.34f, stroke);
        fill.setColor(0xAA7CE7FF);
        c.drawCircle(joyCx, joyCy, 2.2f * d, fill);
    }

    private void drawJump(Canvas c, Game3D g, int mode) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(g.player.grounded ? 0x2225E0C8 : 0x3A25E0C8);
        c.drawCircle(jumpCx, jumpCy, jumpR, fill);
        if (mode == MODE_LAYOUT) {
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(3f * d);
            stroke.setColor(0xFFFFE27A);
            c.drawCircle(jumpCx, jumpCy, jumpR + 6f * d, stroke);
        }
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f * d);
        stroke.setColor(0xAA25E0C8);
        c.drawCircle(jumpCx, jumpCy, jumpR, stroke);
        path.reset();
        path.moveTo(jumpCx, jumpCy - 13f * d);
        path.lineTo(jumpCx + 10f * d, jumpCy - 1f * d);
        path.lineTo(jumpCx + 4f * d, jumpCy - 1f * d);
        path.lineTo(jumpCx + 4f * d, jumpCy + 11f * d);
        path.lineTo(jumpCx - 4f * d, jumpCy + 11f * d);
        path.lineTo(jumpCx - 4f * d, jumpCy - 1f * d);
        path.lineTo(jumpCx - 10f * d, jumpCy - 1f * d);
        path.close();
        fill.setColor(g.player.grounded ? 0xFFB8F7FF : 0x887CE7FF);
        c.drawPath(path, fill);
        label(c, "JUMP", jumpCx, jumpCy + jumpR + 12f * d, 9f * d, 0x9925E0C8, Paint.Align.CENTER);
    }

    private void drawCrouch(Canvas c, Game3D g) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(g.player.crouch ? 0x44FFB020 : 0x22FFB020);
        c.drawCircle(crouchCx, crouchCy, crouchR, fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f * d);
        stroke.setColor(0xAAFFB020);
        c.drawCircle(crouchCx, crouchCy, crouchR, stroke);
        path.reset();
        path.moveTo(crouchCx, crouchCy + 12f * d);
        path.lineTo(crouchCx + 10f * d, crouchCy);
        path.lineTo(crouchCx + 4f * d, crouchCy);
        path.lineTo(crouchCx + 4f * d, crouchCy - 11f * d);
        path.lineTo(crouchCx - 4f * d, crouchCy - 11f * d);
        path.lineTo(crouchCx - 4f * d, crouchCy);
        path.lineTo(crouchCx - 10f * d, crouchCy);
        path.close();
        fill.setColor(g.player.crouch ? 0xFFFFE2A8 : 0x99FFD080);
        c.drawPath(path, fill);
        label(c, "CROUCH", crouchCx, crouchCy + crouchR + 12f * d, 9f * d, 0x99FFB020, Paint.Align.CENTER);
    }

    private void drawFireButton(Canvas c, Game3D g) {
        float interval = g.cheat ? Game3D.CHEAT_FIRE_INTERVAL : Game3D.FIRE_INTERVAL;
        float frac = Math.max(0f, Math.min(1f, g.player.fireCooldown / interval));
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(frac > 0f ? 0x22FF3E8A : 0x3AFF3E8A);
        c.drawCircle(fireCx, fireCy, fireR, fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f * d);
        stroke.setColor(0xAAFF3E8A);
        c.drawCircle(fireCx, fireCy, fireR, stroke);

        if (frac > 0f) {
            rect.set(fireCx - fireR - 5f * d, fireCy - fireR - 5f * d,
                    fireCx + fireR + 5f * d, fireCy + fireR + 5f * d);
            stroke.setStrokeWidth(3f * d);
            stroke.setColor(0xCCFFB020);
            c.drawArc(rect, -90f, 360f * frac, false, stroke);
        }

        path.reset();
        path.moveTo(fireCx + 5f * d, fireCy - 18f * d);
        path.lineTo(fireCx - 12f * d, fireCy + 3f * d);
        path.lineTo(fireCx - 2f * d, fireCy + 3f * d);
        path.lineTo(fireCx - 5f * d, fireCy + 19f * d);
        path.lineTo(fireCx + 12f * d, fireCy - 3f * d);
        path.lineTo(fireCx + 2f * d, fireCy - 3f * d);
        path.close();
        fill.setColor(0xFFE8D8E4);
        c.drawPath(path, fill);
    }

    private void drawCrosshair(Canvas c, Game3D g) {
        float cx = screenW * 0.5f;
        float cy = screenH * 0.5f;
        float gap = (5f + g.player.recoil * 6f) * d;
        float len = 7f * d;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.8f * d);
        stroke.setColor(0xCC8CF0FF);
        c.drawLine(cx - gap - len, cy, cx - gap, cy, stroke);
        c.drawLine(cx + gap, cy, cx + gap + len, cy, stroke);
        c.drawLine(cx, cy - gap - len, cx, cy - gap, stroke);
        c.drawLine(cx, cy + gap, cx, cy + gap + len, stroke);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(g.cheat ? MAGENTA : 0xFF25E0C8);
        c.drawCircle(cx, cy, 1.7f * d, fill);
    }

    private void drawMessages(Canvas c, Game3D g) {
        if (g.toastTimer > 0f && g.toast.length() > 0) {
            float a = Math.min(1f, g.toastTimer / 0.7f);
            int color = colorWithAlpha(g.cheat ? MAGENTA : TEAL, 0.55f + 0.45f * a);
            label(c, g.toast, screenW * 0.5f, screenH * 0.30f, 22f * d, color, Paint.Align.CENTER);
        }
        if (g.hintTimer > 0f && g.hint.length() > 0) {
            float a = Math.min(1f, g.hintTimer / 0.8f);
            label(c, g.hint, screenW * 0.5f, 100f * d, 10f * d,
                    colorWithAlpha(TEAL_SOFT, 0.85f * a), Paint.Align.CENTER);
        }
    }

    private void drawOverlay(Canvas c, Game3D g, int best) {
        if (g.state == Game3D.State.DEAD) {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0xD004060C);
            c.drawRect(0, 0, screenW, screenH, fill);
            label(c, "SIGNAL LOST", screenW * 0.5f, screenH * 0.44f, 30f * d, RED, Paint.Align.CENTER);
            label(c, "SCORE " + g.score + "   SECTOR " + two(g.levelIndex),
                    screenW * 0.5f, screenH * 0.52f, 12f * d, DIM, Paint.Align.CENTER);
            float blink = 0.45f + 0.55f * (float) Math.abs(Math.sin(g.stateTimer * 3.2f));
            label(c, "TAP TO REBOOT", screenW * 0.5f, screenH * 0.60f, 16f * d,
                    colorWithAlpha(TEAL, blink), Paint.Align.CENTER);
            label(c, "BEST " + Math.max(best, g.score), screenW * 0.5f, screenH * 0.66f,
                    10f * d, 0x6625E0C8, Paint.Align.CENTER);
        } else if (g.state == Game3D.State.CLEARED) {
            float a = Math.min(1f, g.stateTimer * 3.5f);
            int band = (int) (90 * Math.min(1f, g.stateTimer * 2f));
            fill.setColor((band << 24) | 0x00103028);
            c.drawRect(0, 0, screenW, screenH, fill);
            label(c, "SECTOR CLEAR", screenW * 0.5f, screenH * 0.45f, 28f * d,
                    colorWithAlpha(TEAL, a), Paint.Align.CENTER);
            label(c, "+" + (400 + g.levelIndex * 50), screenW * 0.5f, screenH * 0.53f,
                    14f * d, colorWithAlpha(TEXT, a), Paint.Align.CENTER);
            label(c, "DESCENDING...", screenW * 0.5f, screenH * 0.60f, 10f * d,
                    colorWithAlpha(DIM, a), Paint.Align.CENTER);
        } else if (g.levelIndex == 1 && g.time < 2.4f) {
            float a = Math.max(0f, Math.min(1f, (2.4f - g.time) / 0.9f));
            label(c, "CPU DUNGEON", screenW * 0.5f, screenH * 0.40f, 32f * d,
                    colorWithAlpha(TEAL, a), Paint.Align.CENTER);
            label(c, "TRUE 3D  /  SOFTWARE RASTERIZER  /  ZERO GPU WORK", screenW * 0.5f,
                    screenH * 0.47f, 10f * d, colorWithAlpha(DIM, a), Paint.Align.CENTER);
        }
    }

    private void drawSettingsButton(Canvas c) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0x88070C14);
        c.drawRoundRect(settingsRect, 6f * d, 6f * d, fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.2f * d);
        stroke.setColor(0x5525E0C8);
        c.drawRoundRect(settingsRect, 6f * d, 6f * d, stroke);
        label(c, "SET", settingsRect.centerX(), settingsRect.centerY() + 4f * d,
                9f * d, TEAL_SOFT, Paint.Align.CENTER);
    }

    private void drawSettingsPanel(Canvas c, Game3D g) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xCC04060C);
        c.drawRect(0, 0, screenW, screenH, fill);

        fill.setColor(0xF0070C14);
        c.drawRoundRect(panel, 10f * d, 10f * d, fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.8f * d);
        stroke.setColor(0x8825E0C8);
        c.drawRoundRect(panel, 10f * d, 10f * d, stroke);

        label(c, "SETTINGS", panel.centerX(), panel.top + panel.height() * 0.13f,
                18f * d, TEXT, Paint.Align.CENTER);
        label(c, "LOOK SENSITIVITY", panel.left + 28f * d, panel.top + panel.height() * 0.30f + 5f * d,
                10f * d, DIM, Paint.Align.LEFT);

        float sens = settings != null ? settings.lookSensitivity : Settings.DEFAULT_SENSITIVITY;
        int tenths = Math.round(sens * 10f);
        label(c, (tenths / 10) + "." + (tenths % 10) + "x",
                panel.left + panel.width() * 0.5f + 52f * d,
                panel.top + panel.height() * 0.30f + 6f * d, 15f * d, TEAL, Paint.Align.CENTER);

        drawRoundButton(c, sensMinus, "-");
        drawRoundButton(c, sensPlus, "+");

        fill.setColor(0x3325E0C8);
        c.drawRoundRect(layoutBtn, 8f * d, 8f * d, fill);
        stroke.setColor(0xAA25E0C8);
        c.drawRoundRect(layoutBtn, 8f * d, 8f * d, stroke);
        label(c, "CUSTOM TOUCH LAYOUT", layoutBtn.centerX(), layoutBtn.centerY() + 4f * d,
                10f * d, TEXT, Paint.Align.CENTER);

        fill.setColor(0x33FF3E8A);
        c.drawRoundRect(closeBtn, 8f * d, 8f * d, fill);
        stroke.setColor(0xAAFF3E8A);
        c.drawRoundRect(closeBtn, 8f * d, 8f * d, stroke);
        label(c, "CLOSE", closeBtn.centerX(), closeBtn.centerY() + 4f * d,
                10f * d, TEXT, Paint.Align.CENTER);

        label(c, "拖动自定义键位 · 灵敏度立即生效", panel.centerX(),
                panel.bottom - 8f * d, 8f * d, 0x6625E0C8, Paint.Align.CENTER);
    }

    private void drawLayoutMode(Canvas c) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0x7704060C);
        c.drawRect(0, 0, screenW, screenH, fill);
        label(c, "DRAG BUTTONS TO REPOSITION", screenW * 0.5f, screenH * 0.16f,
                14f * d, TEAL, Paint.Align.CENTER);
        label(c, "FIRE / JUMP / CROUCH", screenW * 0.5f, screenH * 0.16f + 22f * d,
                9f * d, DIM, Paint.Align.CENTER);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(3f * d);
        stroke.setColor(0xFFFFE27A);
        c.drawCircle(fireCx, fireCy, fireR + 6f * d, stroke);
        c.drawCircle(jumpCx, jumpCy, jumpR + 6f * d, stroke);
        c.drawCircle(crouchCx, crouchCy, crouchR + 6f * d, stroke);

        fill.setColor(0xCC332018);
        c.drawRoundRect(resetBtn, 8f * d, 8f * d, fill);
        stroke.setColor(0xCCFFB020);
        c.drawRoundRect(resetBtn, 8f * d, 8f * d, stroke);
        label(c, "RESET", resetBtn.centerX(), resetBtn.centerY() + 4f * d,
                10f * d, TEXT, Paint.Align.CENTER);

        fill.setColor(0xCC103028);
        c.drawRoundRect(doneBtn, 8f * d, 8f * d, fill);
        stroke.setColor(0xCC25E0C8);
        c.drawRoundRect(doneBtn, 8f * d, 8f * d, stroke);
        label(c, "DONE", doneBtn.centerX(), doneBtn.centerY() + 4f * d,
                10f * d, TEXT, Paint.Align.CENTER);
    }

    private void drawRoundButton(Canvas c, RectF r, String text) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0x3325E0C8);
        c.drawCircle(r.centerX(), r.centerY(), r.width() * 0.5f, fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.6f * d);
        stroke.setColor(0xAA25E0C8);
        c.drawCircle(r.centerX(), r.centerY(), r.width() * 0.5f, stroke);
        label(c, text, r.centerX(), r.centerY() + 7f * d, 20f * d, TEXT, Paint.Align.CENTER);
    }

    private void label(Canvas c, String value, float x, float y, float size, int color, Paint.Align align) {
        textPaint.setTextSize(size);
        textPaint.setTextAlign(align);
        textPaint.setColor(color);
        shadowPaint.setTextSize(size);
        shadowPaint.setTextAlign(align);
        shadowPaint.setColor(0x99000000);
        c.drawText(value, x + 1.4f * d, y + 1.4f * d, shadowPaint);
        c.drawText(value, x, y, textPaint);
    }

    private static int colorWithAlpha(int color, float alpha) {
        int a = (int) (255f * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static String two(int v) {
        return v < 10 ? "0" + v : Integer.toString(v);
    }
}
