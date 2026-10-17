import com.lyq.cpudungeon.engine.Enemy;
import com.lyq.cpudungeon.engine.Game;
import com.lyq.cpudungeon.engine.MapData;
import com.lyq.cpudungeon.engine.MapGen;
import com.lyq.cpudungeon.engine.Pickup;

/** Dependency-free sanity suite: run with `java -cp build EngineTest`. */
public final class EngineTest {
    private static int checks;

    public static void main(String[] args) {
        mapsAreConnected();
        pickupsAreReachableAndSolidFree();
        simulationStaysSane();
        hitscanKillsDrone();
        System.out.println("ALL PASS (" + checks + " checks)");
    }

    private static void mapsAreConnected() {
        for (int level = 1; level <= 8; level++) {
            for (long seed = 1; seed <= 20; seed++) {
                MapData map = MapGen.generate(level, seed * 7919L);
                int[] dist = new int[map.w * map.h];
                int[] queue = new int[map.w * map.h];
                map.bfs(map.startX, map.startY, dist, queue);
                int floors = 0;
                for (int i = 0; i < dist.length; i++) {
                    if (map.tiles[i] == 0) {
                        floors++;
                        check(dist[i] >= 0, "unreachable floor at level " + level + " seed " + seed);
                    }
                }
                check(floors > map.w * map.h / 3, "maze too sparse");
                check(map.exitX != map.startX || map.exitY != map.startY, "exit equals start");
            }
        }
        System.out.println("map connectivity ok");
    }

    private static void pickupsAreReachableAndSolidFree() {
        for (int level = 1; level <= 6; level++) {
            Game game = new Game(level * 104729L);
            check(!game.pickups.isEmpty(), "no pickups generated");
            int[] dist = new int[game.map.w * game.map.h];
            int[] queue = new int[game.map.w * game.map.h];
            game.map.bfs(game.map.startX, game.map.startY, dist, queue);
            for (Pickup p : game.pickups) {
                check(!game.map.solidAt(p.x, p.y), "pickup inside wall");
                int idx = (int) p.y * game.map.w + (int) p.x;
                check(dist[idx] >= 0, "pickup unreachable");
            }
            for (Enemy e : game.enemies) {
                check(!game.map.solidAt(e.x, e.y), "enemy inside wall");
            }
        }
        System.out.println("spawn placement ok");
    }

    private static void simulationStaysSane() {
        Game game = new Game(42424242L);
        for (int i = 0; i < 1800; i++) {
            game.input.moveY = (i / 120) % 2 == 0 ? 1f : -1f;
            game.input.moveX = (float) Math.sin(i * 0.05f);
            game.input.look = 0.02f;
            if (i % 40 == 0) {
                game.input.fireTap = true;
            }
            game.update(1f / 60f);
            check(!Float.isNaN(game.player.x) && !Float.isNaN(game.player.y), "NaN position");
            check(!game.map.hitsCircle(game.player.x, game.player.y, 0.05f), "player inside wall");
            check(game.player.health >= 0f && game.player.health <= 100f, "health out of range");
        }
        System.out.println("simulation ok: level=" + game.level + " score=" + game.score
                + " state=" + game.state + " particles=" + game.particles.size());
    }

    private static void hitscanKillsDrone() {
        Game game = new Game(777L);
        game.enemies.clear();
        game.pickups.clear();
        game.coresTotal = 0;
        float dx = (float) Math.cos(game.player.angle);
        float dy = (float) Math.sin(game.player.angle);
        Enemy e = new Enemy(game.player.x + dx * 2.5f, game.player.y + dy * 2.5f, 0f);
        game.enemies.add(e);
        int shots = 0;
        for (int i = 0; i < 40 && !e.dead; i++) {
            game.input.fireHeld = true;
            game.update(1f / 60f);
            if (game.player.fireCooldown > Game.FIRE_INTERVAL - 0.01f) {
                shots++;
            }
        }
        check(e.dead, "three hitscan shots should kill a drone");
        check(shots >= 3, "expected at least 3 shots, got " + shots);
        System.out.println("combat ok: shots=" + shots + " kills=" + game.kills + " score=" + game.score);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
