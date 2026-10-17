package com.lyq.cpudungeon;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.lyq.cpudungeon.engine3d.Camera3D;
import com.lyq.cpudungeon.engine3d.Game3D;
import com.lyq.cpudungeon.engine3d.PostFx;
import com.lyq.cpudungeon.engine3d.Renderer3D;
import com.lyq.cpudungeon.engine3d.UiEvent;
import com.lyq.cpudungeon.ui.Hud;

/**
 * Render thread for the true-3D build: CPU rasterizes the scene into an int[]
 * framebuffer, then the HUD is drawn with the software canvas on top.
 */
public final class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final int MIN_AUTO_HEIGHT = 360;
    private static final int MAX_AUTO_HEIGHT = 1440;
    private static final long FRAME_NS = 16_666_667L;

    private final SurfaceHolder holder;
    private final Game3D game = new Game3D(0xC0D0C0FFEEL);
    private final Renderer3D renderer = new Renderer3D();
    private final PostFx postFx = new PostFx();
    private final Camera3D camera = new Camera3D();
    private final Hud hud = new Hud();
    private final Paint blit = new Paint();
    private final Rect dstRect = new Rect();
    private final SharedPreferences prefs;
    private final Settings settings;
    private final AudioFx audio = new AudioFx();
    private final float density;

    private Thread thread;
    private volatile boolean running;
    private volatile boolean surfaceReady;
    private volatile boolean paused;

    private int[] fb;
    private Bitmap bitmap;
    private int fbW;
    private int fbH;
    private int viewW;
    private int viewH;

    private long lastNs;
    private float fps = 60f;
    private float fpsTimer;
    private int fpsFrames;
    private int adaptiveHeight = 720;
    private float sceneMs = 16f;
    private long lastAdaptNs;
    private int best;
    private int lastShotSeq;
    private float joyTargetX;
    private float joyTargetY;
    private float joySmoothX;
    private float joySmoothY;

    private int joyPointer = -1;
    private int firePointer = -1;
    private int jumpPointer = -1;
    private int crouchPointer = -1;
    private int lookPointer = -1;
    private float lookLastX;
    private float lookLastY;
    private long lookDownNs;
    private float lookMoved;

    private int cheatTaps;
    private long cheatLastNs;
    private int uiMode = Hud.MODE_PLAY;
    private int dragPointer = -1;
    private int dragButton = 0;
    private float dragDx;
    private float dragDy;

    private boolean keyW;
    private boolean keyA;
    private boolean keyS;
    private boolean keyD;
    private boolean keyTurnL;
    private boolean keyTurnR;
    private boolean keyFire;
    private boolean keyCrouch;

    public GameView(Context context) {
        super(context);
        holder = getHolder();
        holder.addCallback(this);
        holder.setFormat(PixelFormat.RGBA_8888);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setKeepScreenOn(true);
        density = getResources().getDisplayMetrics().density;
        blit.setFilterBitmap(false);
        blit.setDither(false);
        prefs = context.getSharedPreferences("cpu_dungeon", Context.MODE_PRIVATE);
        best = prefs.getInt("best", 0);
        settings = new Settings(context);
        audio.prewarm();
        hud.setSettings(settings);
        hud.layout(Math.max(1, getResources().getDisplayMetrics().widthPixels),
                Math.max(1, getResources().getDisplayMetrics().heightPixels), density);
    }

    public void resumeLoop() {
        paused = false;
        startLoop();
    }

    public void pauseLoop() {
        paused = true;
        settings.save();
        audio.release();
        stopLoop();
    }

    public void persistBest() {
        if (game.score > best) {
            best = game.score;
        }
        prefs.edit().putInt("best", best).apply();
    }

    private void startLoop() {
        if (thread != null || !surfaceReady || paused) {
            return;
        }
        running = true;
        lastNs = 0L;
        thread = new Thread(this, "cpu-rasterizer");
        thread.setPriority(Thread.NORM_PRIORITY + 2);
        thread.start();
    }

    private void stopLoop() {
        running = false;
        Thread t = thread;
        thread = null;
        if (t != null) {
            try {
                t.join(700);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void run() {
        while (running) {
            long frameStart = System.nanoTime();
            if (lastNs == 0L) {
                lastNs = frameStart;
            }
            float dt = (frameStart - lastNs) / 1_000_000_000f;
            lastNs = frameStart;
            if (dt > 0.1f) {
                dt = 0.1f;
            }
            try {
                if (uiMode == Hud.MODE_PLAY) {
                    applyKeyboard(dt);
                    smoothJoystick(dt);
                    game.update(dt);
                    if (game.score > best) {
                        best = game.score;
                    }
                    if (game.shotSeq != lastShotSeq) {
                        lastShotSeq = game.shotSeq;
                        audio.shot();
                    }
                    for (int i = 0; i < game.uiEvents.size(); i++) {
                        UiEvent e = game.uiEvents.get(i);
                        hud.addPopup(e.text, e.color);
                    }
                    game.uiEvents.clear();
                }
                hud.update(dt);
                renderFrame();
            } catch (Throwable t) {
                CrashLog.write(getContext(), t);
            }

            fpsFrames++;
            fpsTimer += dt;
            if (fpsTimer >= 0.5f) {
                fps = fpsFrames / fpsTimer;
                fpsFrames = 0;
                fpsTimer = 0f;
            }
            maybeAdaptive(System.nanoTime());

            long busy = System.nanoTime() - frameStart;
            long sleep = FRAME_NS - busy;
            if (sleep > 1_000_000L) {
                try {
                    Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                }
            }
        }
    }

    private void renderFrame() {
        int w;
        int h;
        Bitmap bmp;
        long renderStart = System.nanoTime();
        synchronized (this) {
            if (fb == null || bitmap == null) {
                return;
            }
            w = fbW;
            h = fbH;
            bmp = bitmap;
            updateCamera(w);
            renderer.renderAll(game.mesh, game.enemyMesh, game.billboards,
                    game.billboardCount, camera, fb, w, h, game.time);
            if (settings.antiAlias) {
                postFx.antiAlias(fb, w, h);
            }
            bmp.setPixels(fb, 0, w, 0, 0, w, h);
        }
        long renderEnd = System.nanoTime();
        sceneMs = sceneMs * 0.9f + (renderEnd - renderStart) / 1_000_000f * 0.1f;

        Canvas canvas = null;
        try {
            canvas = holder.lockCanvas();
            if (canvas != null) {
                dstRect.set(0, 0, canvas.getWidth(), canvas.getHeight());
                canvas.drawBitmap(bmp, null, dstRect, blit);
                hud.draw(canvas, game, fps, w, h, best, uiMode);
            }
        } catch (Throwable ignored) {
            // surface can disappear mid-frame during rotation/background
        } finally {
            if (canvas != null) {
                try {
                    holder.unlockCanvasAndPost(canvas);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void maybeAdaptive(long nowNs) {
        if (settings.resolutionMode != Settings.RES_AUTO || uiMode != Hud.MODE_PLAY) {
            return;
        }
        if (nowNs - lastAdaptNs < 1_200_000_000L) {
            return;
        }
        int next = adaptiveHeight;
        if (sceneMs > 24f && next > MIN_AUTO_HEIGHT) {
            next -= 120;
        } else if (sceneMs < 12f && next < MAX_AUTO_HEIGHT) {
            next += 120;
        }
        next = Math.max(MIN_AUTO_HEIGHT, Math.min(MAX_AUTO_HEIGHT, next));
        if (next != adaptiveHeight) {
            adaptiveHeight = next;
            lastAdaptNs = nowNs;
            post(new Runnable() {
                @Override
                public void run() {
                    synchronized (GameView.this) {
                        allocateFramebuffer();
                    }
                }
            });
        }
    }

    private void applyResolutionSetting() {
        if (settings.resolutionMode == Settings.RES_AUTO) {
            adaptiveHeight = 720;
        }
        sceneMs = 16f;
        lastAdaptNs = System.nanoTime();
        synchronized (this) {
            allocateFramebuffer();
        }
    }

    private void updateCamera(int w) {
        float bob = (float) Math.sin(game.player.bob) * 0.045f * game.player.moveSpeed
                - game.player.landDip * 0.12f;
        camera.x = game.player.x;
        camera.y = game.player.y;
        camera.z = game.player.z + game.player.eyeHeight + bob;
        camera.yaw = game.player.yaw;
        camera.pitch = game.player.pitch + game.player.recoil * 0.012f;
        camera.setFovDegrees(settings.fovDegrees);
        float shake = game.shake;
        if (shake > 0.001f) {
            camera.x += (float) Math.sin(game.time * 93f) * 0.035f * shake;
            camera.y += (float) Math.cos(game.time * 77f) * 0.035f * shake;
            camera.z += (float) Math.sin(game.time * 111f) * 0.020f * shake;
            camera.pitch += (float) Math.sin(game.time * 71f) * 0.010f * shake;
        }
        camera.update(w);
    }

    private void smoothJoystick(float dt) {
        float k = 1f - (float) Math.exp(-dt * 16f);
        joySmoothX += (joyTargetX - joySmoothX) * k;
        joySmoothY += (joyTargetY - joySmoothY) * k;
        if (Math.abs(joySmoothX) < 0.001f) joySmoothX = 0f;
        if (Math.abs(joySmoothY) < 0.001f) joySmoothY = 0f;
        game.input.moveX = joySmoothX;
        game.input.moveY = joySmoothY;
    }

    private void applyKeyboard(float dt) {
        if (keyW || keyA || keyS || keyD) {
            joyTargetX = (keyD ? 1f : 0f) - (keyA ? 1f : 0f);
            joyTargetY = (keyW ? 1f : 0f) - (keyS ? 1f : 0f);
        } else if (joyPointer < 0) {
            joyTargetX = 0f;
            joyTargetY = 0f;
        }
        float turn = ((keyTurnL ? 1f : 0f) - (keyTurnR ? 1f : 0f)) * 1.9f * dt;
        if (turn != 0f) {
            game.input.look += turn;
        }
        if (keyFire) {
            game.input.fireHeld = true;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder h) {
        surfaceReady = true;
        allocateFramebuffer();
        startLoop();
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int format, int width, int height) {
        viewW = width;
        viewH = height;
        hud.layout(width, height, density);
        synchronized (this) {
            allocateFramebuffer();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        surfaceReady = false;
        stopLoop();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewW = w;
        viewH = h;
        hud.layout(w, h, density);
        synchronized (this) {
            allocateFramebuffer();
        }
    }

    private void allocateFramebuffer() {
        if (viewW <= 0 || viewH <= 0) {
            viewW = getWidth();
            viewH = getHeight();
        }
        int h = settings.fixedHeight();
        if (h == 0) {
            h = adaptiveHeight;
        }
        h = Math.max(MIN_AUTO_HEIGHT, Math.min(MAX_AUTO_HEIGHT, h));
        int w = viewW > 0 && viewH > 0 ? Math.round(h * (viewW / (float) viewH)) : 1280;
        if ((w & 1) == 1) {
            w++;
        }
        if (fb != null && w == fbW && h == fbH) {
            return;
        }
        fbW = w;
        fbH = h;
        fb = new int[w * h];
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    }

    private void updateJoystick(float x, float y) {
        float dx = (x - hud.joyCx) / hud.joyR;
        float dy = (y - hud.joyCy) / hud.joyR;
        float len = (float) Math.hypot(dx, dy);
        if (len > 1f) {
            dx /= len;
            dy /= len;
            len = 1f;
        }
        hud.knobX = dx;
        hud.knobY = dy;
        final float deadzone = 0.16f;
        if (len <= deadzone) {
            joyTargetX = 0f;
            joyTargetY = 0f;
        } else {
            float scaled = (len - deadzone) / (1f - deadzone);
            joyTargetX = dx / len * scaled;
            joyTargetY = -dy / len * scaled;
        }
    }

    private void releaseTouch() {
        joyPointer = -1;
        firePointer = -1;
        jumpPointer = -1;
        crouchPointer = -1;
        lookPointer = -1;
        keyW = false;
        keyA = false;
        keyS = false;
        keyD = false;
        keyTurnL = false;
        keyTurnR = false;
        keyFire = false;
        keyCrouch = false;
        joyTargetX = 0f;
        joyTargetY = 0f;
        joySmoothX = 0f;
        joySmoothY = 0f;
        game.input.moveX = 0f;
        game.input.moveY = 0f;
        game.input.look = 0f;
        game.input.pitch = 0f;
        game.input.jumpTap = false;
        game.input.fireHeld = false;
        game.input.crouch = false;
        hud.knobX = 0f;
        hud.knobY = 0f;
    }

    private boolean handleSettingsTouch(MotionEvent e) {
        if (e.getActionMasked() != MotionEvent.ACTION_DOWN) {
            return true;
        }
        float x = e.getX();
        float y = e.getY();
        if (hud.sensMinus.contains(x, y)) {
            settings.nudgeSensitivity(-0.1f);
            hud.layout(viewW, viewH, density);
        } else if (hud.sensPlus.contains(x, y)) {
            settings.nudgeSensitivity(0.1f);
            hud.layout(viewW, viewH, density);
        } else if (hud.fovMinus.contains(x, y)) {
            settings.nudgeFov(-2f);
        } else if (hud.fovPlus.contains(x, y)) {
            settings.nudgeFov(2f);
        } else if (hud.resBtn.contains(x, y)) {
            settings.cycleResolution();
            applyResolutionSetting();
            hud.layout(viewW, viewH, density);
        } else if (hud.aaBtn.contains(x, y)) {
            settings.toggleAntiAlias();
        } else if (hud.layoutBtn.contains(x, y)) {
            uiMode = Hud.MODE_LAYOUT;
            dragPointer = -1;
            dragButton = 0;
        } else if (hud.closeBtn.contains(x, y)) {
            settings.save();
            uiMode = Hud.MODE_PLAY;
        }
        return true;
    }

    private boolean handleLayoutTouch(MotionEvent e) {
        float x = e.getX(e.getActionIndex());
        float y = e.getY(e.getActionIndex());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (hud.doneBtn.contains(x, y)) {
                    settings.save();
                    uiMode = Hud.MODE_SETTINGS;
                    return true;
                }
                if (hud.resetBtn.contains(x, y)) {
                    settings.resetLayout();
                    settings.save();
                    hud.layout(viewW, viewH, density);
                    return true;
                }
                int button = 0;
                float cx = 0f;
                float cy = 0f;
                if (inCircle(x, y, hud.fireCx, hud.fireCy, hud.fireR)) {
                    button = 1;
                    cx = hud.fireCx;
                    cy = hud.fireCy;
                } else if (inCircle(x, y, hud.jumpCx, hud.jumpCy, hud.jumpR)) {
                    button = 2;
                    cx = hud.jumpCx;
                    cy = hud.jumpCy;
                } else if (inCircle(x, y, hud.crouchCx, hud.crouchCy, hud.crouchR)) {
                    button = 3;
                    cx = hud.crouchCx;
                    cy = hud.crouchCy;
                }
                if (button != 0) {
                    dragButton = button;
                    dragPointer = e.getPointerId(e.getActionIndex());
                    dragDx = x - cx;
                    dragDy = y - cy;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (dragButton == 0 || dragPointer < 0) {
                    break;
                }
                for (int i = 0; i < e.getPointerCount(); i++) {
                    if (e.getPointerId(i) != dragPointer) {
                        continue;
                    }
                    float nx = e.getX(i) - dragDx;
                    float ny = e.getY(i) - dragDy;
                    nx = Math.max(56f * density, Math.min(viewW - 56f * density, nx));
                    ny = Math.max(56f * density, Math.min(viewH - 56f * density, ny));
                    if (dragButton == 1) {
                        settings.fireRight = (viewW - nx) / density;
                        settings.fireBottom = (viewH - ny) / density;
                    } else if (dragButton == 2) {
                        settings.jumpRight = (viewW - nx) / density;
                        settings.jumpBottom = (viewH - ny) / density;
                    } else {
                        settings.crouchRight = (viewW - nx) / density;
                        settings.crouchBottom = (viewH - ny) / density;
                    }
                    hud.layout(viewW, viewH, density);
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                if (e.getPointerId(e.getActionIndex()) == dragPointer) {
                    dragPointer = -1;
                    dragButton = 0;
                    settings.save();
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                dragPointer = -1;
                dragButton = 0;
                break;
            default:
                break;
        }
        return true;
    }

    private boolean inCircle(float x, float y, float cx, float cy, float r) {
        return dist(x, y, cx, cy) < r;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (uiMode == Hud.MODE_SETTINGS) {
            return handleSettingsTouch(e);
        }
        if (uiMode == Hud.MODE_LAYOUT) {
            return handleLayoutTouch(e);
        }
        float d = density;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = e.getActionIndex();
                int id = e.getPointerId(idx);
                float x = e.getX(idx);
                float y = e.getY(idx);

                // hidden easter egg: ten taps in the top-right corner
                if (x > viewW - 64 * d && y < 64 * d) {
                    long now = System.nanoTime();
                    if (now - cheatLastNs > 4_000_000_000L) {
                        cheatTaps = 0;
                    }
                    cheatLastNs = now;
                    cheatTaps++;
                    if (cheatTaps >= 10) {
                        cheatTaps = 0;
                        game.input.cheatTap = true;
                    }
                    return true;
                }

                if (hud.settingsRect.contains(x, y)) {
                    releaseTouch();
                    uiMode = Hud.MODE_SETTINGS;
                    return true;
                }
                if (game.state == Game3D.State.DEAD) {
                    game.input.restartTap = true;
                }
                if (inCircle(x, y, hud.joyCx, hud.joyCy, hud.joyR * 1.55f) && joyPointer < 0) {
                    joyPointer = id;
                    updateJoystick(x, y);
                } else if (inCircle(x, y, hud.fireCx, hud.fireCy, hud.fireR * 1.45f) && firePointer < 0) {
                    firePointer = id;
                    game.input.fireHeld = true;
                } else if (inCircle(x, y, hud.jumpCx, hud.jumpCy, hud.jumpR * 1.5f) && jumpPointer < 0) {
                    jumpPointer = id;
                    game.input.jumpTap = true;
                } else if (inCircle(x, y, hud.crouchCx, hud.crouchCy, hud.crouchR * 1.5f) && crouchPointer < 0) {
                    crouchPointer = id;
                    game.input.crouch = true;
                } else if (lookPointer < 0) {
                    lookPointer = id;
                    lookLastX = x;
                    lookLastY = y;
                    lookDownNs = System.nanoTime();
                    lookMoved = 0f;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int id = e.getPointerId(i);
                    if (id == joyPointer) {
                        updateJoystick(e.getX(i), e.getY(i));
                    } else if (id == lookPointer) {
                        float x = e.getX(i);
                        float y = e.getY(i);
                        float dx = x - lookLastX;
                        float dy = y - lookLastY;
                        lookLastX = x;
                        lookLastY = y;
                        lookMoved += Math.abs(dx) + Math.abs(dy);
                        float sens = settings.lookSensitivity;
                        game.input.look += dx * (3.0f / Math.max(1, viewW)) * sens;
                        game.input.pitch -= dy * (3.2f / Math.max(1, viewH)) * sens;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int idx = e.getActionIndex();
                int id = e.getPointerId(idx);
                if (id == joyPointer) {
                    joyPointer = -1;
                    joyTargetX = 0f;
                    joyTargetY = 0f;
                    hud.knobX = 0f;
                    hud.knobY = 0f;
                } else if (id == firePointer) {
                    firePointer = -1;
                    game.input.fireHeld = keyFire;
                } else if (id == jumpPointer) {
                    jumpPointer = -1;
                } else if (id == crouchPointer) {
                    crouchPointer = -1;
                    game.input.crouch = keyCrouch;
                } else if (id == lookPointer) {
                    long dur = (System.nanoTime() - lookDownNs) / 1_000_000L;
                    if (lookMoved < 20 * d && dur < 300) {
                        if (game.state == Game3D.State.DEAD) {
                            game.input.restartTap = true;
                        } else {
                            game.input.fireTap = true;
                        }
                    }
                    lookPointer = -1;
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                releaseTouch();
                break;
            }
            default:
                break;
        }
        return true;
    }

    private static float dist(float x, float y, float cx, float cy) {
        return (float) Math.hypot(x - cx, y - cy);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (uiMode == Hud.MODE_LAYOUT) {
                settings.save();
                uiMode = Hud.MODE_SETTINGS;
                return true;
            }
            if (uiMode == Hud.MODE_SETTINGS) {
                settings.save();
                uiMode = Hud.MODE_PLAY;
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }
        if (keyCode == KeyEvent.KEYCODE_M) {
            if (uiMode == Hud.MODE_PLAY) {
                releaseTouch();
                uiMode = Hud.MODE_SETTINGS;
            } else {
                settings.save();
                uiMode = Hud.MODE_PLAY;
            }
            return true;
        }
        if (uiMode != Hud.MODE_PLAY) {
            return true;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_DPAD_UP:
                keyW = true;
                return true;
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                keyS = true;
                return true;
            case KeyEvent.KEYCODE_A:
                keyA = true;
                return true;
            case KeyEvent.KEYCODE_D:
                keyD = true;
                return true;
            case KeyEvent.KEYCODE_Q:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                keyTurnL = true;
                return true;
            case KeyEvent.KEYCODE_E:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                keyTurnR = true;
                return true;
            case KeyEvent.KEYCODE_SPACE:
                if (game.state == Game3D.State.DEAD) {
                    game.input.restartTap = true;
                }
                game.input.jumpTap = true;
                return true;
            case KeyEvent.KEYCODE_F:
                keyFire = true;
                game.input.fireHeld = true;
                return true;
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
            case KeyEvent.KEYCODE_CTRL_LEFT:
            case KeyEvent.KEYCODE_CTRL_RIGHT:
                keyCrouch = true;
                game.input.crouch = true;
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (uiMode != Hud.MODE_PLAY) {
            return true;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_DPAD_UP:
                keyW = false;
                break;
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                keyS = false;
                break;
            case KeyEvent.KEYCODE_A:
                keyA = false;
                break;
            case KeyEvent.KEYCODE_D:
                keyD = false;
                break;
            case KeyEvent.KEYCODE_Q:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                keyTurnL = false;
                break;
            case KeyEvent.KEYCODE_E:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                keyTurnR = false;
                break;
            case KeyEvent.KEYCODE_F:
                keyFire = false;
                if (firePointer < 0) {
                    game.input.fireHeld = false;
                }
                break;
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
            case KeyEvent.KEYCODE_CTRL_LEFT:
            case KeyEvent.KEYCODE_CTRL_RIGHT:
                keyCrouch = false;
                if (crouchPointer < 0) {
                    game.input.crouch = false;
                }
                break;
            default:
                return super.onKeyUp(keyCode, event);
        }
        return true;
    }
}
