import cs132.IR.visitor.GJDepthFirst;
import cs132.IR.syntaxtree.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

public class Translator extends GJDepthFirst<Node, Cursor>{
    HashMap<String, FunctionObject> functions;
    Identifier temp1Register = new Identifier(new NodeToken("t4"));
    Identifier temp2Register = new Identifier(new NodeToken("t5"));

    public Translator(HashMap<String, FunctionObject> functions){
        this.functions = functions;
    }

    private String getIDString(Identifier id){
        return id.f0.toString();
    }

    private Identifier translateVarName(String varName, Cursor cur){
        VarObject var = cur.curFunc.varTable.get(varName);
        String newName;
        if(var.inRegister){
            newName = var.v_regName;
        }
        else{
            newName = var.v_varName;
        }
        Identifier newID = new Identifier(new NodeToken(newName));
        return newID;
    }

    private Identifier translateID(Identifier id, Cursor cur){
        String idString = getIDString(id);
        return translateVarName(idString, cur);
    }

    private Boolean getInReg(Identifier id, Cursor cur){
        String varName = getIDString(id);
        return cur.curFunc.varTable.get(varName).inRegister;
    }

    private Identifier getRegisterLHS(Identifier id, Cursor cur){
        if(getInReg(id, cur)){
            return translateID(id, cur);
        }
        else{
            return temp1Register;
        }
    }

    private Identifier getRegisterRHS1(Identifier id, Cursor cur){
        if(getInReg(id, cur)){
            return translateID(id, cur);
        }
        else{
            return temp1Register;
        }
    }

    private Identifier getRegisterRHS2(Identifier id, Cursor cur){
        if(getInReg(id, cur)){
            return translateID(id, cur);
        }
        else{
            return temp2Register;
        }
    }

    private void addLoads(NodeListOptional instructions, Identifier rhs1, Identifier rhs2, Cursor cur){
        if(!getInReg(rhs1, cur)){
            Instruction loadRHS1 = new Instruction(new NodeChoice(new Move(temp1Register, translateID(rhs1, cur))));
            instructions.addNode(loadRHS1);
        }
        if(rhs2 != null && !getInReg(rhs2, cur)){
            Instruction loadRHS2 = new Instruction(new NodeChoice(new Move(temp2Register, translateID(rhs2, cur))));
            instructions.addNode(loadRHS2);
        }
    }

    private void addStore(NodeListOptional instructions, Identifier lhs, Cursor cur){
        if(!getInReg(lhs, cur)){
            Instruction storeLHS = new Instruction(new NodeChoice(new Move(translateID(lhs, cur), temp1Register)));
            instructions.addNode(storeLHS);
        }
    }

    private NodeListOptional removeFirstSix(NodeListOptional params, Cursor cur){
        if(!params.present()){
            return params;
        }
        else{
            Vector<Node> paramVals = params.nodes;
            NodeListOptional newParams = new NodeListOptional();
            for(int i = 6; i < paramVals.size(); i++){
                newParams.addNode(paramVals.get(i));
            }
            return newParams;
        }
    }

    private ArrayList<Identifier> getFirstSix(NodeListOptional params, Cursor cur){
        if(!params.present()){
            return new ArrayList<Identifier>();
        }
        else{
            Vector<Node> paramVals = params.nodes;
            ArrayList<Identifier> firstSix = new ArrayList<Identifier>();
            for(int i = 0; i < 6 && i < paramVals.size(); i++){
                firstSix.add((Identifier) paramVals.get(i));
            }
            return firstSix;
        }
    }

    // combine 2 NodeListOptionals in order
    static void combine(NodeListOptional dest, NodeListOptional source){
        if(source.present())
        {
            for(int i = 0; i < source.size(); i++){
                dest.addNode(source.elementAt(i));
            }
        }
    }

