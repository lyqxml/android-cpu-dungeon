package com.lyq.cpudungeon.engine;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Simulation layer: player movement, hitscan combat, drone AI (BFS flow field),
 * pickups, particles and level progression. Contains no Android types, so it can
 * be compiled and rendered headlessly by tools/HeadlessRender.java.
 */
public final class Game {
    public enum State { PLAYING, CLEARED, DEAD }

    public static final float PLAYER_RADIUS = 0.24f;
    public static final float ENEMY_RADIUS = 0.28f;
    public static final float MOVE_SPEED = 3.15f;
    public static final float FIRE_INTERVAL = 0.24f;
    public static final int MAX_PARTICLES = 240;

    public final Input input = new Input();
    public final ArrayList<Enemy> enemies = new ArrayList<>();
    public final ArrayList<Pickup> pickups = new ArrayList<>();
    public final ArrayList<Particle> particles = new ArrayList<>();

    public MapData map;
    public final Player player = new Player();
    public State state = State.PLAYING;

    public int level = 1;
    public int score;
    public int coresTotal;
    public int coresGot;
    public int kills;
    public float time;
    public float stateTimer;
    public float toastTimer;
    public float hintTimer;
    public float vibration;
    public boolean exitActive;

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

    public Game(long seed) {
        this.seed = seed;
        startLevel(1);
    }

    public void restart() {
        score = 0;
        kills = 0;
        level = 1;
        startLevel(1);
    }

    private void startLevel(int lvl) {
        level = lvl;
        rng = new Rng(seed ^ (lvl * 0x9E3779B97F4A7C15L));
        map = MapGen.generate(lvl, seed + lvl * 7919L);
        player.reset(map.startX + 0.5f, map.startY + 0.5f, spawnAngle(map));
        enemies.clear();
        pickups.clear();
        particles.clear();
        coresGot = 0;
        exitActive = false;
        state = State.PLAYING;
        stateTimer = 0f;
        time = 0f;
        flowTimer = 0f;
        lastPlayerCell = -1;
        flow = new int[map.w * map.h];
        queue = new int[map.w * map.h];

        int[] dist = new int[map.w * map.h];
        map.bfs(map.startX, map.startY, dist, queue);

        int available = 0;
        for (int d : dist) {
            if (d >= 0) {
                available++;
            }
        }
        coresTotal = Math.min(8, 4 + (lvl + 1) / 2);

        int guard = 0;
        while (pickups.size() < coresTotal && guard++ < 800) {
            int idx = randomFloorCell(dist, 3);
            if (idx < 0) {
                break;
            }
            int cx = idx % map.w;
            int cy = idx / map.w;
            if (cx == map.exitX && cy == map.exitY) {
                continue;
            }
            boolean ok = true;
            for (Pickup p : pickups) {
                if (Math.hypot(p.x - (cx + 0.5f), p.y - (cy + 0.5f)) < 3.0f) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                pickups.add(new Pickup(cx + 0.5f, cy + 0.5f, guard * 0.7f));
            }
        }
        if (pickups.isEmpty()) {
            // defensive fallback: never let a map spawn without a reachable core
            pickups.add(new Pickup(map.exitX + 0.5f, map.exitY + 0.5f, 0f));
        }
        coresTotal = pickups.size();

        int enemyCount = Math.min(14, 3 + lvl);
        guard = 0;
        while (enemies.size() < enemyCount && guard++ < 800) {
            int idx = randomFloorCell(dist, 6);
            if (idx < 0) {
                break;
            }
            int cx = idx % map.w;
            int cy = idx / map.w;
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
                enemies.add(new Enemy(ex, ey, guard * 1.3f));
            }
        }

