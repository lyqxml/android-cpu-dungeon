import com.lyq.cpudungeon.engine.Game;
import com.lyq.cpudungeon.engine.Raycaster;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;

/** Dev-only harness: renders the engine without Android to verify visuals/perf. */
public final class HeadlessRender {
    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 20261017L;
        int w = 480;
        int h = 270;
        File out = new File("tools/out");
        out.mkdirs();

        Game game = new Game(seed);
        dumpMap(game, new File(out, "map.txt"));

        Raycaster raycaster = new Raycaster();
        int[] fb = new int[w * h];

        for (int frame = 0; frame < 480; frame++) {
            game.input.moveY = 1f;
            game.input.look = 0.006f;
            if (frame % 90 == 0) {
                game.input.fireTap = true;
            }
            game.update(1f / 60f);
            raycaster.render(game, fb, w, h, frame / 60f);
            if (frame % 80 == 0) {
                save(fb, w, h, new File(out, String.format("frame_%03d.png", frame)));
            }
        }

        long t0 = System.nanoTime();
        int frames = 300;
        for (int i = 0; i < frames; i++) {
            game.update(1f / 60f);
            raycaster.render(game, fb, w, h, i / 60f);
        }
        long t1 = System.nanoTime();
        double ms = (t1 - t0) / 1e6 / frames;
        System.out.printf("render+update: %.2f ms/frame at %dx%d (%d px)%n", ms, w, h, w * h);
        System.out.println("state=" + game.state + " level=" + game.level
                + " score=" + game.score + " health=" + game.player.health
                + " cores=" + game.coresGot + "/" + game.coresTotal
                + " enemies=" + game.enemies.size());
        save(fb, w, h, new File(out, "final.png"));
    }

    private static void dumpMap(Game game, File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < game.map.h; y++) {
            for (int x = 0; x < game.map.w; x++) {
                char c = game.map.solid(x, y) ? '#' : '.';
                if (x == game.map.exitX && y == game.map.exitY) c = 'E';
                if (x == game.map.startX && y == game.map.startY) c = 'S';
                sb.append(c);
            }
            sb.append('\n');
        }
        try (PrintWriter pw = new PrintWriter(file)) {
            pw.print(sb);
        }
        System.out.println("map " + game.map.w + "x" + game.map.h + " cores=" + game.coresTotal
                + " start=(" + game.map.startX + "," + game.map.startY + ") exit=("
                + game.map.exitX + "," + game.map.exitY + ")");
    }

    private static void save(int[] fb, int w, int h, File file) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, w, h, fb, 0, w);
        ImageIO.write(img, "png", file);
    }
}