    // flatten NodeListOptional to 1 level
    private NodeListOptional flatten(NodeListOptional n)
    {
        // don't do anything if there's no list
        if(!n.present()){
            return n;
        }
        // otherwise, create new list for the flattened result
        NodeListOptional flat_n = new NodeListOptional();
        // go through the elements
        for(int i = 0; i < n.size(); i++){
            Node elt = n.elementAt(i);
            // if it is a NodeListOptional, flatten it
            // and add its member elements
            if(elt instanceof NodeListOptional){
                NodeListOptional flat_elt = flatten((NodeListOptional)elt);
                combine(flat_n, flat_elt);
            }
            // otherwise, just add it as-is
            else{
                flat_n.addNode(elt);
            }
        }
        return flat_n;
    }

    // Program Visitor
    // f0 -> ( FunctionDeclaration() )* f1 -> 
    @Override
    public Program visit(Program prgm, Cursor cur){
        // make program out of returned function declarations
        NodeListOptional functions = this.visit(prgm.f0, cur);
        return new Program(functions);
    }

    // NodeListOptional Visitor
    @Override
    public NodeListOptional visit(
        NodeListOptional nodeListOptional, 
        Cursor cur)
    {
        // initialize sparrow nodelistoptional
        NodeListOptional sparrowListOptional = new NodeListOptional();
        // parse all minijava nodes
        if(nodeListOptional.present())
        {
            for(int i = 0; i < nodeListOptional.size(); i++){
                Node node = nodeListOptional.elementAt(i);
                Node sparrowVNode = node.accept(this, cur);
                // add corresponding node to sparrow nodelistoptional
                sparrowListOptional.addNode(sparrowVNode);
            }
        }
        // flatten the resulting list
        NodeListOptional flattened_sparrowList = flatten(sparrowListOptional);
        // return the sparrow NodeListOptional
        return flattened_sparrowList;
    } 

    // NodeChoice Visitor
    // visit the node choice and return a NodeListOptional of its return,
    // if not already in the desired format
    public NodeListOptional visit(NodeChoice nodeChoice, Cursor cur)
    {
        Node translated = nodeChoice.choice.accept(this, cur);
        NodeListOptional translated_packaged = new NodeListOptional(translated);
        NodeListOptional flattened_translated = flatten(translated_packaged);
        return flattened_translated;
    }

    // FunctionDeclaration Visitor
    // f0 -> "func" f1 -> FunctionName() f2 -> "(" f3 -> ( Identifier() )* f4 -> ")" f5 -> Block()
    @Override
    public FunctionDeclaration visit(FunctionDeclaration funDec, Cursor cur){
        // remove first six identifiers from the argument list
        NodeListOptional newArgList = removeFirstSix(funDec.f3, cur);
        // scope into the function
        String funcName = funDec.f1.f0.toString();
        cur.curFunc = functions.get(funcName);
        cur.curIndex = 0;
        // save values of 't' registers
        NodeListOptional saveTRegs = calleeSave(cur);
        // allocate registers for the remaining arguments, if they get registers
        NodeListOptional setRegs = setRegisters(newArgList, cur);
        // translate the block
        Block translatedBlock = this.visit(funDec.f5, cur);
        NodeListOptional translatedCode = translatedBlock.f0;
        // restore values of 't' registers
        NodeListOptional restoreTRegs = calleeRestore(cur);
        // put full block together
        NodeListOptional fullCode = new NodeListOptional();
        combine(fullCode, saveTRegs);
        combine(fullCode, setRegs);
        combine(fullCode, translatedCode);
        combine(fullCode, restoreTRegs);
        Block fullBlock = new Block(fullCode, translatedBlock.f2);
        // put together new function declaration
        FunctionDeclaration translatedFunDec = new FunctionDeclaration(funDec.f1, newArgList, fullBlock);
        return translatedFunDec;
    }

    private NodeListOptional setRegisters(NodeListOptional argList, Cursor cur){
        if(!argList.present()){
            return new NodeListOptional();
        }
        NodeListOptional setRegisters = new NodeListOptional();
        for(Node arg : argList.nodes){
            Identifier argID = (Identifier) arg;
            if(getInReg(argID, cur)){
                Identifier regID = translateID(argID, cur);
                Instruction setRegister = new Instruction(new NodeChoice(new Move(regID, argID)));
                setRegisters.addNode(setRegister);
            }
        }
        return setRegisters;
    }

