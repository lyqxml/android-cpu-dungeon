import com.lyq.cpudungeon.engine3d.Camera3D;
import com.lyq.cpudungeon.engine3d.Level3D;
import com.lyq.cpudungeon.engine3d.Mesh3D;
import com.lyq.cpudungeon.engine3d.MeshBuilder3D;
import com.lyq.cpudungeon.engine3d.PostFx;
import com.lyq.cpudungeon.engine3d.Renderer3D;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public final class AaTest {
    public static void main(String[] args) throws Exception {
        int w = 1280, h = 720, gw = 20, gh = 14;
        byte[] solid = new byte[gw * gh];
        float[] z = new float[gw * gh];
        byte[] tex = new byte[gw * gh];
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                solid[y * gw + x] = (byte) (x == 0 || y == 0 || x == gw - 1 || y == gh - 1 ? 1 : 0);
                tex[y * gw + x] = (byte) ((x * 3 + y) & 3);
            }
        }
        Level3D level = new Level3D(gw, gh, solid, z, tex, 2, 7, 16, 7);
        Mesh3D mesh = MeshBuilder3D.build(level);
        Camera3D cam = new Camera3D();
        cam.x = 4f; cam.y = 7f; cam.z = 1.62f; cam.yaw = 0.35f; cam.update(w);
        Renderer3D r = new Renderer3D();
        int[] fb = new int[w * h];
        r.render(mesh, cam, fb, w, h, 0f);
        save(fb, w, h, new File("tools/aa_off.png"));

        PostFx fx = new PostFx();
        for (int i = 0; i < 5; i++) {
            fx.antiAlias(fb, w, h);
        }
        long t0 = System.nanoTime();
        int loops = 20;
        for (int i = 0; i < loops; i++) {
            fx.antiAlias(fb, w, h);
        }
        long t1 = System.nanoTime();
        System.out.printf("FXAA: %.2f ms at %dx%d (steady)%n", (t1 - t0) / 1e6 / loops, w, h);
        save(fb, w, h, new File("tools/aa_on.png"));
    }

    private static void save(int[] fb, int w, int h, File f) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, w, h, fb, 0, w);
        ImageIO.write(img, "png", f);
    }
}
