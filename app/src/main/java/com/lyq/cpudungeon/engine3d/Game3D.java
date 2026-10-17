package com.lyq.cpudungeon.engine3d;

import com.lyq.cpudungeon.engine.Enemy;
import com.lyq.cpudungeon.engine.Input;
import com.lyq.cpudungeon.engine.Particle;
import com.lyq.cpudungeon.engine.Pickup;
import com.lyq.cpudungeon.engine.Textures;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * v2 simulation: true-3D aiming, jumping/crouching, stair-aware collision and
 * billboard entities rendered by {@link Renderer3D}.
 */
public final class Game3D {
    public enum State { PLAYING, CLEARED, DEAD }

    public static final float MOVE_SPEED = 3.1f;
    public static final float CROUCH_SPEED = 1.75f;
    public static final float FIRE_INTERVAL = 0.24f;
    public static final float CHEAT_FIRE_INTERVAL = 0.09f;
    public static final int MAX_PARTICLES = 240;

    public final Input input = new Input();
    public final ArrayList<Enemy> enemies = new ArrayList<>();
    public final ArrayList<Pickup> pickups = new ArrayList<>();
    public final ArrayList<Particle> particles = new ArrayList<>();
    public final ArrayList<Bullet3D> bullets = new ArrayList<>();
    public final ArrayList<UiEvent> uiEvents = new ArrayList<>();
    public final Mesh3D enemyMesh = new Mesh3D();
    public final ArrayList<Billboard3D> billboards = new ArrayList<>();
    public int billboardCount;

    public final Player3D player = new Player3D();
    public Level3D level;
    public Mesh3D mesh;
    public State state = State.PLAYING;

    public int levelIndex = 1;
    public int score;
    public int coresTotal;
    public int coresGot;
    public int kills;
    public float time;
    public float stateTimer;
    public float toastTimer;
    public float hintTimer;
    public float vibration;
    public float shake;
    public int shotSeq;
    public boolean exitActive;
    public boolean cheat;

    public String toast = "";
    public String hint = "";

    private final long seed;
    private Rng rng;
    private int[] flow;
    private int[] queue;
    private float flowTimer;
    private int lastPlayerCell = -1;
    private float flowTargetX;
    private float flowTargetY;

    public Game3D(long seed) {
        this.seed = seed;
        startLevel(1);
    }

    public void restart() {
        score = 0;
        kills = 0;
        levelIndex = 1;
        cheat = false;
        startLevel(1);
    }

    public void toggleCheat() {
        cheat = !cheat;
        toast = cheat ? "OVERCLOCK // GOD MODE" : "OVERCLOCK OFFLINE";
        toastTimer = 2.4f;
    }

    private void startLevel(int index) {
        levelIndex = index;
        rng = new Rng(seed ^ (index * 0x9E3779B97F4A7C15L));
        level = MapGen3.generate(index, seed + index * 7919L);
        mesh = MeshBuilder3D.build(level);
        player.reset(level.startX + 0.5f, level.startY + 0.5f,
                level.floorAt(level.startX + 0.5f, level.startY + 0.5f), spawnYaw());
        enemies.clear();
        pickups.clear();
        particles.clear();
        bullets.clear();
        uiEvents.clear();
        billboards.clear();
        billboardCount = 0;
        enemyMesh.beginFrame();
        shake = 0f;
        coresGot = 0;
        exitActive = false;
        state = State.PLAYING;
        stateTimer = 0f;
        time = 0f;
        flowTimer = 0f;
        lastPlayerCell = -1;
        flow = new int[level.w * level.h];
        queue = new int[level.w * level.h];

        int[] dist = new int[level.w * level.h];
        levelBfs(dist);

        coresTotal = Math.min(8, 4 + (index + 1) / 2);
        int guard = 0;
        while (pickups.size() < coresTotal && guard++ < 900) {
            int idx = randomFloorCell(dist, 3);
            if (idx < 0) {
                break;
            }
            int cx = idx % level.w;
            int cy = idx / level.w;
            if (cx == level.exitX && cy == level.exitY) {
                continue;
            }
            float px = cx + 0.5f;
            float py = cy + 0.5f;
            boolean ok = true;
            for (Pickup p : pickups) {
                if (Math.hypot(p.x - px, p.y - py) < 3.0f) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                pickups.add(new Pickup(px, py, guard * 0.7f));
            }
        }
        if (pickups.isEmpty()) {
            pickups.add(new Pickup(level.exitX + 0.5f, level.exitY + 0.5f, 0f));
        }
        coresTotal = pickups.size();

        int enemyCount = Math.min(14, 3 + index);
        guard = 0;
        while (enemies.size() < enemyCount && guard++ < 900) {
            int idx = randomFloorCell(dist, 6);
            if (idx < 0) {
                break;
            }
            int cx = idx % level.w;
            int cy = idx / level.w;
            float ex = cx + 0.5f;
            float ey = cy + 0.5f;
            boolean ok = true;
            for (Enemy e : enemies) {
                if (Math.hypot(e.x - ex, e.y - ey) < 2.0f) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                Enemy e = new Enemy(ex, ey, guard * 1.3f);
                e.z = level.floorAt(ex, ey) + 0.78f;
                e.yaw = (float) Math.atan2(player.y - ey, player.x - ex);
                enemies.add(e);
            }
        }

        toast = "SECTOR " + index;
        toastTimer = 2.2f;
        if (index == 1) {
            hint = "左摇杆移动 · 右半屏滑动转向/抬头 · JUMP 跳跃 · CROUCH 下蹲";
            hintTimer = 9f;
        } else {
            hint = "";
            hintTimer = 0f;
        }
    }