    // Block Visitor
    // f0 -> ( Instruction() )* f1 -> "return" f2 -> Identifier()
    @Override
    public Block visit(Block block, Cursor cur){
        // translate instructions
        NodeListOptional instructions = this.visit(block.f0, cur);
        // if f2 is in a register, move it to memory
        if(getInReg(block.f2, cur)){
            Identifier regID = translateID(block.f2, cur);
            Instruction moveToMem = new Instruction(new NodeChoice(new Move(block.f2, regID)));
            instructions.addNode(moveToMem);
        }
        // return block
        Block newBlock = new Block(instructions, block.f2);
        return newBlock;
    }

    // Instruction Visitor
    // f0 -> LabelWithColon() | SetInteger() | SetFuncName() 
    // | Add() | Subtract() | Multiply() | LessThan() | Load() 
    // | Store() | Move() | Alloc() | Print() | ErrorMessage() 
    // | Goto() | IfGoto() | Call()
    @Override
    public NodeListOptional visit(Instruction insn, Cursor cur){
        cur.curIndex++;
        return this.visit(insn.f0, cur);
    }

    // LabelWithColon Visitor
    @Override
    public Instruction visit(LabelWithColon lwc, Cursor cur){
        return new Instruction(new NodeChoice(lwc));
    }

    // SetInteger Visitor
    //  f0 -> Identifier() f1 -> "=" f2 -> IntegerLiteral()
    @Override
    public NodeListOptional visit(SetInteger setInt, Cursor cur){
        NodeListOptional translated_setInt = new NodeListOptional();
        // get register name for f0
        Identifier f0Reg = getRegisterLHS(setInt.f0, cur);
        // translate instruction
        Instruction newSetInt = new Instruction(new NodeChoice(new SetInteger(f0Reg, setInt.f2)));
        translated_setInt.addNode(newSetInt);
        // add a store if needed
        addStore(translated_setInt, setInt.f0, cur);
        return translated_setInt;
    }

    // SetFuncName Visitor
    // f0 -> Identifier() f1 -> "=" f2 -> "@" f3 -> FunctionName()
    @Override
    public NodeListOptional visit(SetFuncName setFuncName, Cursor cur)
    {
        NodeListOptional translated_setFuncName = new NodeListOptional();
        // get register name for f0
        Identifier f0Reg = getRegisterLHS(setFuncName.f0, cur);
        // translate instruction
        Instruction newSetFunc = new Instruction(new NodeChoice(new SetFuncName(f0Reg, setFuncName.f3)));
        translated_setFuncName.addNode(newSetFunc);
        // add a store if needed
        addStore(translated_setFuncName, setFuncName.f0, cur);
        return translated_setFuncName;
    }

    // Add Visitor
    //  f0 -> Identifier() f1 -> "=" f2 -> Identifier() f3 -> "+" f4 -> Identifier()
    @Override
    public NodeListOptional visit(Add add, Cursor cur){
        NodeListOptional translated_add = new NodeListOptional();
        // get register names for f2, f4
        Identifier f2Reg = getRegisterRHS1(add.f2, cur);
        Identifier f4Reg = getRegisterRHS2(add.f4, cur);
        // add loads if needed
        addLoads(translated_add, add.f2, add.f4, cur);
        // get register name for f0
        Identifier f0Reg = getRegisterLHS(add.f0, cur);
        // translate instruction
        Instruction new_add = new Instruction(new NodeChoice(new Add(f0Reg, f2Reg, f4Reg)));
        translated_add.addNode(new_add);
        // add store if needed
        addStore(translated_add, add.f0, cur);
        return translated_add;
    }

