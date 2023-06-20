import cs132.IR.visitor.GJDepthFirst;
import cs132.IR.syntaxtree.*;

import java.util.ArrayList;
import java.util.HashSet;

public class LivenessCollector extends GJDepthFirst<String, Cursor>{
    ArrayList<FunctionObject> livenessData = new ArrayList<FunctionObject>();
    
    private HashSet<String> getParamNames(NodeListOptional params, Cursor cur)
    {
        return new HashSet<String>(getParamsOrdered(params,cur));
    }

    private ArrayList<String> getParamsOrdered(NodeListOptional params, Cursor cur)
    {
        if(!params.present()){
            return new ArrayList<String>();
        }
        else{
            ArrayList<String> paramNames = new ArrayList<String>();
            for(Node n : params.nodes){
                paramNames.add(n.accept(this, cur));
            }
            return paramNames;
        }
    }
    // FunctionDeclaration Visitor
    // f0 -> "func" f1 -> FunctionName() f2 -> "(" f3 -> ( Identifier() )* 
    // f4 -> ")" f5 -> Block()
    @Override
    public String visit(FunctionDeclaration funDecl, Cursor cur)
    {
        // make a new function object
        String functionName = funDecl.f1.f0.toString();
        FunctionObject newFunc = new FunctionObject(functionName);
        // add object to livenessData
        livenessData.add(newFunc);
        // scope into the function
        FunctionObject prevFunc = cur.curFunc;
        cur.curFunc = newFunc;
        // save the function parameters
        cur.curFunc.parameters = getParamsOrdered(funDecl.f3, cur);
        // make a new instructionObject for the function header
        Instruction header = new Instruction(new NodeChoice(funDecl));
        InstructionObject funcHeader = new InstructionObject(header);
        // add the instruction to the current function
        cur.curFunc.instructions.add(funcHeader);
        // scope in to the header "instruction"
        InstructionObject prevInsn = cur.curInsn;
        cur.curInsn = funcHeader;
        // set the def of the header (the params) (f3)
        funcHeader.def = getParamNames(funDecl.f3, cur);
        // set the use of the header (none)
        funcHeader.use = new HashSet<String>();
        // scope back out of the header "instruction"
        cur.curInsn = prevInsn;
        // reset index count to 0
        cur.curIndex = 0;
        // fill in the info for remaining instructions (f5)
        funDecl.f5.accept(this, cur);
        // set the direct successors of all instructions
        cur.curFunc.setDirectSuccessors();
        // set the goto successors of all instructions
        cur.curFunc.setGotoSuccessors();
        // scope back out of the function
        cur.curFunc = prevFunc;

        return null;
    }

    // Identifier Visitor
    @Override
    public String visit(Identifier id, Cursor cur){
        return id.f0.toString();
    }

    // Block Visitor
    //  f0 -> ( Instruction() )* f1 -> "return" f2 -> Identifier()
    @Override
    public String visit(Block block, Cursor cur){
        // go through the instructions (f0)
        block.f0.accept(this, cur);
        // make a new instructionObject for the function return
        Instruction ret = new Instruction(new NodeChoice(block.f2));
        InstructionObject retInsn = new InstructionObject(ret);
        // add this instructionObject to the function
        cur.curFunc.instructions.add(retInsn);
        // scope in to the return "instruction"
        InstructionObject prevInsn = cur.curInsn;
        cur.curInsn = retInsn;
        // set the def of the return (none)
        // set the use of the return (the return value) (f2)
        cur.curInsn.addUse(block.f2, this);
        // scope back out of the return "instruction"
        cur.curInsn = prevInsn;

        return null;
    }

    // Instruction Visitor
    // f0 -> LabelWithColon() | SetInteger() | SetFuncName() | Add() 
    // | Subtract() | Multiply() | LessThan() | Load() 
    // | Store() | Move() | Alloc() | Print() | ErrorMessage() 
    // | Goto() | IfGoto() | Call()
    @Override
    public String visit(Instruction insn, Cursor cur){
        // make a new InstructionObject
        InstructionObject newInsn = new InstructionObject(insn);
        // increment index
        cur.curIndex++;
        // add this instructionObject to the current function
        cur.curFunc.instructions.add(newInsn);
        // scope into the instruction
        InstructionObject prevInsn = cur.curInsn;
        cur.curInsn = newInsn;
        // visit the actual instruction (f0.choice)
        insn.f0.choice.accept(this, cur);
        // scope back out of the instruction
        cur.curInsn = prevInsn;

        return null;
    }

