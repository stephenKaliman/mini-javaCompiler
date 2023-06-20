import cs132.IR.visitor.GJDepthFirst;
import cs132.IR.syntaxtree.*;

public class SparrowPrinter extends GJDepthFirst<String, Boolean>{
    // Program Visitor
    // f0 -> ( FunctionDeclaration() )* f1 -> 
    @Override
    public String visit(Program program, Boolean inFunction)
    {
        return program.f0.accept(this, inFunction);
    }

    // NodeListOptionalVisitor
    @Override
    public String visit(NodeListOptional nodeListOptional, Boolean inFunction)
    {
        // make sure it is present first
        if(!nodeListOptional.present()){
            return "";
        }
        // if it is present, put its elements together, with newlines separating
        String fullCode = "";
        for(Node n : nodeListOptional.nodes){
            fullCode += n.accept(this, inFunction);
        }
        return fullCode;
    }

    // FunctionDeclaration Visitor
    //  f0 -> "func" f1 -> FunctionName() f2 -> "(" f3 -> ( Identifier() )* f4 -> ")" f5 -> Block()
    @Override
    public String visit(FunctionDeclaration funDecl, Boolean inFunction)
    {
        String signature = "func ";
        signature += funDecl.f1.accept(this,inFunction);
        signature += "(";
        signature += funDecl.f3.accept(this, inFunction);
        signature += ")\n";
        String body = funDecl.f5.accept(this, true);
        return signature + body;
    }

    // FunctionName Visitor
    @Override
    public String visit(FunctionName funName, Boolean inFunction)
    {
        return funName.f0.toString();
    }

    // Identifier Visitor
    @Override
    public String visit(Identifier id, Boolean inFunction)
    {
        return " " + id.f0.toString();
    }
    
    // Block Visitor
    //  f0 -> ( Instruction() )* f1 -> "return" f2 -> Identifier()
    @Override
    public String visit(Block b, Boolean inFunction)
    {
        String body = b.f0.accept(this,inFunction);
        String returnStmt = "\treturn ";
        returnStmt += b.f2.accept(this, inFunction);
        returnStmt += "\n";
        return body + returnStmt;
    }

    // Instruction Visitor
    @Override
    public String visit(Instruction insn, Boolean inFunction)
    {
        String insnCode = insn.f0.accept(this, inFunction);
        if(inFunction){
            insnCode = "\t" + insnCode;
        }
        return insnCode + "\n";
    }

    // NodeChoice Visitor
    public String visit(NodeChoice nc, Boolean inFunction)
    {
        return nc.choice.accept(this, inFunction);
    }

    // LabelWithColon() 
    //  f0 -> Label() f1 -> ":"
    @Override
    public String visit(LabelWithColon mark, Boolean inFunction)
    {
        String label = mark.f0.accept(this, inFunction);
        label += mark.f1.accept(this, inFunction);
        return label;
    }

    // SetInteger() 
    // f0 -> Identifier() f1 -> "=" f2 -> IntegerLiteral()
    @Override
    public String visit(SetInteger setInt, Boolean inFunction)
    {
        String equate = setInt.f0.accept(this,inFunction);
        equate += " " + setInt.f1.accept(this, inFunction);
        equate += " " + setInt.f2.accept(this, inFunction);
        return equate;
    }

    // NodeToken Visitor
    @Override
    public String visit(NodeToken token, Boolean inFunction)
    {
        return token.toString();
    }
    
    // SetFuncName() 
    // f0 -> Identifier() f1 -> "=" f2 -> "@" f3 -> FunctionName()
    @Override
    public String visit(SetFuncName setFuncName, Boolean inFunction)
    {
        String equate = setFuncName.f0.accept(this, inFunction);
        equate += " " + setFuncName.f1.accept(this, inFunction);
        equate += " " + setFuncName.f2.accept(this, inFunction);
        equate += setFuncName.f3.accept(this, inFunction);
        return equate;
    }

    // Add() 
    // f0 -> Identifier() f1 -> "=" f2 -> Identifier() f3 -> "+" f4 -> Identifier()
    @Override
    public String visit(Add add, Boolean inFunction)
    {
        String sum = add.f0.accept(this, inFunction);
        sum += " " + add.f1.accept(this, inFunction);
        sum += " " + add.f2.accept(this, inFunction);
        sum += " " + add.f3.accept(this, inFunction);
        sum += " " + add.f4.accept(this, inFunction);
        return sum;
    }

    // Subtract() 
    @Override
    public String visit(Subtract sub, Boolean inFunction)
    {
        String diff = sub.f0.accept(this, inFunction);
        diff += " " + sub.f1.accept(this, inFunction);
        diff += " " + sub.f2.accept(this, inFunction);
        diff += " " + sub.f3.accept(this, inFunction);
        diff += " " + sub.f4.accept(this, inFunction);
        return diff;
    }

    // Multiply() 
    @Override
    public String visit(Multiply mult, Boolean inFunction)
    {
        String prod = mult.f0.accept(this, inFunction);
        prod += " " + mult.f1.accept(this, inFunction);
        prod += " " + mult.f2.accept(this, inFunction);
        prod += " " + mult.f3.accept(this, inFunction);
        prod += " " + mult.f4.accept(this, inFunction);
        return prod;
    }

