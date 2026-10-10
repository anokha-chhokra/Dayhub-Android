import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Packs the compiled app (every .class under app/dayhub) into a jar so d8 can convert it: MkJar &lt;classes folder&gt; &lt;jar&gt;. */
public class MkJar {
    public static void main(String[] a) throws IOException {
        Path root = Path.of(a[0]);
        List<Path> classes;
        try (Stream<Path> s = Files.walk(root.resolve("app").resolve("dayhub"))) {
            classes = s.filter(p -> p.toString().endsWith(".class")).sorted().collect(Collectors.toList());
        }
        try (OutputStream out = Files.newOutputStream(Path.of(a[1])); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Path c : classes) {
                zip.putNextEntry(new ZipEntry(root.relativize(c).toString().replace('\\', '/')));
                Files.copy(c, zip);
                zip.closeEntry();
            }
        }
        System.out.println("packed " + classes.size() + " classes");
    }
}
