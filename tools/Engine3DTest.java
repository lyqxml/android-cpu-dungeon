import com.lyq.cpudungeon.engine3d.Game3D;
import com.lyq.cpudungeon.engine3d.Level3D;
import com.lyq.cpudungeon.engine3d.MapGen3;
import com.lyq.cpudungeon.engine3d.Mesh3D;
import com.lyq.cpudungeon.engine3d.MeshBuilder3D;

public final class Engine3DTest {
    private static int checks;

    public static void main(String[] args) {
        heightsAreWalkable();
        playerPhysics();
        simulationStability();
        System.out.println("ALL 3D CHECKS PASS (" + checks + " assertions)");
    }

    private static void heightsAreWalkable() {
        int variations = 0;
        for (int levelIndex = 1; levelIndex <= 8; levelIndex++) {
            for (long seed = 1; seed <= 12; seed++) {
                Level3D lv = MapGen3.generate(levelIndex, seed * 7919L);
                float maxHeight = 0f;
                for (int y = 0; y < lv.h; y++) {
                    for (int x = 0; x < lv.w; x++) {
                        int i = y * lv.w + x;
                        if (lv.solid[i] != 0) continue;
                        maxHeight = Math.max(maxHeight, lv.floorZ[i]);
                        if (x + 1 < lv.w && lv.solid[i + 1] == 0) {
                            float diff = Math.abs(lv.floorZ[i] - lv.floorZ[i + 1]);
                            check(diff <= Level3D.STEP + 0.011f, "step too high between cells: " + diff);
                        }
                        if (y + 1 < lv.h && lv.solid[i + lv.w] == 0) {
                            float diff = Math.abs(lv.floorZ[i] - lv.floorZ[i + lv.w]);
                            check(diff <= Level3D.STEP + 0.011f, "step too high between cells: " + diff);
                        }
                    }
                }
                if (maxHeight > 0.5f) variations++;
                // height-aware reachability
                boolean[] seen = new boolean[lv.w * lv.h];
                int[] queue = new int[lv.w * lv.h];
                int head = 0, tail = 0;
                int start = lv.startY * lv.w + lv.startX;
                seen[start] = true;
                queue[tail++] = start;
                while (head < tail) {
                    int cell = queue[head++];
                    int cx = cell % lv.w;
                    int cy = cell / lv.w;
                    int[] nx = {cx - 1, cx + 1, cx, cx};
                    int[] ny = {cy, cy, cy - 1, cy + 1};
                    for (int k = 0; k < 4; k++) {
                        if (nx[k] < 0 || ny[k] < 0 || nx[k] >= lv.w || ny[k] >= lv.h) continue;
                        int n = ny[k] * lv.w + nx[k];
                        if (seen[n] || lv.solid[n] != 0) continue;
                        if (Math.abs(lv.floorZ[n] - lv.floorZ[cell]) > Level3D.STEP + 0.011f) continue;
                        seen[n] = true;
                        queue[tail++] = n;
                    }
                }
                int floors = 0;
                for (int i = 0; i < seen.length; i++) {
                    if (lv.solid[i] == 0) {
                        check(seen[i], "unreachable floor cell at level " + levelIndex);
                        floors++;
                    }
                }
                check(floors > lv.w * lv.h / 3, "map too sparse");
                check(seen[lv.exitY * lv.w + lv.exitX], "exit unreachable");
                Mesh3D mesh = MeshBuilder3D.build(lv);
                check(mesh.count > 100, "mesh too small");
            }
        }
        check(variations > 40, "not enough height variation across maps: " + variations);
        System.out.println("heights ok, maps with >0.5m elevation: " + variations);
    }

    private static void playerPhysics() {
        Game3D game = new Game3D(424242L);
        float startZ = game.player.z;
        game.input.jumpTap = true;
        float peak = startZ;
        for (int i = 0; i < 80; i++) {
            game.update(1f / 60f);
            peak = Math.max(peak, game.player.z);
        }
        check(peak - startZ > 0.55f, "jump too low: " + (peak - startZ));
        check(game.player.grounded, "player did not land");
        check(Math.abs(game.player.z - startZ) < 0.35f, "player drifted vertically");

        game.input.crouch = true;
        for (int i = 0; i < 40; i++) game.update(1f / 60f);
        check(game.player.eyeHeight < 1.1f, "crouch eye not lowered");
        game.input.crouch = false;
        for (int i = 0; i < 40; i++) game.update(1f / 60f);
        check(game.player.eyeHeight > 1.5f, "stand eye not restored");
        System.out.println("physics ok, jump peak +" + (peak - startZ));
    }

    private static void simulationStability() {
        Game3D game = new Game3D(998877L);
        for (int i = 0; i < 2400; i++) {
            game.input.moveY = (i / 150) % 2 == 0 ? 1f : -1f;
            game.input.moveX = (float) Math.sin(i * 0.04f);
            game.input.look = 0.02f;
            game.input.pitch = (float) Math.sin(i * 0.01f) * 0.01f;
            if (i % 30 == 0) game.input.jumpTap = true;
            if (i % 20 == 0) game.input.fireTap = true;
            if (i % 180 == 0) game.input.crouch = !game.input.crouch;
            game.update(1f / 60f);
            check(!Float.isNaN(game.player.x) && !Float.isNaN(game.player.z), "NaN position");
            check(game.player.z >= -0.01f, "player under floor");
            check(game.player.health >= 0f && game.player.health <= 100f, "health out of range");
        }
        System.out.println("simulation ok: sector=" + game.levelIndex + " score=" + game.score
                + " state=" + game.state + " hp=" + game.player.health);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