    // LessThan() 
    @Override
    public String visit(LessThan lt, Boolean inFunction)
    {
        String cmp = lt.f0.accept(this, inFunction);
        cmp += " " + lt.f1.accept(this, inFunction);
        cmp += " " + lt.f2.accept(this, inFunction);
        cmp += " " + lt.f3.accept(this, inFunction);
        cmp += " " + lt.f4.accept(this, inFunction);
        return cmp;
    }

    // Load() 
    // f0 -> Identifier() f1 -> "=" f2 -> "[" f3 -> Identifier() f4 -> "+" f5 -> IntegerLiteral() f6 -> "]"
    @Override
    public String visit(Load l, Boolean inFunction)
    {
        String load = l.f0.accept(this, inFunction);
        load += " " + l.f1.accept(this, inFunction);
        load += " " + l.f2.accept(this, inFunction);
        load += " " + l.f3.accept(this, inFunction);
        load += " " + l.f4.accept(this, inFunction);
        load += " " + l.f5.accept(this, inFunction);
        load += " " + l.f6.accept(this, inFunction);
        return load;
    }

    // Store() 
    // f0 -> "[" f1 -> Identifier() f2 -> "+" f3 -> IntegerLiteral() f4 -> "]" f5 -> "=" f6 -> Identifier()
    @Override
    public String visit(Store s, Boolean inFunction)
    {
        String store = s.f0.accept(this, inFunction);
        store += " " + s.f1.accept(this, inFunction);
        store += " " + s.f2.accept(this, inFunction);
        store += " " + s.f3.accept(this, inFunction);
        store += " " + s.f4.accept(this, inFunction);
        store += " " + s.f5.accept(this, inFunction);
        store += " " + s.f6.accept(this, inFunction);
        return store;
    }

    // Move() 
    //  f0 -> Identifier() f1 -> "=" f2 -> Identifier()
    @Override
    public String visit(Move m, Boolean b)
    {
        String move = m.f0.accept(this, b);
        move += " " + m.f1.accept(this, b);
        move += " " + m.f2.accept(this, b);
        return move;
    }

    // Alloc() 
    // f0 -> Identifier() f1 -> "=" f2 -> "alloc" f3 -> "(" f4 -> Identifier() f5 -> ")"
    @Override
    public String visit(Alloc a, Boolean b)
    {
        String alloc = a.f0.accept(this, b);
        alloc += " " + a.f1.accept(this, b);
        alloc += " " + a.f2.accept(this, b);
        alloc += a.f3.accept(this, b);
        alloc += a.f4.accept(this, b);
        alloc += a.f5.accept(this, b);
        return alloc;
    }

    // Print() 
    //  f0 -> "print" f1 -> "(" f2 -> Identifier() f3 -> ")"
    @Override
    public String visit(Print p, Boolean b)
    {
        String print = p.f0.accept(this, b);
        print += p.f1.accept(this, b);
        print += p.f2.accept(this, b);
        print += p.f3.accept(this, b);
        return print;
    }

    // ErrorMessage() 
    // f0 -> "error" f1 -> "(" f2 -> StringLiteral() f3 -> ")"
    @Override
    public String visit(ErrorMessage errMsg, Boolean b)
    {
        String errorMessage = errMsg.f0.accept(this,b);
        errorMessage += errMsg.f1.accept(this, b);
        errorMessage += errMsg.f2.accept(this, b);
        errorMessage += errMsg.f3.accept(this, b);
        return errorMessage;
    }

    // Goto() 
    @Override
    public String visit(Goto g, Boolean b)
    {
        String goTo = g.f0.accept(this, b);
        goTo += " " + g.f1.accept(this, b);
        return goTo;
    }

    // IfGoto() 
    // "if0" f1 -> Identifier() f2 -> "goto" f3 -> Label()
    @Override
    public String visit(If ig, Boolean b)
    {
        String ifGoto = ig.f0.accept(this, b);
        ifGoto += " " + ig.f1.accept(this ,b);
        ifGoto += " " + ig.f2.accept(this ,b);
        ifGoto += " " + ig.f3.accept(this ,b);
        return ifGoto;
    }

    // Call()
    //  f0 -> Identifier() f1 -> "=" f2 -> "call" f3 -> Identifier() f4 -> "(" f5 -> ( Identifier() )* f6 -> ")"
    @Override
    public String visit(Call c, Boolean b)
    {
        String call = c.f0.accept(this, b);
        call += " " + c.f1.accept(this, b);
        call += " " + c.f2.accept(this, b);
        call += " " + c.f3.accept(this, b);
        call += c.f4.accept(this, b);
        call += c.f5.accept(this, b);
        call += c.f6.accept(this, b);
        return call;
    }

    // Label Visitor
    @Override
    public String visit(Label l, Boolean b)
    {
        String label = l.f0.accept(this, b);
        return label;
    }

    // IntegerLiteral Visitor
    @Override
    public String visit(IntegerLiteral il, Boolean b)
    {
        String integerLiteral = il.f0.accept(this, b);
        return integerLiteral;
    }

    // StringLiteral Visitor
    @Override
    public String visit(StringLiteral sl, Boolean b)
    {
        String stringLiteral = sl.f0.accept(this, b);
        return "\"" + stringLiteral + "\"";
    }
}
