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


import com.lyq.cpudungeon.engine.Game;
import com.lyq.cpudungeon.engine.Raycaster;
import com.lyq.cpudungeon.ui.Hud;

/**
 * Owns the render thread. The 3D scene is rasterized on the CPU into an int[]
 * framebuffer, uploaded into a Bitmap and blitted with nearest-neighbour
 * scaling; the HUD is drawn with software Canvas on top.
 */
public final class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final int MIN_WIDTH = 240;
    private static final int MAX_WIDTH = 560;
    private static final long FRAME_NS = 16_666_667L;

    private final SurfaceHolder holder;
    private final Game game = new Game(0xC0D0C0FFEEL);
    private final Raycaster raycaster = new Raycaster();
    private final Hud hud = new Hud();
    private final Paint blit = new Paint();
    private final Rect dstRect = new Rect();
    private final SharedPreferences prefs;
    private final float density;

    private Thread thread;
    private volatile boolean running;
    private volatile boolean surfaceReady;
    private volatile boolean paused;

    private int[] fb;
    private Bitmap bitmap;
    private int fbW;
    private int fbH;
    private int targetWidth = 400;
    private int viewW;
    private int viewH;

    private long lastNs;
    private float fps = 60f;
    private float sceneMs;
    private float fpsTimer;
    private int fpsFrames;
    private long lastAdjustNs;
    private int best;

    private int joyPointer = -1;
    private int firePointer = -1;
    private int lookPointer = -1;
    private float lookLastX;
    private long lookDownNs;
    private float lookMoved;

    private boolean keyW;
    private boolean keyA;
    private boolean keyS;
    private boolean keyD;
    private boolean keyTurnL;
    private boolean keyTurnR;
    private boolean keyFire;

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
        hud.layout(Math.max(1, getResources().getDisplayMetrics().widthPixels),
                Math.max(1, getResources().getDisplayMetrics().heightPixels), density);
    }

    public void resumeLoop() {
        paused = false;
        startLoop();
    }

    public void pauseLoop() {
        paused = true;
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
            applyKeyboard(dt);
            game.update(dt);
            if (game.score > best) {
                best = game.score;
            }

            try {
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
            maybeAdjustResolution(System.nanoTime());

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
            raycaster.render(game, fb, w, h, game.time);
            bmp.setPixels(fb, 0, w, 0, 0, w, h);
        }
        long renderEnd = System.nanoTime();
        sceneMs = sceneMs * 0.92f + (renderEnd - renderStart) / 1_000_000f * 0.08f;

        Canvas canvas = null;
        try {
            canvas = holder.lockCanvas();
            if (canvas != null) {
                dstRect.set(0, 0, canvas.getWidth(), canvas.getHeight());
                canvas.drawBitmap(bmp, null, dstRect, blit);
                hud.draw(canvas, game, fps, w, h, best);
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

    private void maybeAdjustResolution(long nowNs) {
        if (nowNs - lastAdjustNs < 1_200_000_000L) {
            return;
        }
        int next = targetWidth;
        if (sceneMs > 11f && targetWidth > MIN_WIDTH) {
            next = targetWidth - 40;
        } else if (sceneMs < 6.5f && targetWidth < MAX_WIDTH) {
            next = targetWidth + 40;
        }
        if (next != targetWidth) {
            targetWidth = next;
            lastAdjustNs = nowNs;
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

    private void applyKeyboard(float dt) {
        if (keyW || keyA || keyS || keyD) {
            float mx = (keyD ? 1f : 0f) - (keyA ? 1f : 0f);
            float my = (keyW ? 1f : 0f) - (keyS ? 1f : 0f);
            game.input.moveX = mx;
            game.input.moveY = my;
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
        if (viewW <= 0 || viewH <= 0) {
            return;
        }
        int w = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, targetWidth));
        int h = Math.max(120, Math.round(w * (viewH / (float) viewW)));
        if (fb != null && w == fbW && h == fbH) {
            return;
        }
        fbW = w;
        fbH = h;
        fb = new int[w * h];
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    }

    private void updateJoystick(float x, float y) {
        float dx = x - hud.joyCx;
        float dy = y - hud.joyCy;
        float len = (float) Math.hypot(dx, dy);
        float r = hud.joyR;
        if (len > r) {
            dx = dx / len * r;
            dy = dy / len * r;
        }
        hud.knobX = dx / r;
        hud.knobY = dy / r;
        game.input.moveX = hud.knobX;
        game.input.moveY = -hud.knobY;
    }

    private void releaseTouch() {
        joyPointer = -1;
        firePointer = -1;
        lookPointer = -1;
        game.input.moveX = 0f;
        game.input.moveY = 0f;
        game.input.fireHeld = false;
        hud.knobX = 0f;
        hud.knobY = 0f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float d = density;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = e.getActionIndex();
                int id = e.getPointerId(idx);
                float x = e.getX(idx);
                float y = e.getY(idx);
                if (game.state == Game.State.DEAD) {
                    game.input.restartTap = true;
                }
                if (dist(x, y, hud.joyCx, hud.joyCy) < hud.joyR * 1.55f && joyPointer < 0) {
                    joyPointer = id;
                    updateJoystick(x, y);
                } else if (dist(x, y, hud.fireCx, hud.fireCy) < hud.fireR * 1.45f && firePointer < 0) {
                    firePointer = id;
                    game.input.fireHeld = true;
                } else if (lookPointer < 0) {
                    lookPointer = id;
                    lookLastX = x;
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
                        float dx = x - lookLastX;
                        lookLastX = x;
                        lookMoved += Math.abs(dx);
                        game.input.look += dx * (3.0f / Math.max(1, viewW));
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
                    game.input.moveX = 0f;
                    game.input.moveY = 0f;
                    hud.knobX = 0f;
                    hud.knobY = 0f;
                } else if (id == firePointer) {
                    firePointer = -1;
                    game.input.fireHeld = keyFire;
                } else if (id == lookPointer) {
                    long dur = (System.nanoTime() - lookDownNs) / 1_000_000L;
                    if (lookMoved < 20 * d && dur < 300) {
                        if (game.state == Game.State.DEAD) {
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
                if (game.state == Game.State.DEAD) {
                    game.input.restartTap = true;
                }
                keyFire = true;
                game.input.fireHeld = true;
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
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
            case KeyEvent.KEYCODE_SPACE:
                keyFire = false;
                if (firePointer < 0) {
                    game.input.fireHeld = false;
                }
                break;
            default:
                return super.onKeyUp(keyCode, event);
        }
        if (!keyW && !keyA && !keyS && !keyD && joyPointer < 0) {
            game.input.moveX = 0f;
            game.input.moveY = 0f;
        }
        return true;
    }
}
