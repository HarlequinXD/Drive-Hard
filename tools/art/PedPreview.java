import com.harlequinxd.drivehard.core.model.Animations;
import com.harlequinxd.drivehard.core.model.CharacterSpec;
import com.harlequinxd.drivehard.core.model.Pose;
import com.harlequinxd.drivehard.core.model.Puppet;
import com.harlequinxd.drivehard.core.render.SoftRenderer;
import com.harlequinxd.drivehard.core.render.Texture;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * A crowd of pedestrian puppets at game scale: styles {from}..{from+count-1}, each in the front
 * three-quarter view plus a mid-stride side view, or all 8 angles of one style.
 * args: out.png [from] [count]
 */
public final class PedPreview {
    public static void main(String[] args) throws Exception {
        int from = args.length > 1 ? Integer.parseInt(args[1]) : 0, count = args.length > 2 ? Integer.parseInt(args[2]) : 20;
        int FW = 44, FH = 72, SCALE = 4, cols = 10;
        int rowsN = (count + cols - 1) / cols * 2;
        BufferedImage out = new BufferedImage(FW * cols * SCALE, FH * rowsN * SCALE, BufferedImage.TYPE_INT_RGB);
        SoftRenderer r = new SoftRenderer(FW, FH);
        r.setFogColor(0x8A9098);
        r.setZenithColor(0x8A9098);
        float dist = (FH * 0.5f / (float) Math.tan(Math.toRadians(31))) / 32f;
        long t0 = System.nanoTime();
        for (int i = 0; i < count; i++) {
            CharacterSpec spec = CharacterSpec.randomPedestrian(from + i);
            Puppet p = new Puppet(spec);
            p.tex = 0;
            r.textures = new Texture[]{p.atlas};
            System.out.printf("%3d %s %s %s/%s/%s %s%s%s h%.2f b%.2f%n", from + i, spec.female ? "F" : "M", spec.hair, spec.top, spec.bottom,
                    spec.hat, spec.mask ? "mask " : "", spec.sunglasses ? "shades " : "", spec.beard ? "beard" : "", spec.height, spec.build);
            for (int v = 0; v < 2; v++) {
                Pose pose = v == 0 ? Animations.poseAt(Animations.Anim.IDLE, 0f, 0) : Animations.poseAt(Animations.Anim.WALK, 0.3f, 0);
                float facing = v == 0 ? (float) (Math.PI / 4) : (float) (Math.PI / 2);
                r.lookAt(0, 0.95f, dist, 0, 0.95f, 0);
                r.begin();
                r.drawSky();
                p.draw(r, 0, 0, 0, facing, pose, -1e9f, 0f);
                r.flush();
                int cx = i % cols, cy = (i / cols) * 2 + v;
                for (int y = 0; y < FH; y++)
                    for (int x = 0; x < FW; x++) {
                        int c = r.fb[y * FW + x];
                        for (int s = 0; s < SCALE * SCALE; s++)
                            out.setRGB((cx * FW + x) * SCALE + s % SCALE, (cy * FH + y) * SCALE + s / SCALE, c);
                    }
            }
        }
        System.out.printf("%.2f ms per puppet incl. render%n", (System.nanoTime() - t0) / 1e6 / count);
        ImageIO.write(out, "png", new File(args[0]));
        System.exit(0);
    }
}
