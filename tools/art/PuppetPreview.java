import com.harlequinxd.drivehard.core.model.Animations;
import com.harlequinxd.drivehard.core.model.Pose;
import com.harlequinxd.drivehard.core.model.Puppet;
import com.harlequinxd.drivehard.core.render.SoftRenderer;
import com.harlequinxd.drivehard.core.render.Texture;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Renders the sprite puppet at game scale (32 px per metre) from 8 angles in a few poses. */
public final class PuppetPreview {
    public static void main(String[] args) throws Exception {
        int FW = 64, FH = 72, SCALE = 4;
        Puppet puppet = new Puppet();
        puppet.tex = 0;
        SoftRenderer r = new SoftRenderer(FW, FH);
        r.textures = new Texture[]{puppet.atlas};
        r.setFogColor(0x8A9098);
        r.setZenithColor(0x8A9098);
        Object[][] rows = {
                {"idle", Animations.Anim.IDLE, 0f},
                {"walk", Animations.Anim.WALK, 0.25f},
                {"walk", Animations.Anim.WALK, 0.75f},
                {"run", Animations.Anim.RUN, 0.25f},
        };
        int cols = 8;
        BufferedImage out = new BufferedImage(FW * cols * SCALE, FH * (rows.length + 1) * SCALE, BufferedImage.TYPE_INT_RGB);
        // 32 px per metre on screen: focal / distance = 32
        float dist = (FH * 0.5f / (float) Math.tan(Math.toRadians(31))) / 32f;
        for (int row = 0; row <= rows.length; row++) {
            for (int c = 0; c < cols; c++) {
                Animations.Anim anim;
                float phase;
                float facing;
                if (row < rows.length) {
                    anim = (Animations.Anim) rows[row][1];
                    phase = (Float) rows[row][2];
                    facing = (float) (c * Math.PI / 4);      // S, SE, E, NE, N, NW, W, SW (camera on +Z)
                } else {
                    anim = Animations.Anim.WALK;              // the walk cycle, side on
                    phase = c / 8f;
                    facing = (float) (Math.PI / 2);
                }
                Pose pose = Animations.pose(anim, Math.round(phase * anim.frames) % anim.frames);
                r.lookAt(0, 0.95f, dist, 0, 0.95f, 0);
                r.begin();
                r.drawSky();
                puppet.draw(r, 0, 0, 0, facing, pose, -1e9f, 0f);
                r.flush();
                for (int y = 0; y < FH; y++)
                    for (int x = 0; x < FW; x++) {
                        int v = r.fb[y * FW + x];
                        for (int sy = 0; sy < SCALE; sy++)
                            for (int sx = 0; sx < SCALE; sx++)
                                out.setRGB((c * FW + x) * SCALE + sx, (row * FH + y) * SCALE + sy, v);
                    }
            }
        }
        ImageIO.write(out, "png", new File(args[0]));
        BufferedImage at = new BufferedImage(puppet.atlas.w * 6, puppet.atlas.h * 6, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < puppet.atlas.h; y++)
            for (int x = 0; x < puppet.atlas.w; x++)
                for (int s = 0; s < 36; s++) at.setRGB(x * 6 + s % 6, y * 6 + s / 6, puppet.atlas.px[y * puppet.atlas.w + x]);
        ImageIO.write(at, "png", new File(args[0].replace(".png", "-parts.png")));
        System.exit(0);
    }
}
