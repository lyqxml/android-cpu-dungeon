import com.lyq.cpudungeon.engine.Textures;
import com.lyq.cpudungeon.engine3d.Billboard3D;
import com.lyq.cpudungeon.engine3d.Camera3D;
import com.lyq.cpudungeon.engine3d.Level3D;
import com.lyq.cpudungeon.engine3d.Mesh3D;
import com.lyq.cpudungeon.engine3d.MeshBuilder3D;
import com.lyq.cpudungeon.engine3d.Renderer3D;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Controlled test room: platform + stairs + pillars for visual verification. */
public final class Scene3D {
    public static void main(String[] args) throws Exception {
        int w = 480;
        int h = 270;
        int gw = 22;
        int gh = 16;
        byte[] solid = new byte[gw * gh];
        float[] z = new float[gw * gh];
        byte[] tex = new byte[gw * gh];
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                boolean border = x == 0 || y == 0 || x == gw - 1 || y == gh - 1;
                solid[y * gw + x] = (byte) (border ? 1 : 0);
                tex[y * gw + x] = (byte) ((x + y) & 3);
            }
        }
        // raised platform
        for (int y = 4; y <= 11; y++) {
            for (int x = 12; x <= 18; x++) {
                z[y * gw + x] = 0.75f;
            }
        }
        // stairs
        for (int y = 5; y <= 10; y++) {
            z[y * gw + 11] = 0.5f;
            z[y * gw + 10] = 0.25f;
        }
        // pillars
        solid[6 * gw + 7] = 1;
        solid[10 * gw + 7] = 1;
        solid[8 * gw + 15] = 1;
        z[8 * gw + 15] = 0.75f;

        Level3D level = new Level3D(gw, gh, solid, z, tex, 2, 8, 18, 8);
        Mesh3D mesh = MeshBuilder3D.build(level);
        System.out.println("quads=" + mesh.count);

        Camera3D cam = new Camera3D();
        cam.x = 3.2f;
        cam.y = 8.2f;
        cam.z = 1.62f;
        cam.yaw = 0f;
        cam.pitch = 0f;

        Renderer3D renderer = new Renderer3D();
        int[] fb = new int[w * h];
        List<Billboard3D> billboards = new ArrayList<>();
        Billboard3D drone = new Billboard3D();
        drone.set(Textures.DRONE, 7.5f, 8.0f, 0.35f, 0.9f, 0xFFFFFFFF, 1.15f, false);
        billboards.add(drone);
        Billboard3D core = new Billboard3D();
        core.set(Textures.CORE, 7.0f, 6.4f, 0.55f, 0.42f, 0xFFFFFFFF, 1.2f, true);
        billboards.add(core);
        Billboard3D portal = new Billboard3D();
        portal.set(Textures.PORTAL_ON, 17.5f, 8.0f, 0.75f, 1.9f, 0xFFFFFFFF, 1.15f, true);
        billboards.add(portal);

        cam.update(w);
        renderer.render(mesh, cam, billboards, fb, w, h, 0f);
        save(fb, w, h, new File("tools/out3d/scene_forward.png"));

        cam.pitch = 0.30f;
        cam.update(w);
        renderer.render(mesh, cam, billboards, fb, w, h, 0f);
        save(fb, w, h, new File("tools/out3d/scene_up.png"));

        cam.pitch = -0.25f;
        cam.yaw = 0.30f;
        cam.update(w);
        renderer.render(mesh, cam, billboards, fb, w, h, 0f);
        save(fb, w, h, new File("tools/out3d/scene_down.png"));

        long t0 = System.nanoTime();
        int frames = 300;
        for (int i = 0; i < frames; i++) {
            cam.yaw += 0.008f;
            cam.pitch = 0.15f * (float) Math.sin(i * 0.03);
            cam.update(w);
            renderer.render(mesh, cam, billboards, fb, w, h, i / 60f);
        }
        long t1 = System.nanoTime();
        System.out.printf("render: %.2f ms/frame at %dx%d%n", (t1 - t0) / 1e6 / frames, w, h);
    }

    private static void save(int[] fb, int w, int h, File file) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, w, h, fb, 0, w);
        ImageIO.write(img, "png", file);
    }
}
