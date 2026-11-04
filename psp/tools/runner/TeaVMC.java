import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.teavm.diagnostics.Problem;
import org.teavm.tooling.ConsoleTeaVMToolLog;
import org.teavm.tooling.TeaVMTargetType;
import org.teavm.tooling.TeaVMTool;
import org.teavm.vm.TeaVMOptimizationLevel;

// Usage: TeaVMC <mainClass> <outDir> <classpath entries...>
public class TeaVMC {
    public static void main(String[] args) throws Exception {
        TeaVMTool tool = new TeaVMTool();
        tool.setTargetType(TeaVMTargetType.C);
        tool.setMainClass(args[0]);
        tool.setTargetDirectory(new File(args[1]));
        List<File> cp = new ArrayList<>();
        for (int i = 2; i < args.length; i++) cp.add(new File(args[i]));
        tool.setClassPath(cp);
        tool.setOptimizationLevel(TeaVMOptimizationLevel.ADVANCED);
        tool.setLog(new ConsoleTeaVMToolLog(false));
        tool.setShortFileNames(true);
        tool.setObfuscated(Boolean.getBoolean("obfuscate"));
        tool.setMinHeapSize(Integer.getInteger("heap.min", 4 << 20));
        tool.setMaxHeapSize(Integer.getInteger("heap.max", 24 << 20));
        tool.setIncremental(false);
        tool.generate();
        boolean bad = false;
        for (Problem p : tool.getProblemProvider().getProblems()) {
            System.err.println(p.getSeverity() + ": " + p.getText() + " " + java.util.Arrays.toString(p.getParams()) + " @ " + (p.getLocation() == null ? "?" : p.getLocation().getMethod() + " " + p.getLocation().getSourceLocation()));
        }
        if (!tool.getProblemProvider().getSevereProblems().isEmpty()) System.exit(1);
    }
}
