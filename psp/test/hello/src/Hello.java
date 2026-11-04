import java.util.*;
import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.c.Include;

public class Hello {
    @Import(name = "psp_test_sum")
    @Include(value = "psp_test.h", isSystem = false)
    static native float sum(Address data, int n);

    public static void main(String[] args) {
        float[] a = {1.5f, 2.5f, 3f};
        System.out.println("native sum " + sum(Address.ofData(a), a.length));
        long t0 = System.nanoTime(); float facc = 0;
        for (int i = 0; i < 1000000; i++) facc += (float) Math.sqrt(i) * (i & 7);
        System.out.println("1M float+Math.sqrt ms " + (System.nanoTime() - t0) / 1000000);
        t0 = System.nanoTime(); facc = 0;
        for (int i = 0; i < 1000000; i++) facc += i * 0.5f * (i & 7);
        System.out.println("1M float mul ms " + (System.nanoTime() - t0) / 1000000);
        t0 = System.nanoTime(); int iacc = 0;
        for (int i = 0; i < 1000000; i++) iacc += i * (i & 7) ^ (iacc >> 3);
        System.out.println("1M int ms " + (System.nanoTime() - t0) / 1000000 + " " + iacc + facc);
        t0 = System.nanoTime(); ArrayList<Integer> l = new ArrayList<>();
        for (int i = 0; i < 100000; i++) l.add(i);
        long s = 0; for (int x : l) s += x;
        System.out.println("100k boxed list ms " + (System.nanoTime() - t0) / 1000000 + " " + s);
        t0 = System.nanoTime(); Random r = new Random(1); int cnt = 0;
        for (int i = 0; i < 100000; i++) if (r.nextFloat() < 0.5f) cnt++;
        System.out.println("100k Random.nextFloat ms " + (System.nanoTime() - t0) / 1000000 + " " + cnt);
        t0 = System.nanoTime(); double dacc = 0;
        for (int i = 0; i < 100000; i++) dacc += i * 0.5;
        System.out.println("100k double mul ms " + (System.nanoTime() - t0) / 1000000 + " " + (int) dacc);
    }
}
