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

public final class XrayTest {
    public static void main(String[] args) throws Exception {
        int w = 480, h = 270, gw = 16, gh = 10;
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
        // full-height blocker wall between camera and drone
        for (int y = 3; y <= 7; y++) {
            solid[y * gw + 6] = 1;
        }
        Level3D level = new Level3D(gw, gh, solid, z, tex, 2, 5, 14, 5);
        Mesh3D mesh = MeshBuilder3D.build(level);

        Camera3D cam = new Camera3D();
        cam.x = 3.0f; cam.y = 5.5f; cam.z = 1.62f; cam.yaw = 0f; cam.pitch = 0f;
        cam.update(w);

        List<Billboard3D> list = new ArrayList<>();
        Billboard3D drone = new Billboard3D();
        drone.set(Textures.DRONE, 9.0f, 5.5f, 0.45f, 0.9f, 0xFFFFFFFF, 1.1f, false);
        list.add(drone);

        Renderer3D r = new Renderer3D();
        int[] fb = new int[w * h];
        r.render(mesh, cam, list, fb, w, h, 0f);
        save(fb, w, h, new File("tools/xray_normal.png"));

        drone.tint = 0xFFFF7A9C;
        drone.xray = true;
        r.render(mesh, cam, list, fb, w, h, 0f);
        save(fb, w, h, new File("tools/xray_on.png"));
        System.out.println("xray test done");
    }

    private static void save(int[] fb, int w, int h, File f) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, w, h, fb, 0, w);
        ImageIO.write(img, "png", f);
    }
}