    // LabelWithColon() Visitor
    // f0 -> Label() f1 -> ":"
    @Override
    public String visit(LabelWithColon labelWithColon, Cursor cur)
    {
        // def is empty
        // use is empty
        // save label and index (f0)
        Label markLabel = labelWithColon.f0;
        String markName = markLabel.f0.toString();
        cur.curFunc.labels.put(markName, cur.curIndex);
        // not a goto, ifgoto, or call
        return null;
    }

    // SetInteger() Visitor
    // f0 -> Identifier() f1 -> "=" f2 -> IntegerLiteral()
    @Override
    public String visit(SetInteger setInt, Cursor cur)
    {
        // def is LHS (f0)
        cur.curInsn.addDef(setInt.f0, this);
        // use is empty
        // not a label
        // not a goto
        // not an ifgoto
        // not a call
        return null;
    }

    // SetFuncName() Visitor
    // f0 -> Identifier() f1 -> "=" f2 -> "@" f3 -> FunctionName()
    @Override
    public String visit(SetFuncName setFuncName, Cursor cur)
    {
        // def is LHS (f0)
        cur.curInsn.addDef(setFuncName.f0, this);
        // use is empty
        // not a label, goto, ifgoto, or call
        return null;
    }

    // Add() Visitor
    //  f0 -> Identifier() f1 -> "=" f2 -> Identifier() f3 -> "+" f4 -> Identifier()
    @Override
    public String visit(Add add, Cursor cur)
    {
        // def is LHS (f0)
        cur.curInsn.addDef(add.f0, this);
        // use is RHS (f2, f4)
        cur.curInsn.addUse(add.f2, this);
        cur.curInsn.addUse(add.f4, this);
        // not a label, goto, ifgoto, or call
        return null;
    }

    // Subtract() Visitor
    // f0 -> Identifier() f1 -> "=" f2 -> Identifier() f3 -> "-" f4 -> Identifier()
    @Override
    public String visit(Subtract sub, Cursor cur)
    {
        // def is LHS (f0)
        cur.curInsn.addDef(sub.f0, this);
        // use is RHS (f2, f4)
        cur.curInsn.addUse(sub.f2, this);
        cur.curInsn.addUse(sub.f4, this);
        // not a label, goto, ifgoto, or call
        return null;
    }

    // Multiply() Visitor
    // f0 -> Identifier() f1 -> "=" f2 -> Identifier() f3 -> "*" f4 -> Identifier()
    @Override
    public String visit(Multiply mul, Cursor cur)
    {
        // def is LHS (f0)
        cur.curInsn.addDef(mul.f0, this);
        // use is RHS (f2, f4)
        cur.curInsn.addUse(mul.f2, this);
        cur.curInsn.addUse(mul.f4, this);
        // not a label, goto, ifgoto, or call
        return null;
    }

    // LessThan() Visitor
    //  f0 -> Identifier() f1 -> "=" f2 -> Identifier() f3 -> "<" f4 -> Identifier()
    @Override
    public String visit(LessThan lt, Cursor cur)
    {
        // def is LHS (f0)
        cur.curInsn.addDef(lt.f0, this);
        // use is RHS (f2, f4)
        cur.curInsn.addUse(lt.f2, this);
        cur.curInsn.addUse(lt.f4, this);
        // not a label, goto, ifgoto, or call
        return null;
    }

    // Load() Visitor
    // f0 -> Identifier() f1 -> "=" f2 -> "[" 
    // f3 -> Identifier() f4 -> "+" f5 -> IntegerLiteral() f6 -> "]"
    @Override
    public String visit(Load load, Cursor cur)
    {
        // def is LHS (f0)
        cur.curInsn.addDef(load.f0, this);
        // use is heap address (f3)
        cur.curInsn.addUse(load.f3, this);
        // not a label, goto, ifgoto, or call
        return null;
    }

