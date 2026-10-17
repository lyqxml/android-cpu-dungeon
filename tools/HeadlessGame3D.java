import com.lyq.cpudungeon.engine3d.Camera3D;
import com.lyq.cpudungeon.engine3d.Game3D;
import com.lyq.cpudungeon.engine3d.Renderer3D;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public final class HeadlessGame3D {
    public static void main(String[] args) throws Exception {
        int w = 1280;
        int h = 720;
        File out = new File("tools/out3d");
        out.mkdirs();

        Game3D game = new Game3D(20261017L);
        Renderer3D renderer = new Renderer3D();
        Camera3D cam = new Camera3D();
        int[] fb = new int[w * h];

        simulate(game, cam, renderer, fb, w, h, out, "g3d_start.png", 0);
        game.input.moveY = 1f;
        game.input.look = 0.004f;
        for (int i = 0; i < 140; i++) {
            game.update(1f / 60f);
        }
        game.input.moveY = 0f;
        game.input.look = 0f;
        simulate(game, cam, renderer, fb, w, h, out, "g3d_walk.png", 0f);

        // look up and down
        game.input.pitch = 0.6f;
        game.update(1f / 60f);
        simulate(game, cam, renderer, fb, w, h, out, "g3d_up.png", 0f);
        game.input.pitch = -0.8f;
        game.update(1f / 60f);
        simulate(game, cam, renderer, fb, w, h, out, "g3d_down.png", 0f);

        // jump keeps the camera above the floor
        game.player.pitch = 0f;
        game.input.jumpTap = true;
        game.update(1f / 60f);
        float peak = game.player.z;
        for (int i = 0; i < 20; i++) {
            game.update(1f / 60f);
            peak = Math.max(peak, game.player.z);
        }
        System.out.println("jump: z=" + game.player.z + " peak=" + peak + " grounded=" + game.player.grounded);

        // crouch lowers the eye
        game.input.crouch = true;
        for (int i = 0; i < 20; i++) {
            game.update(1f / 60f);
        }
        System.out.println("crouch eye=" + game.player.eyeHeight);
        game.input.crouch = false;
        for (int i = 0; i < 20; i++) {
            game.update(1f / 60f);
        }
        System.out.println("stand eye=" + game.player.eyeHeight);

        long t0 = System.nanoTime();
        int frames = 240;
        for (int i = 0; i < frames; i++) {
            game.input.look = 0.006f;
            game.update(1f / 60f);
            cam.x = game.player.x;
            cam.y = game.player.y;
            cam.z = game.player.z + game.player.eyeHeight;
            cam.yaw = game.player.yaw;
            cam.pitch = game.player.pitch;
            cam.update(w);
            renderer.renderAll(game.mesh, game.enemyMesh, game.billboards, game.billboardCount, cam, fb, w, h, game.time);
        }
        long t1 = System.nanoTime();
        System.out.printf("game+render: %.2f ms/frame at %dx%d (quads=%d sprites=%d)%n",
                (t1 - t0) / 1e6 / frames, w, h, game.mesh.count, game.billboardCount);
        System.out.println("state=" + game.state + " level=" + game.levelIndex + " score=" + game.score
                + " cores=" + game.coresGot + "/" + game.coresTotal + " hp=" + game.player.health);
    }

    private static void simulate(Game3D game, Camera3D cam, Renderer3D r, int[] fb, int w, int h,
                                 File out, String name, float unused) throws Exception {
        cam.x = game.player.x;
        cam.y = game.player.y;
        cam.z = game.player.z + game.player.eyeHeight;
        cam.yaw = game.player.yaw;
        cam.pitch = game.player.pitch;
        cam.update(w);
        r.renderAll(game.mesh, game.enemyMesh, game.billboards, game.billboardCount, cam, fb, w, h, game.time);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, w, h, fb, 0, w);
        ImageIO.write(img, "png", new File(out, name));
    }
}
