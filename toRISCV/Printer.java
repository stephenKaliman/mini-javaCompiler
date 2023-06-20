import cs132.IR.sparrowv.visitor.ArgRetVisitor;
import cs132.IR.sparrowv.*;

public class Printer implements ArgRetVisitor<FunctionObject, String>{
    ProgramObject SparrowVProgram;
    private int labelCounter = 0;

    public Printer(ProgramObject p){
        this.SparrowVProgram = p;
    }
    
    private String equivCode(){
        String code = "\t.equiv @sbrk, 9\n";
        code += "\t.equiv @print_string, 4\n"; 
        code += "\t.equiv @print_char, 11\n"; 
        code += "\t.equiv @print_int, 1\n"; 
        code += "\t.equiv @exit 10\n"; 
        code += "\t.equiv @exit2, 17\n";
        code += "\n\n";
        return code;
    }

    private String gotoMain(){
        String mainFuncName = SparrowVProgram.mainFunction;
        String code = ".text";
        code += "\n\n";
        code += "\tjal " + mainFuncName + "\n";
        code += "\tli a0, @exit\n";
        code += "\tecall\n";
        code += "\n\n";
        return code;
    }

    private String errorCode(){
        String code = "";
        code += ".globl error\n";
        code += "error:\n";
        code += "\tmv a1, a0\n";
        code += "\tli a0, @print_string\n";
        code += "\tecall\n";
        code += "\tli a1, 10\n";
        code += "\tli a0, @print_char\n";
        code += "\tecall\n";
        code += "\tli a0, @exit\n";
        code += "\tecall\n";
        code += "\tabort_17:\n";
        code += "\tj abort_17\n";
        code += "\n\n";
        return code;
    }

    private String allocCode(){
        String code = "";
        code += ".globl alloc\n";
        code += "alloc:\n";
        code += "\tmv a1, a0\n";
        code += "\tli a0, @sbrk\n";
        code += "\tecall\n";
        code += "\tjr ra\n";
        code += "\n\n";
        return code;
    }

    private String errorMessages(){
        // code accumulator
        String code = "";
        for(String errorMsg : SparrowVProgram.errorMsgs.keySet()){
            // move to next counter value
            int msg_Number = SparrowVProgram.errorMsgs.get(errorMsg);
            // make code
            code += ".globl msg_" + msg_Number + "\n";
            code += "msg_" + msg_Number + ":\n";
            code += "\t.asciiz " + errorMsg + "\n";
            code += "\t.align 2\n";
            code += "\n";
        }
        code += "\n\n";
        return code;
    }

    /* List<FunctionDecl> funDecls; */
    public String visit(Program program, FunctionObject curFunc) {
        String riscV_program = "";
        // add .equiv's
        riscV_program += equivCode();
        // add jump to main function (.text)
        riscV_program += gotoMain();
        // add functions (.globl)
        for(FunctionDecl funDec : program.funDecls){
            riscV_program += this.visit(funDec, curFunc);
        }
        // add error code (.globl)
        riscV_program += errorCode();
        // add alloc code (.globl)
        riscV_program += allocCode();
        // add .data [empty]
        riscV_program += ".data\n\n";
        // add error messages (.globl)
        riscV_program += errorMessages();

        return riscV_program;
    }

    /* Program parent;
    * FunctionName functionName;
    * List<Identifer> formalParameters;
    * Block block; */
    public String visit(FunctionDecl funDec, FunctionObject curFunc) {
        String funcCode = "";
        String funcName = funDec.functionName.name;
        curFunc = SparrowVProgram.funcTable.get(funcName);
        // write the function header
        funcCode += ".globl " + funcName + "\n";
        // write the function label
        funcCode += funcName + ":\n";
        // store the old frame pointer
        funcCode += "\tsw fp, -8(sp)\n";
        // set the new frame pointer
        funcCode += "\tmv fp, sp\n";
        // allocate frame space for locals
        int frameSize = curFunc.getFrameSize();
        funcCode += "\tli t6, " + frameSize + "\n";
        funcCode += "\tsub sp, sp, t6\n";
        // store return address
        funcCode += "\tsw ra, -4(fp)\n";
        // translate the block
        funcCode += this.visit(funDec.block, curFunc);
        funcCode += "\n\n";
        curFunc = null;
        return funcCode;
    }

