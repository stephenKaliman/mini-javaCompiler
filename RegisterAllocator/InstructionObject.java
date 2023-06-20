import cs132.IR.syntaxtree.*;

import java.util.HashSet;

public class InstructionObject {
    Instruction instruction;
    HashSet<String> def;
    HashSet<String> use;
    InstructionObject directSuccessor;
    InstructionObject gotoSuccessor;
    HashSet<String> in;
    HashSet<String> out;

    public InstructionObject(Instruction i){
        this.instruction = i;
        def = new HashSet<String>();
        use = new HashSet<String>();
        in = new HashSet<String>();
        out = new HashSet<String>();
    }

    public void addDef(Identifier id, LivenessCollector visitor)
    {
        String var = id.accept(visitor, null);
        this.def.add(var);
    }

    public void addUse(Identifier id, LivenessCollector visitor)
    {
        String var = id.accept(visitor, null);
        this.use.add(var);
    }

    public boolean inFromOut(){
        // in = use + (out - def)
        HashSet<String> newIn = new HashSet<String>();
        newIn.addAll(out);
        newIn.removeAll(def);
        newIn.addAll(use);
        boolean fixedPoint = in.equals(newIn);
        in = newIn;
        return fixedPoint;
    }

    public boolean outFromIn(){
        // out = union of in's of successors
        HashSet<String> newOut = new HashSet<String>();
        if(directSuccessor != null){
            newOut.addAll(directSuccessor.in);
        }
        if(gotoSuccessor != null){
            newOut.addAll(gotoSuccessor.in);
        }
        boolean fixedPoint = out.equals(newOut);
        out = newOut;
        return fixedPoint;
    }
}