        toast = "SECTOR " + lvl;
        toastTimer = 2.2f;
        if (lvl == 1) {
            hint = "左摇杆移动 · 右半屏滑动转向 · 按住 FIRE 开火";
            hintTimer = 9f;
        } else {
            hint = "";
            hintTimer = 0f;
        }
        if (available < 4) {
            hint = "生成异常，请重开";
            hintTimer = 3f;
        }
    }

    private float spawnAngle(MapData m) {
        int sx = m.startX;
        int sy = m.startY;
        if (!m.solid(sx + 1, sy)) return 0f;
        if (!m.solid(sx, sy + 1)) return (float) (Math.PI * 0.5f);
        if (!m.solid(sx - 1, sy)) return (float) Math.PI;
        return (float) (-Math.PI * 0.5f);
    }

    private int randomFloorCell(int[] dist, int minDist) {
        for (int attempt = 0; attempt < 120; attempt++) {
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
        if (vibration > 0) vibration = Math.max(0f, vibration - dt * 4f);

        updateParticles(dt);

        if (state == State.PLAYING) {
            updatePlayer(dt);
            updateEnemies(dt);
            updatePickups();
            updateExit();
        } else if (state == State.CLEARED) {
            stateTimer += dt;
            if (stateTimer > 1.7f) {
                int next = level + 1;
                startLevel(next);
            }
        } else if (state == State.DEAD) {
            stateTimer += dt;
            if (input.restartTap) {
                restart();
            }
        }
        input.clearTransient();
    }

    private void updatePlayer(float dt) {
        player.angle += input.look;
        float dirX = (float) Math.cos(player.angle);
        float dirY = (float) Math.sin(player.angle);
        float mx = clamp(input.moveX, -1f, 1f);
        float my = clamp(input.moveY, -1f, 1f);
        float vx = (dirX * my - dirY * mx) * MOVE_SPEED;
        float vy = (dirY * my + dirX * mx) * MOVE_SPEED;
        float mag = (float) Math.sqrt(vx * vx + vy * vy);
        if (mag > MOVE_SPEED) {
            vx = vx / mag * MOVE_SPEED;
            vy = vy / mag * MOVE_SPEED;
            mag = MOVE_SPEED;
        }
        movePlayer(vx * dt, vy * dt);

        float target = mag / MOVE_SPEED;
        player.moveSpeed += (target - player.moveSpeed) * Math.min(1f, dt * 9f);
        player.bob += dt * (5f + 7f * player.moveSpeed) * player.moveSpeed;

        player.fireCooldown -= dt;
        if ((input.fireHeld || input.fireTap) && player.fireCooldown <= 0f) {
            shoot();
        }
    }

    private void movePlayer(float dx, float dy) {
        if (!map.hitsCircle(player.x + dx, player.y, PLAYER_RADIUS)) {
            player.x += dx;
        }
        if (!map.hitsCircle(player.x, player.y + dy, PLAYER_RADIUS)) {
            player.y += dy;
        }
    }

    private void shoot() {
        player.fireCooldown = FIRE_INTERVAL;
        player.recoil = 1f;
        player.muzzle = 1f;
        float dirX = (float) Math.cos(player.angle);
        float dirY = (float) Math.sin(player.angle);
        Enemy best = null;
        float bestT = Float.MAX_VALUE;
        for (Enemy e : enemies) {
            if (e.dead) continue;
            float dx = e.x - player.x;
            float dy = e.y - player.y;
            float t = dx * dirX + dy * dirY;
            if (t < 0.15f || t > 17f) continue;
            float perp = Math.abs(-dx * dirY + dy * dirX);
            if (perp > 0.42f) continue;
            if (!hasLineOfSight(player.x, player.y, e.x, e.y)) continue;
            if (t < bestT) {
                bestT = t;
                best = e;
            }
        }
        if (best != null) {
            best.hp--;
            best.hitFlash = 0.16f;
            best.alert = 1f;
            spark(best.x, best.y, 0.55f, 7, 0xFFFF63A8, 2.4f);
            if (best.hp <= 0) {
                best.dead = true;
                kills++;
                score += 60;
                burst(best.x, best.y, 0.5f, 20, 0xFFFF3E8A);
                vibration = Math.max(vibration, 0.25f);
            }
        } else {
            float t = 0.6f;
            while (t < 17f) {
                float x = player.x + dirX * t;
                float y = player.y + dirY * t;
                if (map.solidAt(x, y)) {
                    break;
                }
                t += 0.07f;
            }
            spark(player.x + dirX * t, player.y + dirY * t, 0.5f, 4, 0xFF7CE7FF, 1.1f);
        }
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
            boolean los = dist < 13f && hasLineOfSight(e.x, e.y, player.x, player.y);
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
                    int cx = rng.nextInt(map.w);
                    int cy = rng.nextInt(map.h);
                    if (map.solid(cx, cy)) {
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
            float speed = e.alert > 0.25f ? 1.25f + Math.min(0.85f, level * 0.055f) : 0.55f;
            float vx = 0f;
            float vy = 0f;
            if (md > 0.02f) {
                vx = mdx / md * speed;
                vy = mdy / md * speed;
            }
            // keep drones from stacking into one blob
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

            e.attackCooldown -= dt;
            if (dist < 0.74f && e.attackCooldown <= 0f) {
                e.attackCooldown = 0.95f;
                damagePlayer(8 + Math.min(10, level * 2), e.x, e.y);
            }
        }
    }

    private void rebuildFlowIfNeeded(float dt) {
        flowTimer -= dt;
        int cx = (int) player.x;
        int cy = (int) player.y;
        int cell = cy * map.w + cx;
        if (flowTimer <= 0f || cell != lastPlayerCell) {
            map.bfs(cx, cy, flow, queue);
            flowTimer = 0.26f;
            lastPlayerCell = cell;
        }
    }

    private void flowTarget(Enemy e) {
        int cx = (int) e.x;
        int cy = (int) e.y;
        int w = map.w;
        int best = flow[cy * w + cx];
        if (best < 0) {
            best = Integer.MAX_VALUE;
        }
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
        if (cy < map.h - 1 && flow[(cy + 1) * w + cx] >= 0 && flow[(cy + 1) * w + cx] < best) {
            bestX = cx;
            bestY = cy + 1;
        }
        flowTargetX = bestX + 0.5f;
        flowTargetY = bestY + 0.5f;
    }

    private void moveEnemy(Enemy e, float dx, float dy) {
        if (!map.hitsCircle(e.x + dx, e.y, ENEMY_RADIUS)) {
            e.x += dx;
        }
        if (!map.hitsCircle(e.x, e.y + dy, ENEMY_RADIUS)) {
            e.y += dy;
        }
    }

    private void damagePlayer(int amount, float fromX, float fromY) {
        if (state != State.PLAYING) return;
        player.health -= amount;
        player.damageFlash = 1f;
        vibration = Math.max(vibration, 0.7f);
        float dx = player.x - fromX;
        float dy = player.y - fromY;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d > 0.001f) {
            movePlayer(dx / d * 0.26f, dy / d * 0.26f);
        }
        spark(player.x, player.y, 0.6f, 6, 0xFFFF4E6E, 1.6f);
        if (player.health <= 0f) {
            player.health = 0f;
            state = State.DEAD;
            stateTimer = 0f;
            burst(player.x, player.y, 0.5f, 26, 0xFFFF3E5E);
            toast = "SIGNAL LOST";
            toastTimer = 3f;
        }
    }

    private void updatePickups() {
        for (Pickup p : pickups) {
            if (p.taken) continue;
            p.spin += 0.04f;
            float d = (float) Math.hypot(p.x - player.x, p.y - player.y);
            if (d < 0.48f) {
                p.taken = true;
                coresGot++;
                score += 100;
                player.health = Math.min(player.maxHealth, player.health + 8f);
                spark(p.x, p.y, 0.5f, 14, 0xFF44FFE2, 2.0f);
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
        float d = (float) Math.hypot(map.exitX + 0.5f - player.x, map.exitY + 0.5f - player.y);
        if (d < 0.9f) {
            state = State.CLEARED;
            stateTimer = 0f;
            score += 400 + level * 50;
            player.health = Math.min(player.maxHealth, player.health + 25f);
            burst(map.exitX + 0.5f, map.exitY + 0.5f, 0.7f, 30, 0xFF44FFE2);
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
            if (p.z < 0f) {
                p.z = 0f;
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

    public boolean hasLineOfSight(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        int steps = (int) (d / 0.11f) + 1;
        for (int i = 1; i < steps; i++) {
            float t = i / (float) steps;
            if (map.solidAt(x0 + dx * t, y0 + dy * t)) {
                return false;
            }
        }
        return true;
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