    // Subtract Visitor
    //  f0 -> Identifier() f1 -> "=" f2 -> Identifier() f3 -> "-" f4 -> Identifier()
    @Override
    public NodeListOptional visit(Subtract sub, Cursor cur){
        NodeListOptional translated_sub = new NodeListOptional();
        // get register names for f2, f4
        Identifier f2Reg = getRegisterRHS1(sub.f2, cur);
        Identifier f4Reg = getRegisterRHS2(sub.f4, cur);
        // add loads if needed
        addLoads(translated_sub, sub.f2, sub.f4, cur);
        // get register name for f0
        Identifier f0Reg = getRegisterLHS(sub.f0, cur);
        // translate instruction
        Instruction new_sub = new Instruction(new NodeChoice(new Subtract(f0Reg, f2Reg, f4Reg)));
        translated_sub.addNode(new_sub);
        // add store if needed
        addStore(translated_sub, sub.f0, cur);
        return translated_sub;
    }

    // Multiply Visitor
    //  f0 -> Identifier() f1 -> "=" f2 -> Identifier() f3 -> "-" f4 -> Identifier()
    @Override
    public NodeListOptional visit(Multiply mul, Cursor cur){
        NodeListOptional translated_mul = new NodeListOptional();
        // get register names for f2, f4
        Identifier f2Reg = getRegisterRHS1(mul.f2, cur);
        Identifier f4Reg = getRegisterRHS2(mul.f4, cur);
        // add loads if needed
        addLoads(translated_mul, mul.f2, mul.f4, cur);
        // get register name for f0
        Identifier f0Reg = getRegisterLHS(mul.f0, cur);
        // translate instruction
        Instruction new_mul = new Instruction(new NodeChoice(new Multiply(f0Reg, f2Reg, f4Reg)));
        translated_mul.addNode(new_mul);
        // add store if needed
        addStore(translated_mul, mul.f0, cur);
        return translated_mul;
    }

    // LessThan Visitor
    //  f0 -> Identifier() f1 -> "=" f2 -> Identifier() f3 -> "-" f4 -> Identifier()
    @Override
    public NodeListOptional visit(LessThan lt, Cursor cur){
        NodeListOptional translated_lt = new NodeListOptional();
        // get register names for f2, f4
        Identifier f2Reg = getRegisterRHS1(lt.f2, cur);
        Identifier f4Reg = getRegisterRHS2(lt.f4, cur);
        // add loads if needed
        addLoads(translated_lt, lt.f2, lt.f4, cur);
        // get register name for f0
        Identifier f0Reg = getRegisterLHS(lt.f0, cur);
        // translate instruction
        Instruction new_lt = new Instruction(new NodeChoice(new LessThan(f0Reg, f2Reg, f4Reg)));
        translated_lt.addNode(new_lt);
        // add store if needed
        addStore(translated_lt, lt.f0, cur);
        return translated_lt;
    }

    // Load Visitor
    //  f0 -> Identifier() f1 -> "=" f2 -> "[" f3 -> Identifier() f4 -> "+" f5 -> IntegerLiteral() f6 -> "]"
    @Override
    public NodeListOptional visit(Load load, Cursor cur){
        NodeListOptional translated_load = new NodeListOptional();
        // get register name for f3
        Identifier f3Reg = getRegisterRHS1(load.f3, cur);
        // add load if needed
        addLoads(translated_load, load.f3, null, cur);
        // get register name for f0
        Identifier f0Reg = getRegisterLHS(load.f0, cur);
        // translate instruction
        Instruction newLoad = new Instruction(new NodeChoice(new Load(f0Reg,f3Reg, load.f5)));
        translated_load.addNode(newLoad);
        // add store if needed
        addStore(translated_load, load.f0, cur);
        return translated_load;
    }

    // Store Visitor
    //  f0 -> "[" f1 -> Identifier() f2 -> "+" f3 -> IntegerLiteral() f4 -> "]" f5 -> "=" f6 -> Identifier()
    @Override
    public NodeListOptional visit(Store store, Cursor cur){
        NodeListOptional translated_store = new NodeListOptional();
        // get register names for f1, f6
        Identifier f1Reg = getRegisterRHS1(store.f1, cur);
        Identifier f6Reg = getRegisterRHS2(store.f6, cur);
        // add loads if needed
        addLoads(translated_store, store.f1, store.f6, cur);
        // translate instruction
        Instruction newStore = new Instruction(new NodeChoice(new Store(f1Reg,store.f3, f6Reg)));
        translated_store.addNode(newStore);
        return translated_store;
    }

