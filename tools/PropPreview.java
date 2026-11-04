import com.harlequinxd.drivehard.core.model.PropModels;
import com.harlequinxd.drivehard.core.sprite.SpriteBaker;
import com.harlequinxd.drivehard.core.sprite.SpriteSheet;
import java.util.List;

/** Bakes the prop sprites and writes their atlases. */
public class PropPreview {
    public static void main(String[] a) throws Exception {
        String out = a.length > 0 ? a[0] : "build/preview";
        new java.io.File(out).mkdirs();
        long t0 = System.nanoTime();
        SpriteSheet palms = com.harlequinxd.drivehard.core.sprite.PropSprites.palms();
        SpriteSheet lamp = com.harlequinxd.drivehard.core.sprite.PropSprites.lamp();
        SpriteSheet sig = com.harlequinxd.drivehard.core.sprite.PropSprites.signals();
        System.out.printf("props baked in %.0f ms: palms %dx%d lamp %dx%d signals %dx%d%n", (System.nanoTime() - t0) / 1e6,
                palms.width, palms.height, lamp.width, lamp.height, sig.width, sig.height);
        BakePreview.write(palms.pixels, palms.width, palms.height, 1, new java.io.File(out, "sprite_palms.png"));
        BakePreview.write(lamp.pixels, lamp.width, lamp.height, 2, new java.io.File(out, "sprite_lamp.png"));
        BakePreview.write(sig.pixels, sig.width, sig.height, 1, new java.io.File(out, "sprite_signals.png"));
    }
}
