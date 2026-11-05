import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.game.Vehicle;
import com.harlequinxd.drivehard.core.game.World;
import com.harlequinxd.drivehard.core.model.Cars;

import java.util.Collections;
import java.util.List;

/** Drives each car on an empty flat pad: 0-100, top speed, braking 100-0, cornering grip, spin-outs. */
public class HandlingBench {
    static final World PAD = new World() {
        { minX = minZ = -1e6f; maxX = maxZ = 1e6f; }
        public List<Box> near(float x, float z) { return Collections.emptyList(); }
    };
    static final float DT = 1f / 60f;

    public static void main(String[] a) {
        String[] names = a.length > 0 ? a : new String[]{"Kei car", "Hatchback", "Sedan", "Hot hatch", "Coupe",
                "Drift coupe", "Rally sedan", "Minivan", "SUV", "Box truck", "Fire engine"};
        System.out.println("model            kg    0-100s  top km/h  brake m  lat g@15  lat g@25  yaw@30  lift-off-in-turn");
        for (String n : names) {
            int style = -1;
            for (int m = 0; m < Cars.MODELS; m++) if (Cars.spec(m * Cars.PAINTS).name.equals(n)) style = m * Cars.PAINTS;
            if (style < 0) { System.out.println("?? " + n); continue; }
            Cars.Spec s = Cars.spec(style);
            Vehicle v = new Vehicle(style, 0, 0, 0, 0);
            float t = 0, t100 = -1;
            for (int i = 0; i < 60 * 60; i++) {
                v.update(DT, 0, 1, 0, PAD, null); t += DT;
                if (t100 < 0 && v.speed >= 100 / 3.6f) t100 = t;
            }
            float top = v.speed * 3.6f;
            v = new Vehicle(style, 0, 0, 0, 100 / 3.6f);
            float z0 = v.z;
            for (int i = 0; i < 600 && v.speed > 0.01f; i++) v.update(DT, 0, 0, 1, PAD, null);
            float brake = v.z - z0;
            float lat15 = skid(style, 15f), lat25 = skid(style, 25f);
            // full lock at 30 m/s for 2 s: does it hold or spin?
            v = new Vehicle(style, 0, 0, 0, 30f);
            float maxYaw = 0;
            for (int i = 0; i < 120; i++) { v.update(DT, 1, 0.6f, 0, PAD, null); maxYaw = Math.max(maxYaw, Math.abs(v.spin)); }
            // lift off mid-corner at 22 m/s: how much slip angle does the tail take?
            v = new Vehicle(style, 0, 0, 0, 22f);
            for (int i = 0; i < 90; i++) v.update(DT, 0.8f, 0.5f, 0, PAD, null);
            float maxBeta = 0;
            for (int i = 0; i < 60; i++) {
                v.update(DT, 0.8f, 0, 0.6f, PAD, null);
                float sp = (float) Math.hypot(v.vx, v.vz);
                if (sp > 2) maxBeta = Math.max(maxBeta, (float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (v.vx * v.fx() + v.vz * v.fz()) / sp)))));
            }
            System.out.printf("%-15s %5.0f  %6.1f  %8.0f  %7.1f  %8.2f  %8.2f  %6.2f  %5.1f deg%n", n, s.mass,
                    t100, top, brake, lat15, lat25, maxYaw, maxBeta);
        }
    }

    /** Steady full-lock circle at a held speed: lateral acceleration in g. */
    static float skid(int style, float sp) {
        Vehicle v = new Vehicle(style, 0, 0, 0, sp);
        float sum = 0; int k = 0;
        for (int i = 0; i < 360; i++) {
            float gas = v.speed < sp ? 1f : 0.15f;
            v.update(DT, 1, gas, 0, PAD, null);
            float s = (float) Math.hypot(v.vx, v.vz);
            if (i > 180) { sum += Math.abs(v.spin) * s; k++; }
        }
        return sum / k / 9.81f;
    }
}