    // Move Visitor
    //  f0 -> Identifier() f1 -> "=" f2 -> Identifier()
    @Override
    public NodeListOptional visit(Move move, Cursor cur){
        NodeListOptional translated_move = new NodeListOptional();
        // get translated name for f2
        Identifier f2ID = translateID(move.f2, cur);
        // get register name for f0
        Identifier f0Reg = getRegisterLHS(move.f0, cur);
        // translate instruction
        Instruction newMove = new Instruction(new NodeChoice(new Move(f0Reg, f2ID)));
        translated_move.addNode(newMove);
        // add store if needed
        addStore(translated_move, move.f0, cur);
        return translated_move;
    }

    // Alloc Visitor
    // f0 -> Identifier() f1 -> "=" f2 -> "alloc" f3 -> "(" f4 -> Identifier() f5 -> ")"
    @Override
    public NodeListOptional visit(Alloc alloc, Cursor cur){
        NodeListOptional translated_alloc = new NodeListOptional();
        // get register name for f4
        Identifier f4Reg = getRegisterRHS1(alloc.f4, cur);
        // add load if needed
        addLoads(translated_alloc, alloc.f4, null, cur);
        // get register name for f0
        Identifier f0Reg = getRegisterLHS(alloc.f0, cur);
        // translate instruction
        Instruction newAlloc = new Instruction(new NodeChoice(new Alloc(f0Reg, f4Reg)));
        translated_alloc.addNode(newAlloc);
        // add store if needed
        addStore(translated_alloc, alloc.f0, cur);
        return translated_alloc;
    }

    // Print Visitor
    // f0 -> "print" f1 -> "(" f2 -> Identifier() f3 -> ")"
    @Override
    public NodeListOptional visit(Print print, Cursor cur){
        NodeListOptional translated_print = new NodeListOptional();
        // get register name for f2
        Identifier f2Reg = getRegisterRHS1(print.f2, cur);
        // add load if needed
        addLoads(translated_print, print.f2, null, cur);
        // translate instruction
        Instruction newPrint = new Instruction(new NodeChoice(new Print(f2Reg)));
        translated_print.addNode(newPrint);
        return translated_print;
    }

    // ErrorMessage Visitor
    @Override
    public Instruction visit(ErrorMessage error, Cursor cur){
        return new Instruction(new NodeChoice(error));
    }

    // Goto Visitor
    @Override
    public Instruction visit(Goto goTo, Cursor cur){
        return new Instruction(new NodeChoice(goTo));
    }

    // IfGoto Visitor
    // f0 -> "if0" f1 -> Identifier() f2 -> "goto" f3 -> Label()
    @Override
    public NodeListOptional visit(IfGoto ifGoto, Cursor cur){
        NodeListOptional translated_if = new NodeListOptional();
        // get register name for f1
        Identifier f1Reg = getRegisterRHS1(ifGoto.f1, cur);
        // add load if needed
        addLoads(translated_if, ifGoto.f1, null, cur);
        // translate instruction
        Instruction newIf = new Instruction(new NodeChoice(new If(f1Reg, ifGoto.f3)));
        translated_if.addNode(newIf);
        return translated_if;
    }

