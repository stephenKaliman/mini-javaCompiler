import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class FunctionObject{
    // initial collection phase
    String funcName;
    ArrayList<String> parameters;
    ArrayList<InstructionObject> instructions;
    HashMap<String, Integer> labels;
    HashMap<Integer, String> gotos;
    HashMap<Integer, String> ifGotos;
    HashSet<Integer> errors;
    HashSet<Integer> functionCalls;
    // register allocation phase
    ArrayList<VarObject> liveIntervals;
    HashMap<String, VarObject> varTable;

    public FunctionObject(String name){
        this.funcName = name;
        this.parameters = new ArrayList<String>();
        this.instructions = new ArrayList<InstructionObject>();
        this.labels = new HashMap<String, Integer>();
        this.gotos = new HashMap<Integer, String>();
        this.ifGotos = new HashMap<Integer, String>();
        this.errors = new HashSet<Integer>();
        this.functionCalls = new HashSet<Integer>();
        this.liveIntervals = new ArrayList<VarObject>();
        this.varTable = new HashMap<String, VarObject>();
    }

    public void setDirectSuccessors()
    {
        // find instruction indices with no direct successor
        HashSet<Integer> noDirectSuccessor = new HashSet<Integer>();
        // gotos have no direct successor
        noDirectSuccessor.addAll(gotos.keySet());
        // errors have no direct successor
        noDirectSuccessor.addAll(errors);
        // fill in direct successors for everything else, excluding return stmt
        for(Integer i = 0; i < instructions.size()-1; i++){
            if(noDirectSuccessor.contains(i)){
                continue;
            }
            else{
                InstructionObject curInsn = instructions.get(i);
                curInsn.directSuccessor = instructions.get(i+1);
            }
        }
        return;
    }

    public void setGotoSuccessors()
    {
        // find instruction indices with a goto successor
        HashMap<Integer, String> hasGotoSuccessor = new HashMap<Integer, String>();
        hasGotoSuccessor.putAll(gotos);
        hasGotoSuccessor.putAll(ifGotos);
        // fill in goto successors for all of these
        for(Integer i : hasGotoSuccessor.keySet()){
            String target = hasGotoSuccessor.get(i);
            Integer targetIndex = labels.get(target);
            InstructionObject curInsn = instructions.get(i);
            curInsn.gotoSuccessor = instructions.get(targetIndex);
        }
        return;
    }

    // use "out" data to fill "in" data
    private void inFromOut()
    {
        boolean isFixedPoint = true;
        for(InstructionObject i : instructions){
            isFixedPoint &= i.inFromOut();
        }
        if(isFixedPoint){
            return;
        }
        else{
            outFromIn();
        }
    }

    // use "in" data to fill "out" data
    private void outFromIn()
    {
        boolean isFixedPoint = true;
        for(InstructionObject i : instructions){
            isFixedPoint &= i.outFromIn();
        }
        if(isFixedPoint){
            return;
        }
        else{
            inFromOut();
        }
    }

    public void fixedPointLiveness()
    {
        inFromOut();
        for(InstructionObject i : instructions){
            i.out.addAll(i.def);
        }
    }

    public void setLiveIntervals()
    {
        varTable = new HashMap<String, VarObject>();
        for(int index = 0; index < instructions.size(); index++)
        {
            // combine "in" and "out" for this instruction, into an "all live" set
            InstructionObject curInsn = instructions.get(index);
            HashSet<String> allLive = new HashSet<String>();
            allLive.addAll(curInsn.in);
            allLive.addAll(curInsn.out);
            // for all currently-live variables:
            for(String varName : allLive){
                // if it is not yet live, make a new VarObject, and give it a start time
                if(!varTable.containsKey(varName)){
                    VarObject newVar = new VarObject(varName, index);
                    // add it to the map
                    varTable.put(varName, newVar);
                }
                VarObject curVar = varTable.get(varName);
                // if the current instruction is a call, mark all live variables as including calls
                if(functionCalls.contains(index)){
                    curVar.includesCall = true;
                }
                // update the end time to the current time
                curVar.end = index;
            }
        }
        liveIntervals.addAll(varTable.values());
        liveIntervals.sort((a,b)->{return a.start - b.start;});
    }

    public void allocateArgRegisters(){
        for(int i = 0; i < 6 && i < parameters.size(); i++){
            String parameterName = parameters.get(i);
            VarObject parameterVar = varTable.get(parameterName);
            parameterVar.inRegister=true;
            String argRegister = "a"+(i+2);
            parameterVar.v_regName = argRegister;
            liveIntervals.remove(parameterVar);
        }
    }

    public void linearScanAllocation(){
        ArrayList<String> freeRegisters = new ArrayList<String>();
        for(int i = 0; i < 4; i++){
            freeRegisters.add("t"+i);
        }
        for(int i = 1; i <= 11; i++){
            freeRegisters.add("s"+i);
        }
        ArrayList<VarObject> active = new ArrayList<VarObject>();
        liveIntervals.sort((a,b)-> {return a.start - b.start;});
        for(VarObject var : liveIntervals){
            expireOldIntervals(var, active, freeRegisters);
            if(active.size() == 15){
                spillAtInterval(var, active, freeRegisters);
            }
            else{
                var.v_regName = freeRegisters.get(0);
                var.inRegister = true;
                freeRegisters.remove(0);
                active.add(var);
                active.sort((a,b)->{return a.end - b.end;});
            }
        }
    }

    private void expireOldIntervals(VarObject var, ArrayList<VarObject> active, ArrayList<String> freeRegisters){
        active.sort((a,b)->{return a.end - b.end;});
        Iterator<VarObject> activeIterator = active.iterator();
        while(activeIterator.hasNext()){
            VarObject activeVar = activeIterator.next();
            if(activeVar.end >= var.start){
                return;
            }
            // give preference to unsaved (caller saved) registers
            if(activeVar.v_regName.charAt(0)=='t'){
                freeRegisters.add(0, activeVar.v_regName);
            }
            else{
                freeRegisters.add(activeVar.v_regName);
            }
            activeIterator.remove();
        }
    }

    private void spillAtInterval(VarObject var, ArrayList<VarObject> active, ArrayList<String> freeRegisters){
        VarObject spill = active.get(active.size()-1);
        if(spill.end > var.end){
            var.v_regName = spill.v_regName;
            spill.inRegister = false;
            active.remove(spill);
            active.add(var);
            active.sort((a,b)->{return a.end-b.end;});
        }
        else{
            var.inRegister = false;
        }
    }
}