    /** Face the longest open corridor so the first frame is not a wall. */
    private float spawnYaw() {
        int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        float[] angles = {0f, (float) (Math.PI * 0.5f), (float) Math.PI, (float) (-Math.PI * 0.5f)};
        int best = -1;
        float bestAngle = 0f;
        for (int d = 0; d < 4; d++) {
            int open = 0;
            for (int step = 1; step <= 12; step++) {
                float px = level.startX + 0.5f + dirs[d][0] * step;
                float py = level.startY + 0.5f + dirs[d][1] * step;
                if (level.solidAt(px, py)) {
                    break;
                }
                open++;
            }
            if (open > best) {
                best = open;
                bestAngle = angles[d];
            }
        }
        return bestAngle;
    }

    private int randomFloorCell(int[] dist, int minDist) {
        for (int attempt = 0; attempt < 140; attempt++) {
            int idx = rng.nextInt(dist.length);
            if (dist[idx] >= minDist) {
                return idx;
            }
        }
        return -1;
    }

    public void update(float dt) {
        if (dt > 0.05f) dt = 0.05f;
        if (dt < 0f) dt = 0f;
        time += dt;
        if (toastTimer > 0) toastTimer -= dt;
        if (hintTimer > 0) hintTimer -= dt;
        player.damageFlash = Math.max(0f, player.damageFlash - dt * 2.4f);
        player.recoil = Math.max(0f, player.recoil - dt * 7f);
        player.muzzle = Math.max(0f, player.muzzle - dt * 9f);
        player.landDip = Math.max(0f, player.landDip - dt * 4f);
        if (vibration > 0) vibration = Math.max(0f, vibration - dt * 4f);
        if (shake > 0) shake = Math.max(0f, shake - dt * 3.2f);

        if (input.cheatTap) {
            toggleCheat();
            input.cheatTap = false;
        }

        updateParticles(dt);

        if (state == State.PLAYING) {
            updatePlayer(dt);
            updateEnemies(dt);
            updateBullets(dt);
            updatePickups();
            updateExit();
        } else if (state == State.CLEARED) {
            stateTimer += dt;
            if (stateTimer > 1.7f) {
                startLevel(levelIndex + 1);
            }
        } else if (state == State.DEAD) {
            stateTimer += dt;
            if (input.restartTap) {
                restart();
            }
        }
        buildEnemyMesh();
        buildBillboards();
        input.clearTransient();
    }