    // Call Visitor
    // f0 -> Identifier() f1 -> "=" f2 -> "call" f3 -> Identifier() f4 -> "(" f5 -> ( Identifier() )* f6 -> ")"
    @Override
    public NodeListOptional visit(Call call, Cursor cur){
        NodeListOptional translated_call = new NodeListOptional();
        // save values of any variables that outlive the call and are in 'a' or 't' registers
        NodeListOptional saveRegs = callerSave(cur);
        combine(translated_call, saveRegs);
        // get register name for f3
        Identifier f3Reg = getRegisterRHS1(call.f3, cur);
        // add load if needed
        addLoads(translated_call, call.f3, null, cur);
        // remove first 6 arguments
        NodeListOptional newParams = removeFirstSix(call.f5, cur);
        // get register name for f0
        Identifier f0Reg = getRegisterLHS(call.f0, cur);
        // store any remaining arguments that are in registers
        NodeListOptional storeArgs = storeArguments(newParams, cur);
        combine(translated_call, storeArgs);
        // load the first 6 arguments into 'a' registers
        NodeListOptional setArgs = setArguments(call.f5, cur);
        combine(translated_call, setArgs);
        // translate instruction
        Instruction newCall = new Instruction(new NodeChoice(new Call(f0Reg, f3Reg, newParams)));
        translated_call.addNode(newCall);
        // add store if needed
        addStore(translated_call, call.f0, cur);
        // load the values of any variables that outlive the call and are in 'a' or 't' registers
        NodeListOptional restoreRegs = callerRestore(cur);
        combine(translated_call, restoreRegs);
        return translated_call;
    }

    private NodeListOptional storeArguments(NodeListOptional params, Cursor cur){
        NodeListOptional storeArgs = new NodeListOptional();
        // if there are no params, no need to do anything
        if(!params.present()){return storeArgs;}
        // otherwise, go through them one by one
        for(Node param : params.nodes){
            // treat as identifier
            Identifier paramID = (Identifier)param;
            // make sure it is in a register
            if(getInReg(paramID, cur)){
                Identifier regID = translateID(paramID, cur);
                Instruction storeArg = new Instruction(new NodeChoice(new Move(paramID, regID)));
                storeArgs.addNode(storeArg);
            }
        }
        return storeArgs;
    }

    private NodeListOptional setArguments(NodeListOptional params, Cursor cur){
        NodeListOptional argSet = new NodeListOptional();
        ArrayList<Identifier> firstSix = getFirstSix(params, cur);
        for(int i = 0; i < 6 && i < firstSix.size(); i++){
            String reg = "a"+(i+2);
            Identifier regID = new Identifier(new NodeToken(reg));
            Identifier argID = translateID(firstSix.get(i), cur);
            argID = argRegToMem(argID, regID);
            Instruction move = new Instruction(new NodeChoice(new Move(regID, argID)));
            argSet.addNode(move);
        }
        return argSet;
    }

    private Identifier argRegToMem(Identifier argID, Identifier regID){
        char regIndex = regID.f0.toString().charAt(1);
        String argString = argID.f0.toString();
        if(
            argString.equals("a2")||
            argString.equals("a3")||
            argString.equals("a4")||
            argString.equals("a5")||
            argString.equals("a6")||
            argString.equals("a7")
        ){
            char argIndex = argString.charAt(1);
            if(argIndex < regIndex){
                String newString = "$"+argString;
                Identifier newID = new Identifier (new NodeToken(newString));
                return newID;
            }
            else{
                return argID;
            }
        }
        else{
            return argID;
        }
    }

    // unsaved registers are 't' registers or 'a' registers
    private ArrayList<String> activeUnsaveRegisters(Cursor cur){
        FunctionObject curFunc = cur.curFunc;
        Integer curIndex = cur.curIndex;
        ArrayList<String> activeUnsaveRegs = new ArrayList<String>();
        // check each variable
        for(VarObject var : curFunc.varTable.values()){
            // make sure it is active right now
            if(var.start <= curIndex && var.end > curIndex){
                // make sure it is in a register that is not saved
                if(var.inRegister && var.v_regName.charAt(0) != 's'){
                    // add its register to the list
                    activeUnsaveRegs.add(var.v_regName);
                }
            }
        }
        return activeUnsaveRegs;
    }