    // Store() Visitor
    // f0 -> "[" f1 -> Identifier() f2 -> "+" 
    // f3 -> IntegerLiteral() f4 -> "]" f5 -> "=" f6 -> Identifier()
    @Override
    public String visit(Store store, Cursor cur)
    {
        // def is empty
        // use is heap addr and var (f1, f6)
        cur.curInsn.addUse(store.f1, this);
        cur.curInsn.addUse(store.f6, this);
        // not a label, goto, ifgoto, or call
        return null;
    }

    // Move() Visitor
    //  f0 -> Identifier() f1 -> "=" f2 -> Identifier()
    @Override
    public String visit(Move move, Cursor cur)
    {
        // def is LHS (f0)
        cur.curInsn.addDef(move.f0, this);
        // use is RHS (f2)
        cur.curInsn.addUse(move.f2, this);
        // not a label, goto ,ifgoto, or call
        return null;
    }

    // Alloc() Visitor
    //  f0 -> Identifier() f1 -> "=" f2 -> "alloc" f3 -> "(" f4 -> Identifier() f5 -> ")"
    @Override
    public String visit(Alloc alloc, Cursor cur)
    {
        // def is LHS (f0)
        cur.curInsn.addDef(alloc.f0, this);
        // use is alloc amount (f4)
        cur.curInsn.addUse(alloc.f4, this);
        // not a label, goto, ifgoto, or call
        return null;
    }

    // Print() Visitor
    // f0 -> "print" f1 -> "(" f2 -> Identifier() f3 -> ")"
    @Override
    public String visit(Print print, Cursor cur)
    {
        // def is empty
        // use is print id (f2)
        cur.curInsn.addUse(print.f2, this);
        // not a label, goto, ifgoto, or call
        return null;
    }

    // ErrorMessage() Visitor
    // f0 -> "error" f1 -> "(" f2 -> StringLiteral() f3 -> ")"
    @Override
    public String visit(ErrorMessage error, Cursor cur)
    {
        // def is empty
        // use is empty
        // save error index
        cur.curFunc.errors.add(cur.curIndex);
        // not a label, goto, ifgoto, or call
        return null;
    }

    // Goto() Visitor
    // f0 -> "goto" f1 -> Label()
    @Override
    public String visit(Goto goTo, Cursor cur)
    {
        // def is empty
        // use is empty
        // save goto index and label (f1)
        Label gotoLabel = goTo.f1;
        String gotoName = gotoLabel.f0.toString();
        cur.curFunc.gotos.put(cur.curIndex, gotoName);
        // not a label, ifgoto, or call
        return null;
    }

    // IfGoto() Visitor
    // f0 -> "if0" f1 -> Identifier() f2 -> "goto" f3 -> Label()
    @Override
    public String visit(IfGoto ifGoto, Cursor cur)
    {
        // def is empty
        // use is condition (f1)
        cur.curInsn.addUse(ifGoto.f1, this);
        // save ifgoto index and label (f3)
        Label ifgotoLabel = ifGoto.f3;
        String ifgotoName = ifgotoLabel.f0.toString();
        cur.curFunc.ifGotos.put(cur.curIndex, ifgotoName);
        // not a label, goto, or call
        return null;
    }

    // Call() Visitor
    // f0 -> Identifier() f1 -> "=" f2 -> "call" 
    // f3 -> Identifier() f4 -> "(" f5 -> ( Identifier() )* f6 -> ")"
    @Override
    public String visit(Call call, Cursor cur)
    {
        // def is LHS (f0)
        cur.curInsn.addDef(call.f0, this);
        // use is RHS (f3, f5)
        cur.curInsn.addUse(call.f3, this);
        HashSet<String> callParams = this.getParamNames(call.f5, cur);
        cur.curInsn.use.addAll(callParams);
        // save call index
        cur.curFunc.functionCalls.add(cur.curIndex);
        // not a label, goto, or ifgoto
        return null;
    }
}