    private void updatePlayer(float dt) {
        player.yaw += input.look;
        player.pitch += input.pitch;
        float maxPitch = 1.25f;
        if (player.pitch > maxPitch) player.pitch = maxPitch;
        if (player.pitch < -maxPitch) player.pitch = -maxPitch;

        player.crouch = input.crouch;
        float targetEye = player.crouch ? Player3D.CROUCH_EYE : Player3D.STAND_EYE;
        player.eyeHeight += (targetEye - player.eyeHeight) * Math.min(1f, dt * 12f);

        float speed = (player.crouch ? CROUCH_SPEED : MOVE_SPEED) * (cheat ? 1.9f : 1f);
        float dirX = (float) Math.cos(player.yaw);
        float dirY = (float) Math.sin(player.yaw);
        float mx = clamp(input.moveX, -1f, 1f);
        float my = clamp(input.moveY, -1f, 1f);
        float vx = (dirX * my - dirY * mx) * speed;
        float vy = (dirY * my + dirX * mx) * speed;
        float mag = (float) Math.sqrt(vx * vx + vy * vy);
        if (mag > speed) {
            vx = vx / mag * speed;
            vy = vy / mag * speed;
            mag = speed;
        }
        if (mag > 0.01f) {
            float stepX = vx * dt;
            float stepY = vy * dt;
            if (canMove(player.x + stepX, player.y)) {
                player.x += stepX;
            }
            if (canMove(player.x, player.y + stepY)) {
                player.y += stepY;
            }
        }

        float ground = groundAt(player.x, player.y);
        if (input.jumpTap && player.grounded) {
            player.vz = Player3D.JUMP_SPEED * (cheat ? 1.12f : 1f);
            player.grounded = false;
        }
        player.vz -= Player3D.GRAVITY * dt;
        player.z += player.vz * dt;

        ground = groundAt(player.x, player.y);
        if (player.z <= ground) {
            if (!player.grounded && player.vz < -2f) {
                player.landDip = Math.min(1f, -player.vz * 0.07f);
            }
            player.z = ground;
            player.vz = 0f;
            player.grounded = true;
        } else {
            player.grounded = false;
        }

        float ceilingLimit = Level3D.CEIL_Z - player.height();
        if (player.z > ceilingLimit) {
            player.z = ceilingLimit;
            if (player.vz > 0f) player.vz = 0f;
        }

        float target = mag / MOVE_SPEED;
        player.moveSpeed += (target - player.moveSpeed) * Math.min(1f, dt * 9f);
        if (player.grounded) {
            player.bob += dt * (5f + 7f * player.moveSpeed) * player.moveSpeed;
        }

        player.fireCooldown -= dt;
        float interval = cheat ? CHEAT_FIRE_INTERVAL : FIRE_INTERVAL;
        if ((input.fireHeld || input.fireTap) && player.fireCooldown <= 0f && state == State.PLAYING) {
            shoot(interval);
        }
    }

    private boolean canMove(float x, float y) {
        float r = Player3D.RADIUS;
        if (level.solidAt(x - r, y - r) || level.solidAt(x + r, y - r)
                || level.solidAt(x - r, y + r) || level.solidAt(x + r, y + r)) {
            return false;
        }
        float ground = groundAt(x, y);
        return ground <= player.z + Player3D.STEP_UP + 0.001f;
    }

    private float groundAt(float x, float y) {
        float r = Player3D.RADIUS * 0.9f;
        float g = level.floorAt(x, y);
        g = Math.max(g, level.floorAt(x - r, y));
        g = Math.max(g, level.floorAt(x + r, y));
        g = Math.max(g, level.floorAt(x, y - r));
        g = Math.max(g, level.floorAt(x, y + r));
        return g;
    }

    private void shoot(float interval) {
        player.fireCooldown = interval;
        player.recoil = 1f;
        player.muzzle = 1f;
        shotSeq++;

        float cp = (float) Math.cos(player.pitch);
        float sp = (float) Math.sin(player.pitch);
        float fx = (float) Math.cos(player.yaw) * cp;
        float fy = (float) Math.sin(player.yaw) * cp;
        float fz = sp;
        float rx = -(float) Math.sin(player.yaw);
        float ry = (float) Math.cos(player.yaw);

        Bullet3D b = new Bullet3D();
        b.px = b.x = player.x + fx * 0.34f + rx * 0.10f;
        b.py = b.y = player.y + fy * 0.34f + ry * 0.10f;
        b.pz = b.z = player.z + player.eyeHeight + fz * 0.34f - 0.09f;
        float speed = 26f;
        b.vx = fx * speed;
        b.vy = fy * speed;
        b.vz = fz * speed;
        b.life = 2.2f;
        if (bullets.size() >= 48) {
            bullets.remove(0);
        }
        bullets.add(b);
    }