    // generic saved registers are s registers
    private HashSet<String> usedSaveRegisters(Cursor cur){
        FunctionObject curFunc = cur.curFunc;
        HashSet<String> usedSaveRegSet = new HashSet<String>();
        // check each variable
        for(VarObject var : curFunc.varTable.values()){
            // make sure it is in a t register
            if(var.inRegister && var.v_regName.charAt(0) == 's'){
                // add its register to the list
                usedSaveRegSet.add(var.v_regName);
            }
        }
        return usedSaveRegSet;
    }

    private NodeListOptional calleeSave(Cursor cur){
        // find all 's' registers that will be used
        HashSet<String> usedSaves = usedSaveRegisters(cur);
        NodeListOptional saveRegisters = new NodeListOptional();
        for(String regName : usedSaves){
            Identifier regID = new Identifier(new NodeToken(regName));
            Identifier memID = new Identifier(new NodeToken("$"+regName));
            Instruction save = new Instruction(new NodeChoice(new Move(memID, regID)));
            saveRegisters.addNode(save);
        }
        return saveRegisters;
    }

    private NodeListOptional calleeRestore(Cursor cur){
        // find all currently active 's' registers
        HashSet<String> usedSaves = usedSaveRegisters(cur);
        NodeListOptional restoreRegisters = new NodeListOptional();
        for(String regName : usedSaves){
            Identifier regID = new Identifier(new NodeToken(regName));
            Identifier memID = new Identifier(new NodeToken("$"+regName));
            Instruction restore = new Instruction(new NodeChoice(new Move(regID, memID)));
            restoreRegisters.addNode(restore);
        }
        return restoreRegisters;
    }

    private ArrayList<String> activeArgRegisters(Cursor cur){
        FunctionObject curFunc = cur.curFunc;
        Integer curIndex = cur.curIndex;
        ArrayList<String> activeArgRegs = new ArrayList<String>();
        // check each variable
        for(VarObject var : curFunc.varTable.values()){
            // make sure it is active right now, and needed specifically for the upcoming call
            if(var.start <= curIndex && var.end == curIndex){
                // make sure it is in an arg register
                if(var.inRegister && var.v_regName.charAt(0) == 'a'){
                    // add its register to the list
                    activeArgRegs.add(var.v_regName);
                }
            }
        }
        return activeArgRegs;
    }

    private NodeListOptional callerSave(Cursor cur){
        // find all currently active 't' or 'a' registers that need to be saved
        ArrayList<String> toSave = activeUnsaveRegisters(cur);
        toSave.addAll(activeArgRegisters(cur));
        // find the register corresponding to the LHS of the current call,
        // and mark them as not needing to be saved
        HashSet<String> callDef = cur.curFunc.instructions.get(cur.curIndex).def;
        for(String varName : callDef){
            String regName = translateVarName(varName, cur).f0.toString();
            toSave.remove(regName);
        }
        NodeListOptional saveRegisters = new NodeListOptional();
        for(String regName : toSave){
            Identifier regID = new Identifier(new NodeToken(regName));
            Identifier memID = new Identifier(new NodeToken("$"+regName));
            Instruction save = new Instruction(new NodeChoice(new Move(memID, regID)));
            saveRegisters.addNode(save);
        }
        return saveRegisters;
    }

    private NodeListOptional callerRestore(Cursor cur){
        // find all 't' or 'a' registers that need to be restored
        HashSet<String> toRestore = new HashSet<String>(activeUnsaveRegisters(cur));
        // find the register corresponding to the LHS of the current call,
        // and mark them as not needing to be restored
        HashSet<String> callDef = cur.curFunc.instructions.get(cur.curIndex).def;
        for(String varName : callDef){
            String regName = translateVarName(varName, cur).f0.toString();
            toRestore.remove(regName);
        }
        NodeListOptional restoreRegisters = new NodeListOptional();
        for(String regName : toRestore){
            Identifier regID = new Identifier(new NodeToken(regName));
            Identifier memID = new Identifier(new NodeToken("$"+regName));
            Instruction restore = new Instruction(new NodeChoice(new Move(regID, memID)));
            restoreRegisters.addNode(restore);
        }
        return restoreRegisters;
    }
}
