import cs132.IR.sparrowv.visitor.ArgVisitor;
import cs132.IR.sparrowv.*;
import cs132.IR.token.*;

public class Collector implements ArgVisitor<FunctionObject>{
    ProgramObject SparrowVProgram = new ProgramObject();

    /* List<FunctionDecl> funDecls; */
    public void visit(Program program, FunctionObject curFunc) {
        for(FunctionDecl funDec : program.funDecls){
            this.visit(funDec, curFunc);
        }
    }

    /* Program parent;
    * FunctionName functionName;
    * List<Identifer> formalParameters;
    * Block block; */
    public void visit(FunctionDecl funDec, FunctionObject curFunc) {
        // get the name
        String funcName = funDec.functionName.name;
        // make a new function object
        FunctionObject thisFunc = new FunctionObject(funcName);
        // add it to the hashmap
        SparrowVProgram.funcTable.put(funcName, thisFunc);
        // set main name if appropriate
        if(SparrowVProgram.mainFunction == null){
            SparrowVProgram.mainFunction = funcName;
        }
        // scope in
        curFunc = thisFunc;
        // get the args
        for(Identifier param : funDec.formalParameters){
            String paramString = param.toString();
            curFunc.funcArgMap.put(paramString, curFunc.funcArgMap.size());
        }
        // visit the block
        this.visit(funDec.block, curFunc);
        // scope back out
        curFunc = null;
    }

    /* FunctionDecl parent;
    * List<Instruction> instructions;
    * Identifer return_id; */
    public void visit(Block block, FunctionObject curFunc) {
        // visit the instructions
        for(Instruction instruction : block.instructions){
            instruction.accept(this, curFunc);
        }
        // get the return id
        Identifier retID = block.return_id;
        String retVal = retID.toString();
        curFunc.retVal = retVal;
    }

    /* Label label; */
    public void visit(LabelInstr label, FunctionObject curFunc) {
        return;
    }

    /* Register lhs;
    * int rhs; */
    public void visit(Move_Reg_Integer setInt, FunctionObject curFunc) {
        return;
    }

    /* Register lhs;
    * FunctionName rhs;*/
    public void visit(Move_Reg_FuncName setFunc, FunctionObject curFunc) {
        return;
    }

    /* Register lhs;
    * Register arg1;
    * Register arg2; */
    public void visit(Add add, FunctionObject curFunc) {
        return;
    }

    /* Register lhs;
    * Register arg1;
    * Register arg2; */
    public void visit(Subtract sub, FunctionObject curFunc) {
        return;
    }

    /* Register lhs;
    * Register arg1;
    * Register arg2; */
    public void visit(Multiply mul, FunctionObject curFunc) {
        return;
    }

    /* Register lhs;
    * Register arg1;
    * Register arg2; */
    public void visit(LessThan lt, FunctionObject curFunc) {
        return;
    }

    /* Register lhs;
    * Register base;
    * int offset; */
    public void visit(Load load, FunctionObject curFunc) {
        return;
    }

    /* Register base;
    * int offset;
    * Register rhs; */
    public void visit(Store store, FunctionObject curFunc) {
        return;
    }

    /* Register lhs;
    * Register rhs; */
    public void visit(Move_Reg_Reg setReg, FunctionObject curFunc) {
        return;
    }

    /* Identifer lhs;
    * Register rhs; */
    public void visit(Move_Id_Reg store, FunctionObject curFunc) {
        // get local variable from lhs
        Identifier local = store.lhs;
        String localName = local.toString();
        if(!curFunc.funcVarMap.containsKey(localName)){
            curFunc.funcVarMap.put(localName, curFunc.funcVarMap.size());
        }
    }

    /* Register lhs;
    * Identifer rhs; */
    public void visit(Move_Reg_Id load, FunctionObject curFunc) {
        return;
    }

    /* Register lhs;
    * Register size; */
    public void visit(Alloc alloc, FunctionObject curFunc) {
        return;
    }

    /* Register content; */
    public void visit(Print print, FunctionObject curFunc) {
        return;
    }

    /* String msg; */
    public void visit(ErrorMessage error, FunctionObject curFunc) {
        // keep track of the error message
        if(!SparrowVProgram.errorMsgs.containsKey(error.msg)){
            SparrowVProgram.errorMsgs.put(error.msg, SparrowVProgram.errorMsgs.size());
        }
    }

    /* Label label; */
    public void visit(Goto goTo, FunctionObject curFunc) {
        return;
    }

    /* Register condition;
    * Label label; */
    public void visit(IfGoto ifGoto, FunctionObject curFunc) {
        return;
    }

    /* Register lhs;
    * Register callee;
    * List<Identifer> args; */
    public void visit(Call call, FunctionObject curFunc) {
        return;
    }
    
}
