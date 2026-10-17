import com.lyq.cpudungeon.engine.Enemy;
import com.lyq.cpudungeon.engine3d.Camera3D;
import com.lyq.cpudungeon.engine3d.EnemyModel;
import com.lyq.cpudungeon.engine3d.Level3D;
import com.lyq.cpudungeon.engine3d.Mesh3D;
import com.lyq.cpudungeon.engine3d.MeshBuilder3D;
import com.lyq.cpudungeon.engine3d.Renderer3D;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public final class ModelTest {
    public static void main(String[] args) throws Exception {
        int w = 1280, h = 720, gw = 22, gh = 16;
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
        Level3D level = new Level3D(gw, gh, solid, z, tex, 2, 8, 18, 8);
        Mesh3D staticMesh = MeshBuilder3D.build(level);
        Mesh3D enemies = new Mesh3D();
        Enemy e = new Enemy(7.5f, 8.0f, 0.5f);
        e.z = 0.78f;
        e.yaw = (float) Math.PI;
        EnemyModel.build(e, level, enemies, false);
        System.out.println("static quads=" + staticMesh.count + " enemy quads=" + enemies.count);

        Camera3D cam = new Camera3D();
        cam.x = 3.0f; cam.y = 8.0f; cam.z = 1.62f; cam.yaw = 0f; cam.pitch = 0f;
        cam.update(w);
        Renderer3D r = new Renderer3D();
        int[] fb = new int[w * h];
        r.renderAll(staticMesh, enemies, null, 0, cam, fb, w, h, 0f);
        save(fb, w, h, new File("tools/model_720.png"));

        long t0 = System.nanoTime();
        int frames = 30;
        for (int i = 0; i < frames; i++) {
            enemies.beginFrame();
            e.yaw += 0.05f;
            e.phase += 0.05f;
            EnemyModel.build(e, level, enemies, false);
            cam.update(w);
            r.renderAll(staticMesh, enemies, null, 0, cam, fb, w, h, i / 60f);
        }
        long t1 = System.nanoTime();
        System.out.printf("720p static+model: %.2f ms/frame%n", (t1 - t0) / 1e6 / frames);
    }

    private static void save(int[] fb, int w, int h, File f) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, w, h, fb, 0, w);
        ImageIO.write(img, "png", f);
    }
}
