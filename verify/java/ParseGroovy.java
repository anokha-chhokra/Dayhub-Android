import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import java.io.File;
public class ParseGroovy {
    public static void main(String[] a) throws Exception {
        for (String f : a) {
            CompilationUnit unit = new CompilationUnit(new CompilerConfiguration());
            unit.addSource(new File(f));
            unit.compile(Phases.CONVERSION); // read the file and build its syntax tree: reports any syntax error
            System.out.println("syntax ok: " + f);
        }
    }
}
