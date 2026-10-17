import com.lyq.cpudungeon.engine3d.Camera3D;
import com.lyq.cpudungeon.engine3d.Level3D;
import com.lyq.cpudungeon.engine3d.MapGen3;
import com.lyq.cpudungeon.engine3d.Mesh3D;
import com.lyq.cpudungeon.engine3d.MeshBuilder3D;
import com.lyq.cpudungeon.engine3d.Renderer3D;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public final class Headless3D {
    public static void main(String[] args) throws Exception {
        int w = 480;
        int h = 270;
        File out = new File("tools/out3d");
        out.mkdirs();

        Level3D level = MapGen3.generate(1, 20261017L);
        Mesh3D mesh = MeshBuilder3D.build(level);
        System.out.println("map " + level.w + "x" + level.h + " quads=" + mesh.count
                + " start=(" + level.startX + "," + level.startY + ") exit=(" + level.exitX + "," + level.exitY + ")");
        dumpHeight(level);

        Renderer3D renderer = new Renderer3D();
        Camera3D cam = new Camera3D();
        cam.x = level.startX + 0.5f;
        cam.y = level.startY + 0.5f;
        cam.z = level.floorAt(cam.x, cam.y) + 1.62f;
        cam.yaw = 0f;
        cam.pitch = 0f;
        cam.update(w);

        int[] fb = new int[w * h];
        renderer.render(mesh, cam, fb, w, h, 0f);
        save(fb, w, h, new File(out, "m1_forward.png"));

        cam.pitch = 0.45f;
        cam.update(w);
        renderer.render(mesh, cam, fb, w, h, 0f);
        save(fb, w, h, new File(out, "m1_up.png"));

        cam.pitch = -0.35f;
        cam.yaw = 1.1f;
        cam.update(w);
        renderer.render(mesh, cam, fb, w, h, 0f);
        save(fb, w, h, new File(out, "m1_down_left.png"));

        // benchmark: fly through the level while rotating
        cam.pitch = 0f;
        long t0 = System.nanoTime();
        int frames = 240;
        for (int i = 0; i < frames; i++) {
            cam.yaw += 0.01f;
            cam.x += 0.01f;
            cam.update(w);
            renderer.render(mesh, cam, fb, w, h, i / 60f);
        }
        long t1 = System.nanoTime();
        System.out.printf("render: %.2f ms/frame at %dx%d%n", (t1 - t0) / 1e6 / frames, w, h);
        save(fb, w, h, new File(out, "m1_bench.png"));
    }

    private static void dumpHeight(Level3D level) {
        System.out.println("heights (0-4 = 0.00-1.00):");
        for (int y = 0; y < level.h; y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < level.w; x++) {
                if (level.solid(x, y)) {
                    sb.append('#');
                } else {
                    sb.append((char) ('0' + Math.round(level.floorZ[y * level.w + x] * 4f)));
                }
            }
            System.out.println(sb);
        }
    }

    private static void save(int[] fb, int w, int h, File file) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, w, h, fb, 0, w);
        ImageIO.write(img, "png", file);
    }
}
