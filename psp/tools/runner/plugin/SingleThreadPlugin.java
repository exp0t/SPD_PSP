import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyListener;
import org.teavm.dependency.FieldDependency;
import org.teavm.dependency.MethodDependency;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ElementModifier;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.Program;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.MonitorEnterInstruction;
import org.teavm.model.instructions.MonitorExitInstruction;
import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// The PSP build runs SPD on one thread: drop every monitor so TeaVM never needs coroutines for them,
// and with them notify/notifyAll/wait, which would otherwise throw IllegalMonitorStateException.
public class SingleThreadPlugin implements TeaVMPlugin {
    // Object's final monitor methods; nothing can override them, so matching the descriptor is enough
    private static final Set<String> MONITOR_CALLS = Set.of("notify()V", "notifyAll()V", "wait()V", "wait(J)V", "wait(JI)V");

    @Override
    public void install(TeaVMHost host) {
        host.add(new ClassHolderTransformer() {
            @Override
            public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
                boolean runtime = cls.getName().startsWith("org.teavm.");
                for (MethodHolder method : cls.getMethods()) {
                    method.getModifiers().remove(ElementModifier.SYNCHRONIZED);
                    Program program = method.getProgram();
                    if (program == null) continue;
                    for (int i = 0; i < program.basicBlockCount(); i++) {
                        BasicBlock block = program.basicBlockAt(i);
                        List<Instruction> drop = new ArrayList<>();
                        for (Instruction insn : block) {
                            if (insn instanceof MonitorEnterInstruction || insn instanceof MonitorExitInstruction) {
                                drop.add(insn);
                            } else if (!runtime && insn instanceof InvokeInstruction) {
                                InvokeInstruction inv = (InvokeInstruction) insn;
                                MethodDescriptor d = inv.getMethod().getDescriptor();
                                if (inv.getInstance() != null && MONITOR_CALLS.contains(d.toString())) drop.add(insn);
                            }
                        }
                        for (Instruction insn : drop) insn.delete();
                    }
                }
            }
        });
        host.add(new SaveClassesListener());
    }

    // Saves name their objects' classes (Bundle: Class.forName + newInstance). TeaVM only prepares the
    // constructor and virtual methods of classes it sees flowing into newInstance, so a class that is only
    // ever created by loading a save ends up with null method pointers. Every concrete Bundlable with a
    // no-arg constructor is therefore declared a possible result of Class.forName.
    static class SaveClassesListener implements DependencyListener {
        private static final String BUNDLABLE = "com.watabou.utils.Bundlable";
        private final List<String> pending = new ArrayList<>();
        private MethodDependency forName;
        private int offered;

        @Override public void started(DependencyAgent agent) { }

        @Override
        public void classReached(DependencyAgent agent, String className) {
            if (!agent.getClassHierarchy().isSuperType(BUNDLABLE, className, false)) return;
            ClassReader cls = agent.getClassSource().get(className);
            if (cls == null || cls.hasModifier(ElementModifier.ABSTRACT) || cls.hasModifier(ElementModifier.INTERFACE)
                    || cls.getMethod(new MethodDescriptor("<init>", ValueType.VOID)) == null) return;
            if (forName != null) offer(agent, className);
            else pending.add(className);
        }

        @Override
        public void methodReached(DependencyAgent agent, MethodDependency method) {
            MethodReference ref = method.getReference();
            if (forName == null && ref.getClassName().equals("java.lang.Class") && ref.getName().equals("forName")
                    && ref.parameterCount() == 1) {
                forName = method;
                for (String c : pending) offer(agent, c);
                pending.clear();
            }
        }

        private void offer(DependencyAgent agent, String className) {
            offered++;
            forName.getResult().propagate(agent.getType(ValueType.object("java.lang.Class")));
            forName.getResult().getClassValueNode().propagate(agent.getType(ValueType.object(className)));
        }

        @Override public void fieldReached(DependencyAgent agent, FieldDependency field) { }
        @Override public void completing(DependencyAgent agent) { }
        @Override public void complete() { System.out.println("save classes offered to Class.forName: " + offered); }
    }
}