    private void updateBullets(float dt) {
        float stepDist = 26f * dt;
        int steps = Math.max(1, (int) Math.ceil(stepDist / 0.10f));
        float inv = 1f / steps;
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet3D b = bullets.get(i);
            b.life -= dt;
            if (!b.alive || b.life <= 0f) {
                bullets.remove(i);
                continue;
            }
            for (int s = 0; s < steps && b.alive; s++) {
                b.px = b.x;
                b.py = b.y;
                b.pz = b.z;
                b.x += b.vx * dt * inv;
                b.y += b.vy * dt * inv;
                b.z += b.vz * dt * inv;

                if (b.z >= Level3D.CEIL_Z - 0.02f
                        || b.z <= level.floorAt(b.x, b.y) + 0.02f
                        || level.solidAt(b.x, b.y)) {
                    spark(b.x, b.y, b.z, 5, 0xFF9FE8FF, 1.5f);
                    b.alive = false;
                    break;
                }
                for (Enemy e : enemies) {
                    if (e.dead) {
                        continue;
                    }
                    float dx = b.x - e.x;
                    float dy = b.y - e.y;
                    float dz = b.z - e.z;
                    if (dx * dx + dy * dy < 0.36f * 0.36f && Math.abs(dz) < 0.48f) {
                        damageEnemy(e, cheat ? 3 : 1);
                        b.alive = false;
                        break;
                    }
                }
            }
            if (!b.alive) {
                bullets.remove(i);
            }
        }
    }

    private void damageEnemy(Enemy e, int damage) {
        e.hp -= damage;
        e.hitFlash = 0.16f;
        e.alert = 1f;
        ui("-" + damage, 0xFFFF9AB0);
        spark(e.x, e.y, e.z, 7, 0xFFFF63A8, 2.4f);
        if (e.hp <= 0) {
            e.dead = true;
            kills++;
            score += 60;
            ui("+60", 0xFFFFD36A);
            burst(e.x, e.y, e.z, 20, 0xFFFF3E8A);
            vibration = Math.max(vibration, 0.25f);
        }
    }

    private void buildEnemyMesh() {
        enemyMesh.beginFrame();
        for (Enemy e : enemies) {
            if (e.dead) {
                continue;
            }
            EnemyModel.build(e, level, enemyMesh, cheat);
        }
    }

    private void ui(String text, int color) {
        if (uiEvents.size() > 24) {
            uiEvents.remove(0);
        }
        uiEvents.add(new UiEvent(text, color));
    }

    public boolean hasLineOfSight(float x0, float y0, float z0, float x1, float y1, float z1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dz = z1 - z0;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) (dist / 0.11f) + 1;
        for (int i = 1; i < steps; i++) {
            float t = i / (float) steps;
            float x = x0 + dx * t;
            float y = y0 + dy * t;
            float z = z0 + dz * t;
            if (z > Level3D.CEIL_Z || z < 0f) {
                return false;
            }
            if (level.solidAt(x, y) || z <= level.floorAt(x, y)) {
                return false;
            }
        }
        return true;
    }

    private void updateEnemies(float dt) {
        rebuildFlowIfNeeded(dt);
        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy e = it.next();
            if (e.dead) {
                it.remove();
                continue;
            }
            e.phase += dt * 3.1f;
            if (e.hitFlash > 0f) e.hitFlash -= dt;

            float dx = player.x - e.x;
            float dy = player.y - e.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            boolean los = dist < 13f
                    && hasLineOfSight(e.x, e.y, e.z, player.x, player.y, player.z + 0.9f);
            if (los || dist < 7f) {
                e.alert = Math.min(1f, e.alert + dt * 2.5f);
            } else {
                e.alert = Math.max(0f, e.alert - dt * 0.35f);
            }

            float tx;
            float ty;
            if (e.alert > 0.25f) {
                if (los) {
                    tx = player.x;
                    ty = player.y;
                } else {
                    flowTarget(e);
                    tx = flowTargetX;
                    ty = flowTargetY;
                }
            } else {
                e.wanderTimer -= dt;
                if (e.wanderTimer <= 0f || Math.hypot(e.targetX - e.x, e.targetY - e.y) < 0.3f) {
                    e.wanderTimer = 3f + rng.nextFloat() * 3f;
                    int cx = rng.nextInt(level.w);
                    int cy = rng.nextInt(level.h);
                    if (level.solid(cx, cy)) {
                        cx = (int) e.spawnX;
                        cy = (int) e.spawnY;
                    }
                    e.targetX = cx + 0.5f;
                    e.targetY = cy + 0.5f;
                }
                tx = e.targetX;
                ty = e.targetY;
            }

            float mdx = tx - e.x;
            float mdy = ty - e.y;
            float md = (float) Math.sqrt(mdx * mdx + mdy * mdy);
            float speed = e.alert > 0.25f ? 1.25f + Math.min(0.85f, levelIndex * 0.055f) : 0.55f;
            float vx = 0f;
            float vy = 0f;
            if (md > 0.02f) {
                vx = mdx / md * speed;
                vy = mdy / md * speed;
            }
            for (Enemy other : enemies) {
                if (other == e || other.dead) continue;
                float sx = e.x - other.x;
                float sy = e.y - other.y;
                float sd = (float) Math.sqrt(sx * sx + sy * sy);
                if (sd > 0.001f && sd < 0.62f) {
                    vx += sx / sd * 0.9f;
                    vy += sy / sd * 0.9f;
                }
            }
            moveEnemy(e, vx * dt, vy * dt);

            float targetZ = level.floorAt(e.x, e.y) + 0.78f;
            e.z += (targetZ - e.z) * Math.min(1f, dt * 6f);
            e.z += (float) Math.sin(e.phase) * 0.0015f;

            float targetYaw = (float) Math.atan2(player.y - e.y, player.x - e.x);
            float diff = targetYaw - e.yaw;
            while (diff > Math.PI) diff -= Math.PI * 2f;
            while (diff < -Math.PI) diff += Math.PI * 2f;
            e.yaw += diff * Math.min(1f, dt * 5f);

            e.attackCooldown -= dt;
            float dz = (player.z + 0.9f) - e.z;
            if (dist < 0.75f && Math.abs(dz) < 1.35f && e.attackCooldown <= 0f) {
                e.attackCooldown = 0.95f;
                damagePlayer(8 + Math.min(10, levelIndex * 2), e.x, e.y);
            }
        }
    }

    private void rebuildFlowIfNeeded(float dt) {
        flowTimer -= dt;
        int cx = (int) player.x;
        int cy = (int) player.y;
        int cell = cy * level.w + cx;
        if (flowTimer <= 0f || cell != lastPlayerCell) {
            level.bfs(cx, cy, flow, queue);
            flowTimer = 0.26f;
            lastPlayerCell = cell;
        }
    }

    private void flowTarget(Enemy e) {
        int cx = (int) e.x;
        int cy = (int) e.y;
        int w = level.w;
        int best = flow[cy * w + cx];
        if (best < 0) best = Integer.MAX_VALUE;
        int bestX = cx;
        int bestY = cy;
        if (cx > 0 && flow[cy * w + cx - 1] >= 0 && flow[cy * w + cx - 1] < best) {
            best = flow[cy * w + cx - 1];
            bestX = cx - 1;
            bestY = cy;
        }
        if (cx < w - 1 && flow[cy * w + cx + 1] >= 0 && flow[cy * w + cx + 1] < best) {
            best = flow[cy * w + cx + 1];
            bestX = cx + 1;
            bestY = cy;
        }
        if (cy > 0 && flow[(cy - 1) * w + cx] >= 0 && flow[(cy - 1) * w + cx] < best) {
            best = flow[(cy - 1) * w + cx];
            bestX = cx;
            bestY = cy - 1;
        }
        if (cy < level.h - 1 && flow[(cy + 1) * w + cx] >= 0 && flow[(cy + 1) * w + cx] < best) {
            bestX = cx;
            bestY = cy + 1;
        }
        flowTargetX = bestX + 0.5f;
        flowTargetY = bestY + 0.5f;
    }

    private void moveEnemy(Enemy e, float dx, float dy) {
        float r = 0.28f;
        if (!level.solidAt(e.x + dx - r, e.y) && !level.solidAt(e.x + dx + r, e.y)
                && !level.solidAt(e.x + dx, e.y - r) && !level.solidAt(e.x + dx, e.y + r)) {
            e.x += dx;
        }
        if (!level.solidAt(e.x, e.y + dy - r) && !level.solidAt(e.x, e.y + dy + r)
                && !level.solidAt(e.x - r, e.y + dy) && !level.solidAt(e.x + r, e.y + dy)) {
            e.y += dy;
        }
    }

    private void damagePlayer(int amount, float fromX, float fromY) {
        if (state != State.PLAYING || cheat) return;
        player.health -= amount;
        player.damageFlash = 1f;
        shake = 1f;
        vibration = Math.max(vibration, 0.7f);
        float dx = player.x - fromX;
        float dy = player.y - fromY;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d > 0.001f) {
            float pushX = dx / d * 0.26f;
            float pushY = dy / d * 0.26f;
            if (canMove(player.x + pushX, player.y)) player.x += pushX;
            if (canMove(player.x, player.y + pushY)) player.y += pushY;
        }
        spark(player.x, player.y, player.z + 0.9f, 6, 0xFFFF4E6E, 1.6f);
        if (player.health <= 0f) {
            player.health = 0f;
            state = State.DEAD;
            stateTimer = 0f;
            burst(player.x, player.y, player.z + 0.9f, 26, 0xFFFF3E5E);
            toast = "SIGNAL LOST";
            toastTimer = 3f;
        }
    }

    private void updatePickups() {
        for (Pickup p : pickups) {
            if (p.taken) continue;
            p.spin += 0.04f;
            float cz = level.floorAt(p.x, p.y) + 0.58f;
            float dx = p.x - player.x;
            float dy = p.y - player.y;
            float dz = cz - (player.z + 0.9f);
            if (dx * dx + dy * dy + dz * dz < 0.85f * 0.85f) {
                p.taken = true;
                coresGot++;
                score += 100;
                ui("+100", 0xFF44FFE2);
                player.health = Math.min(player.maxHealth, player.health + 8f);
                spark(p.x, p.y, cz, 14, 0xFF44FFE2, 2.0f);
                if (coresGot >= coresTotal) {
                    exitActive = true;
                    toast = "PORTAL ONLINE";
                    toastTimer = 3f;
                    hint = "传送门已激活，前往紫色光柱";
                    hintTimer = 5f;
                } else {
                    toast = "CORE " + coresGot + "/" + coresTotal;
                    toastTimer = 1.4f;
                }
            }
        }
    }

    private void updateExit() {
        exitActive = coresGot >= coresTotal;
        if (!exitActive) return;
        float ex = level.exitX + 0.5f;
        float ey = level.exitY + 0.5f;
        float d = (float) Math.hypot(ex - player.x, ey - player.y);
        if (d < 0.9f && Math.abs(player.z - level.floorAt(ex, ey)) < 0.8f) {
            state = State.CLEARED;
            stateTimer = 0f;
            int bonus = 400 + levelIndex * 50;
            score += bonus;
            ui("+" + bonus, 0xFF44FFE2);
            player.health = Math.min(player.maxHealth, player.health + 25f);
            burst(ex, ey, level.floorAt(ex, ey) + 0.9f, 30, 0xFF44FFE2);
            toast = "SECTOR CLEAR";
            toastTimer = 3f;
        }
    }

    private void updateParticles(float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life -= dt;
            if (p.life <= 0f) {
                particles.remove(i);
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;
            p.vz -= 4.5f * dt;
            if (p.z < 0.02f) {
                p.z = 0.02f;
                p.vz *= -0.35f;
                p.vx *= 0.7f;
                p.vy *= 0.7f;
            }
        }
    }

    private void spark(float x, float y, float z, int count, int color, float speed) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            double a = rng.nextFloat() * Math.PI * 2;
            float s = rng.nextFloat() * speed;
            p.x = x;
            p.y = y;
            p.z = z;
            p.vx = (float) Math.cos(a) * s;
            p.vy = (float) Math.sin(a) * s;
            p.vz = rng.nextFloat() * speed;
            p.maxLife = 0.35f + rng.nextFloat() * 0.4f;
            p.life = p.maxLife;
            p.size = 0.035f + rng.nextFloat() * 0.04f;
            p.color = color;
            addParticle(p);
        }
    }

    private void burst(float x, float y, float z, int count, int color) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            double a = rng.nextFloat() * Math.PI * 2;
            float s = 0.4f + rng.nextFloat() * 2.2f;
            p.x = x;
            p.y = y;
            p.z = z;
            p.vx = (float) Math.cos(a) * s;
            p.vy = (float) Math.sin(a) * s;
            p.vz = 0.5f + rng.nextFloat() * 2.2f;
            p.maxLife = 0.5f + rng.nextFloat() * 0.7f;
            p.life = p.maxLife;
            p.size = 0.04f + rng.nextFloat() * 0.06f;
            p.color = color;
            addParticle(p);
        }
    }

    private void addParticle(Particle p) {
        if (particles.size() >= MAX_PARTICLES) {
            particles.remove(0);
        }
        particles.add(p);
    }

    private void buildBillboards() {
        billboardCount = 0;
        float exitFloor = level.floorAt(level.exitX + 0.5f, level.exitY + 0.5f);
        float pulse = exitActive
                ? 1.0f + 0.28f * (float) Math.sin(time * 6f)
                : 0.45f + 0.06f * (float) Math.sin(time * 2f);
        addBillboard(exitActive ? Textures.PORTAL_ON : Textures.PORTAL_OFF,
                level.exitX + 0.5f, level.exitY + 0.5f, exitFloor, 1.9f,
                0xFFFFFFFF, pulse, true);

        for (Pickup p : pickups) {
            if (p.taken) continue;
            float bob = 0.52f + 0.05f * (float) Math.sin(p.spin * 1.7f);
            Billboard3D b = addBillboard(Textures.CORE, p.x, p.y,
                    level.floorAt(p.x, p.y) + bob, 0.42f,
                    0xFFFFFFFF, 1.1f + 0.15f * (float) Math.sin(p.spin * 2.2f), true);
            b.xray = cheat;
        }
        for (Bullet3D b : bullets) {
            if (!b.alive) continue;
            addBillboard(Textures.GLOW, b.x, b.y, b.z - 0.07f, 0.14f,
                    0xFFB8F7FF, 1.25f, true);
            float mx = (b.x + b.px) * 0.5f;
            float my = (b.y + b.py) * 0.5f;
            float mz = (b.z + b.pz) * 0.5f;
            addBillboard(Textures.GLOW, mx, my, mz - 0.04f, 0.08f,
                    0xFF66D9FF, 0.55f, true);
        }
        for (Particle p : particles) {
            float life = p.life / p.maxLife;
            float h = p.size * 2.6f;
            addBillboard(Textures.GLOW, p.x, p.y, p.z - h * 0.5f, h,
                    p.color, Math.min(1.2f, life + 0.25f), true);
        }
    }

    private Billboard3D addBillboard(com.lyq.cpudungeon.engine.Sprite sprite, float x, float y, float z,
                              float height, int tint, float brightness, boolean glow) {
        Billboard3D b;
        if (billboardCount < billboards.size()) {
            b = billboards.get(billboardCount);
        } else {
            b = new Billboard3D();
            billboards.add(b);
        }
        b.set(sprite, x, y, z, height, tint, brightness, glow);
        billboardCount++;
        return b;
    }

    public void levelBfs(int[] dist) {
        level.bfs(level.startX, level.startY, dist, queue);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    private static final class Rng {
        private long state;

        Rng(long seed) {
            state = seed != 0 ? seed : 0x2545F4914F6CDD1DL;
        }

        int nextInt(int bound) {
            state ^= state << 13;
            state ^= state >>> 7;
            state ^= state << 17;
            int v = (int) (state & 0x7FFFFFFFL);
            return bound <= 0 ? 0 : v % bound;
        }

        float nextFloat() {
            return nextInt(1 << 24) / (float) (1 << 24);
        }
    }
}
