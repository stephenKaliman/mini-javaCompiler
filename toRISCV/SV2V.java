import cs132.IR.ParseException;
import cs132.IR.registers.*;
import cs132.IR.syntaxtree.*;
import cs132.IR.SparrowParser;
import cs132.IR.visitor.SparrowVConstructor;


public class SV2V{
    public static void main(String [] args) {
        Registers.SetRiscVregs();
        try {
            Node root = new SparrowParser(System.in).Program();
            SparrowVConstructor svc = new SparrowVConstructor();
            root.accept(svc);
            cs132.IR.sparrowv.Program program = svc.getProgram();
            // first pass
            Collector pass1 = new Collector();
            program.accept(pass1, null);
            // second pass
            Printer pass2 = new Printer(pass1.SparrowVProgram);
            String riscVCode = program.accept(pass2, null);
            System.out.println(riscVCode);
        } catch ( ParseException e) {
        System.out.println(e.toString());
        }
    }
}