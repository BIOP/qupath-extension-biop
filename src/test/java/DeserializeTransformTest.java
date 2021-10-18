import net.imglib2.realtransform.RealTransform;
import org.junit.jupiter.api.Test;
import qupath.ext.biop.transform.Warpy;

import java.io.File;

public class DeserializeTransformTest {

    public static void main(String... args) {

        RealTransform rt = Warpy.getRealTransform(new File("src/test/resources/transform/transform.json"));
    }
}