    /* FunctionDecl parent;
    * List<Instruction> instructions;
    * Identifer return_id; */
    public String visit(Block block, FunctionObject curFunc) {
        String code = "";
        // translate the instructions
        for(Instruction insn : block.instructions){
            code += insn.accept(this, curFunc);
        }
        // translate the return statement:
        // 1. load return value into a0
        code += "\tlw a0, " + curFunc.getOffset(block.return_id.toString()) + "(fp)\n";
        // 2. restore ra register
        code += "\tlw ra, -4(fp)\n";
        // 3. restore old fp
        code += "\tlw fp, -8(fp)\n";
        // 4. move sp back across frame
        int frameSize = curFunc.getFrameSize();
        code += "\taddi sp, sp, " + frameSize + "\n";
        // 5. move sp back across args
        int paramSize = curFunc.getParamSize();
        code += "\t addi sp, sp " + paramSize + "\n";
        // 6. return 
        code += "jr ra\n";
        
        return code;
    }

    /* Label label; */
    public String visit(LabelInstr label, FunctionObject curFunc) {
        // translate the label
        String newLabel = curFunc.getNewLabel(label);
        String code = "";
        code += newLabel + ":\n";
        return code;
    }

    /* Register lhs;
    * int rhs; */
    public String visit(Move_Reg_Integer setInt, FunctionObject curFunc) {
        // translate the instruction (li)
        String code = "";
        String reg = setInt.lhs.toString();
        int num = setInt.rhs;
        code += "\tli " + reg + ", " + num + "\n";
        return code;
    }

    /* Register lhs;
    * FunctionName rhs;*/
    public String visit(Move_Reg_FuncName setFunc, FunctionObject curFunc) {
        // translate the instruction (la)
        String reg = setFunc.lhs.toString();
        String fnName = setFunc.rhs.name;
        String code = "";
        code += "\tla " + reg + ", " + fnName + "\n";
        return code;
    }

    /* Register lhs;
    * Register arg1;
    * Register arg2; */
    public String visit(Add add, FunctionObject curFunc) {
        // translate the instruction (add)
        String code = "";
        code += "\tadd " + add.lhs + ", " + add.arg1 + ", " + add.arg2 + "\n";
        return code;
    }

    /* Register lhs;
    * Register arg1;
    * Register arg2; */
    public String visit(Subtract sub, FunctionObject curFunc) {
        // translate the instruction (sub)
        String code = "";
        code += "\tsub " + sub.lhs + ", " + sub.arg1 + ", " + sub.arg2 + "\n";
        return code;
    }

    /* Register lhs;
    * Register arg1;
    * Register arg2; */
    public String visit(Multiply mul, FunctionObject curFunc) {
        // translate the instruction (mul)
        String code = "";
        code += "\tmul " + mul.lhs + ", " + mul.arg1 + ", " + mul.arg2 + "\n";
        return code;
    }

    /* Register lhs;
    * Register arg1;
    * Register arg2; */
    public String visit(LessThan slt, FunctionObject curFunc) {
        // translate the instruction (slt)
        String code = "";
        code += "\tslt " + slt.lhs + ", " + slt.arg1 + ", " + slt.arg2 + "\n";
        return code;
    }

    /* Register lhs;
    * Register base;
    * int offset; */
    public String visit(Load load, FunctionObject curFunc) {
        // translate the instruction (lw)
        String code = "";
        code += "\tlw " + load.lhs + ", " + load.offset + "(" + load.base + ")\n";
        return code;
    }

    /* Register base;
    * int offset;
    * Register rhs; */
    public String visit(Store store, FunctionObject curFunc) {
        // translate the instruction (sw)
        String code = "";
        code += "\tsw " + store.rhs + ", " + store.offset + "(" + store.base + ")\n";
        return code;
    }

    /* Register lhs;
    * Register rhs; */
    public String visit(Move_Reg_Reg setReg, FunctionObject curFunc) {
        // translate the instruction (mv)
        String code = "";
        code += "\tmv " + setReg.lhs + ", " + setReg.rhs + "\n";
        return code;
    }

