import com.lyq.cpudungeon.engine.Enemy;
import com.lyq.cpudungeon.engine.Game;
import com.lyq.cpudungeon.engine.Pickup;
import com.lyq.cpudungeon.engine.Raycaster;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public final class Showcase {
    public static void main(String[] args) throws Exception {
        int w = 480;
        int h = 270;
        Game game = new Game(20261017L);
        Raycaster r = new Raycaster();
        int[] fb = new int[w * h];
        game.player.angle = 0f;
        game.enemies.clear();
        game.enemies.add(new Enemy(game.player.x + 3.0f, game.player.y, 0.4f));
        game.enemies.add(new Enemy(game.player.x + 5.5f, game.player.y + 0.55f, 1.7f));
        game.pickups.clear();
        game.pickups.add(new Pickup(game.player.x + 2.0f, game.player.y - 0.35f, 0.2f));
        game.update(1f / 60f);
        r.render(game, fb, w, h, 0.5f);
        save(fb, w, h, new File("tools/out/showcase.png"));

        // close-up of a drone to inspect the sprite + z buffer
        game.enemies.clear();
        game.enemies.add(new Enemy(game.player.x + 1.6f, game.player.y, 0f));
        r.render(game, fb, w, h, 0.5f);
        save(fb, w, h, new File("tools/out/showcase_close.png"));

        // portal active at the exit cell
        game.enemies.clear();
        game.player.x = game.map.exitX + 0.5f + 2.0f;
        game.player.y = game.map.exitY + 0.5f;
        game.player.angle = (float) Math.PI;
        game.coresTotal = Math.max(1, game.coresTotal);
        game.coresGot = game.coresTotal;
        game.exitActive = true;
        r.render(game, fb, w, h, 1.0f);
        save(fb, w, h, new File("tools/out/showcase_portal.png"));
        System.out.println("camera=(" + game.player.x + "," + game.player.y + ") angle=" + game.player.angle);
    }

    private static void save(int[] fb, int w, int h, File f) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, w, h, fb, 0, w);
        ImageIO.write(img, "png", f);
    }
}