    /* Identifer lhs;
    * Register rhs; */
    public String visit(Move_Id_Reg store, FunctionObject curFunc) {
        // translate the instruction (sw)
        String var = store.lhs.toString();
        int varOffset = curFunc.getOffset(var);
        String code = "";
        code += "\tsw " + store.rhs + ", " + varOffset + "(fp)\n";
        return code;
    }

    /* Register lhs;
    * Identifer rhs; */
    public String visit(Move_Reg_Id load, FunctionObject curFunc) {
        // translate the instruction (lw)
        String var = load.rhs.toString();
        int varOffset = curFunc.getOffset(var);
        String code = "";
        code += "\tlw " + load.lhs + ", " + varOffset + "(fp)\n";
        return code;
    }

    /* Register lhs;
    * Register size; */
    public String visit(Alloc alloc, FunctionObject curFunc) {
        String code = "";
        // move the requested size to a0
        code += "\tmv a0, " + alloc.size + "\n";
        // call alloc subroutine to request heap memory
        code += "\tjal alloc\n";
        // move returned pointer to appropriate register
        code += "\tmv " + alloc.lhs + ", a0\n";

        return code;
    }

    /* Register content; */
    public String visit(Print print, FunctionObject curFunc) {
        String code = "";
        // move content to be printed to a1
        code += "\tmv a1, " + print.content + "\n";
        // load the code for print_int to a0
        code += "\tli a0, @print_int\n";
        // print the number (ecall)
        code += "\tecall\n";
        // print a newline
        // load newline character
        code += "\tli a1, 10\n";
        // load the code for print_char to a0
        code += "\tli a0, @print_char\n";
        // print the newline (ecall)
        code += "\tecall\n";

        return code;
    }

    /* String msg; */
    public String visit(ErrorMessage error, FunctionObject curFunc) {
        String error_name = SparrowVProgram.getMsgName(error.msg);
        String code = "";
        // load address of error message to a0 (la)
        code += "\tla a0, " + error_name + "\n";
        // jump to error code (j error)
        code += "\tj error\n";

        return code;
    }

    /* Label label; */
    public String visit(Goto goTo, FunctionObject curFunc) {
        // modify label to include function name (<funcName>_<label>)
        String newLabel = curFunc.getNewLabel(goTo.label);
        // jump to the label (j <label>)
        String code = "";
        code += "\tj " + newLabel + "\n";
        return code;
    }

    /* Register condition;
    * Label label; */
    public String visit(IfGoto ifGoto, FunctionObject curFunc) {
        // modify label to include function name (<funcName>_<label>)
        String newLabel = curFunc.getNewLabel(ifGoto.label);
        // make continuation label (<continue>_<funcName>_<label>)
        String continueLabel = curFunc.getContinueLabel(ifGoto.label) + labelCounter++;
        // go to continuation label if not 0 (bnez)
        String code = "";
        code += "\tbnez " + ifGoto.condition + ", " + continueLabel + "\n";
        // add jump to actual label (jal)
        code += "\tjal " + newLabel + "\n";
        // add continuation label
        code += continueLabel + ":\n";
        return code;
    }

    /* Register lhs;
    * Register callee;
    * List<Identifer> args; */
    public String visit(Call call, FunctionObject curFunc) {
        String code = "";
        // dynamically allocate out array on the stack upon calling
        int numParams = call.args.size();
        int paramSpaceSize = 4 * numParams;
        code += "\tli t6, " + paramSpaceSize + "\n";
        code += "\tsub sp, sp, t6\n";
        int newParamOffset = 0;
        // for each local being used as a param:
        for(cs132.IR.token.Identifier param : call.args){
            // 1. load it to t6, from the stack
            int paramOffset = curFunc.getOffset(param.toString());
            code += "\tlw t6, " + paramOffset + "(fp)\n";
            // 2. push it to the stack
            code += "\tsw t6, " + newParamOffset + "(sp)\n";
            newParamOffset += 4;
        }
        // go to the callee (jalr)
        code += "\tjalr " + call.callee + "\n";
        // move the return value from a0 to the lhs (mv)
        code += "\tmv " + call.lhs + ", a0\n";
        return code;
    }
    
}
