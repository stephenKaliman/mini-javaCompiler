import cs132.minijava.visitor.GJDepthFirst;
import cs132.minijava.syntaxtree.*;
import cs132.IR.syntaxtree.*;
import cs132.IR.syntaxtree.Identifier;
import cs132.IR.syntaxtree.Node;
import cs132.IR.syntaxtree.NodeChoice;
import cs132.IR.syntaxtree.NodeListOptional;
import cs132.IR.syntaxtree.NodeToken;
import cs132.IR.syntaxtree.Block;
import cs132.IR.syntaxtree.IntegerLiteral;

import java.util.ArrayList;

public class SparrowWriter extends GJDepthFirst<Node, SymbolTable>{
    Integer tmp_counter = 0;
    Integer label_counter = 0;

    Identifier thisID = new Identifier(new NodeToken("this"));
    IntegerLiteral zeroIL = new IntegerLiteral(new NodeToken("0"));
    IntegerLiteral oneIL = new IntegerLiteral(new NodeToken("1"));
    IntegerLiteral fourIL = new IntegerLiteral(new NodeToken("4"));

    Identifier zeroID = new Identifier(new NodeToken("zero"));
    Identifier oneID = new Identifier(new NodeToken("one"));
    Identifier fourID = new Identifier(new NodeToken("four"));

    StringLiteral err_outOfBounds = new StringLiteral(new NodeToken("array index out of bounds"));
    StringLiteral err_nullPointer = new StringLiteral(new NodeToken("null pointer"));
    Instruction throwErr_nullPointer = new Instruction(new NodeChoice(new ErrorMessage(err_nullPointer)));
    Instruction throwErr_outOfBounds = new Instruction(new NodeChoice(new ErrorMessage(err_outOfBounds)));

    static void fail(String context)
    {
        System.out.println(context);
        System.exit(0);
    }

    static void combine(NodeListOptional dest, NodeListOptional source){
        if(source.present())
        {
            for(int i = 0; i < source.size(); i++){
                dest.addNode(source.elementAt(i));
            }
        }
    }

    // flatten NodeListOptional to 1 level
    NodeListOptional flatten(NodeListOptional n)
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

    // Goal Visitor
    // f0->MainClass() f1->(TypeDeclaration())* f2->
    @Override
    public Program visit (Goal g, SymbolTable st)
    {
        // get main function from main class
        FunctionDeclaration mainFunc = this.visit(g.f0, st);
        // get other functions from rest of program
        NodeListOptional otherFuncs = this.visit(g.f1, st);
        // combine into one NodeListOptional
        NodeListOptional allFuncs = new NodeListOptional(mainFunc);
        combine(allFuncs, otherFuncs);
        // make program object
        Program program = new Program(allFuncs);
        return program;
    }

    // Main Class Visitor
    // f0 -> "class" 
    // f1 -> Identifier() 
    // f2 -> "{" f3 -> "public" f4 -> "static" f5 -> "void" f6 -> "main" f7 -> "(" 
    // f8 -> "String" f9 -> "[" f10 -> "]" f11 -> Identifier() f12 -> ")" f13 -> "{" 
    // f14 -> ( VarDeclaration() )* 
    // f15 -> ( Statement() )* 
    // f16 -> "}" f17 -> "}"
    // Convert to Function Declaration:
    // f0 -> "func" 
    // f1 -> FunctionName() 
    // f2 -> "(" f3 -> ( Identifier() )* f4 -> ")" 
    // f5 -> Block()
    @Override
    public FunctionDeclaration visit (MainClass mainClass, SymbolTable st)
    {
        // The whole main class should just become a function
        // make main function name from class name
        String mainClassName = mainClass.f1.f0.toString();
        String mainFuncString = mainClassName + "main";
        NodeToken mainFuncToken = new NodeToken(mainFuncString);
        FunctionName mainFuncName = new FunctionName(mainFuncToken);
        // no parameters 
        NodeListOptional params = new NodeListOptional();
        // scope into class and method
        ClassLike prev_class = st.cur.cur_class;
        st.cur.cur_class = st.table.get(mainClassName);
        MethodLike prev_method = st.cur.cur_method;
        st.cur.cur_method = st.cur.cur_class.methods.get("main");
        // translate code (f14, f15)
        NodeListOptional varDecs = this.visit(mainClass.f14, st);
        NodeListOptional instructions = this.visit(mainClass.f15, st);
        // scope back out of the method and class
        st.cur.cur_method = prev_method;
        st.cur.cur_class = prev_class;
        // combine var declarations with instructions
        NodeListOptional fullBody = new NodeListOptional();
        combine(fullBody, varDecs);
        combine(fullBody, instructions);
        // create return value
        Instruction setZero = new Instruction(new NodeChoice(new SetInteger(zeroID, zeroIL)));
        fullBody.addNode(setZero);
        Identifier returnId = zeroID;
        // create block
        Block body = new Block(fullBody, returnId);
        // return the function
        FunctionDeclaration mainFunc 
            = new FunctionDeclaration(mainFuncName, params, body);
        return mainFunc;
    }
    
    // NodeListOptional Visitor
    @Override
    public NodeListOptional visit(
        cs132.minijava.syntaxtree.NodeListOptional nodeListOptional, 
        SymbolTable st)
    {
        // initialize sparrow nodelistoptional
        NodeListOptional sparrowListOptional = new NodeListOptional();
        // parse all minijava nodes
        if(nodeListOptional.present())
        {
            for(int i = 0; i < nodeListOptional.size(); i++){
                cs132.minijava.syntaxtree.Node minijavaNode = nodeListOptional.elementAt(i);
                Node sparrowNode = minijavaNode.accept(this, st);
                // add corresponding node to sparrow nodelistoptional
                sparrowListOptional.addNode(sparrowNode);
            }
        }
        // flatten the resulting list
        NodeListOptional flattened_sparrowList = flatten(sparrowListOptional);
        // return the sparrow NodeListOptional
        return flattened_sparrowList;
    }

    // TypeDeclaration Visitor
    @Override
    public NodeListOptional visit(TypeDeclaration typeDec, SymbolTable st)
    {
        return this.visit(typeDec.f0, st);
    }

    // VarDeclaration Visitor
    //  f0 -> Type() f1 -> Identifier() f2 -> ";"
    // return Instruction
    @Override
    public Instruction visit(VarDeclaration varDec, SymbolTable st){
        // get variable name (f1)
        String varName = "_" + varDec.f1.f0.toString();
        Identifier varId = new Identifier(new NodeToken(varName));
        // create integerLiteral for 0
        IntegerLiteral zero = new IntegerLiteral(new NodeToken("0"));
        // write statement setting this variable equal to 0
        SetInteger stmt = new SetInteger(varId, zero);
        // create corresponding instruction
        Instruction insn = new Instruction(new NodeChoice(stmt));
        return insn;
    }

    // NodeChoice Visitor
    // visit the node choice and return a NodeListOptional of its return,
    // if not already in the desired format
    public NodeListOptional visit(cs132.minijava.syntaxtree.NodeChoice nodeChoice, SymbolTable st)
    {
        Node translated = nodeChoice.choice.accept(this, st);
        NodeListOptional translated_packaged = new NodeListOptional(translated);
        NodeListOptional flattened_translated = flatten(translated_packaged);
        return flattened_translated;
    }

    // Statement Visitor
    //  f0 -> Block() | AssignmentStatement() | ArrayAssignmentStatement() | IfStatement() | WhileStatement() | PrintStatement()
    // return NodeListOptional of Instructions
    @Override
    public NodeListOptional visit(Statement stmt, SymbolTable st){
        return this.visit(stmt.f0, st);
    }

    private Identifier newTemp(){
        // create new temp name
        String tempName =  "$t" + tmp_counter.toString();
        // increment temp counter
        tmp_counter++;
        // return the new temp name
        return new Identifier(new NodeToken(tempName));
    }

    // should return a FunctionDeclaration for a constructor for the class c
    public FunctionDeclaration buildConstructor(ClassLike c){
        // name constructor
        String funcString = "new" + c.className;
        FunctionName constructorName = new FunctionName(new NodeToken(funcString));
        // constructor is always default, takes no params
        NodeListOptional constructorParams = new NodeListOptional();
        // create body of constructor
        // allocate space for fields and method table pointer
        Integer totalSpace = 4 * (c.totalFields + 1);
        IntegerLiteral totalSpaceId = new IntegerLiteral(new NodeToken(totalSpace.toString()));
        Identifier totalSpaceTmp = newTemp();
        Instruction setTotalSpace = new Instruction(new NodeChoice(new SetInteger(totalSpaceTmp, totalSpaceId)));
        Identifier object_tmpId = newTemp();
        Alloc alloc_objectTopLevel = new Alloc(object_tmpId, totalSpaceTmp);
        // allocate space for method table
        Integer totalMethodSpace = 4 * (c.methods.size());
        IntegerLiteral totalMethodSpaceId = new IntegerLiteral(new NodeToken(totalMethodSpace.toString()));
        Identifier totalMethodSpaceTmp = newTemp();
        Instruction setTotalMethodSpace = new Instruction(new NodeChoice(new SetInteger(totalMethodSpaceTmp, totalMethodSpaceId)));
        Identifier methodTable_tmpId = newTemp();
        Alloc alloc_methodTable = new Alloc(methodTable_tmpId, totalMethodSpaceTmp);
        // fill in method table
        ArrayList<SetFuncName> getFuncInsns = new ArrayList<SetFuncName>();
        ArrayList<Store> fillMethodTableInsns = new ArrayList<Store>();
        for(MethodLike curMethod : c.methods.values())
        {
            String methodName = curMethod.methodName;
            Integer methodIndex = curMethod.index;
            String methodOwner = curMethod.owner;
            // create sparrow func name
            FunctionName sparrowFuncName = new FunctionName(new NodeToken(methodOwner + methodName));
            // store in a temp variable
            Identifier method_tmpId = newTemp();
            SetFuncName methodAssignment = new SetFuncName(method_tmpId, sparrowFuncName);
            // store back to method table
            Integer methodTableIndex = methodIndex * 4;
            IntegerLiteral methodTableIndexIL = new IntegerLiteral(new NodeToken(methodTableIndex.toString()));
            Store storeMethodTableEntry = new Store(methodTable_tmpId, methodTableIndexIL, method_tmpId);
            // record instructions
            getFuncInsns.add(methodAssignment);
            fillMethodTableInsns.add(storeMethodTableEntry);
        }
        // store method table
        IntegerLiteral zero = new IntegerLiteral(new NodeToken("0"));
        Instruction storeMethodTable = new Instruction(new NodeChoice(new Store(object_tmpId, zero, methodTable_tmpId)));
        // put function body together
        NodeListOptional constructorBody = new NodeListOptional();
        constructorBody.addNode(setTotalSpace);
        constructorBody.addNode(new Instruction(new NodeChoice(alloc_objectTopLevel)));
        constructorBody.addNode(setTotalMethodSpace);
        constructorBody.addNode(new Instruction(new NodeChoice(alloc_methodTable)));
        for(int i = 0; i < c.methods.size(); i++){
            constructorBody.addNode(new Instruction(new NodeChoice(getFuncInsns.get(i))));
            constructorBody.addNode(new Instruction(new NodeChoice(fillMethodTableInsns.get(i))));
        }
        constructorBody.addNode(storeMethodTable);
        Block constructorBlock = new Block(constructorBody, object_tmpId);
        // put function declaration together
        FunctionDeclaration constructorDeclaration = new FunctionDeclaration(constructorName, constructorParams, constructorBlock);
        // return constructor function declaration
        return constructorDeclaration;
    }

    // ClassDeclaration Visitor
    // f0 -> "class" f1 -> Identifier() f2 -> "{" f3 -> ( VarDeclaration() )* f4 -> ( MethodDeclaration() )* f5 -> "}"
    @Override
    public NodeListOptional visit(ClassDeclaration classDec, SymbolTable st)
    {
        // find proper class by identifier (f1)
        String cur_className = classDec.f1.f0.toString();
        ClassLike cur_class = st.table.get(cur_className);
        // create constructor func using symbol table
        FunctionDeclaration constructor = buildConstructor(cur_class);
        // scope into class
        ClassLike prev_class = st.cur.cur_class;
        st.cur.cur_class = cur_class;
        // ignore var declarations, already took care of those in symbol table
        // create funcs for all of the other methods (f4)
        NodeListOptional otherFuncs = this.visit(classDec.f4, st);
        // scope out of class
        st.cur.cur_class = prev_class;
        // return NodeListOptional containing all the funcs
        NodeListOptional allFuncs = new NodeListOptional(constructor);
        combine(allFuncs, otherFuncs);
        return allFuncs;
    }

    // ClassExtendsDeclaration Visitor
    // f0 -> "class" f1 -> Identifier() f2 -> "extends" f3 -> Identifier() f4 -> "{" 
    // f5 -> ( VarDeclaration() )* f6 -> ( MethodDeclaration() )* f7 -> "}"
    @Override
    public NodeListOptional visit(ClassExtendsDeclaration classExtDec, SymbolTable st)
    {
        // find proper class by identifier (f1)
        String cur_className = classExtDec.f1.f0.toString();
        ClassLike cur_class = st.table.get(cur_className);
        // create constructor func using symbol table (inheritance already handled)
        FunctionDeclaration constructor = buildConstructor(cur_class);
        // scope into class
        ClassLike prev_class = st.cur.cur_class;
        st.cur.cur_class = cur_class;
        // var declarations already handled in symbol table construction
        // create funcs for all of the other methods (f6)
        NodeListOptional otherFuncs = this.visit(classExtDec.f6, st);
        // scope out of class
        st.cur.cur_class = prev_class;
        // return NodeListOptional containing all the funcs
        NodeListOptional allFuncs = new NodeListOptional(constructor);
        combine(allFuncs, otherFuncs);
        return allFuncs;
    }

    // Block Visitor
    // f0 -> "{" f1 -> ( Statement() )* f2 -> "}"
    @Override
    public NodeListOptional visit(cs132.minijava.syntaxtree.Block block, SymbolTable st)
    {
        return this.visit(block.f1, st);
    }

    private boolean isLocal(String varName, SymbolTable st){
        boolean param = st.cur.cur_method.paramsTypes.containsKey(varName);
        boolean local = st.cur.cur_method.localsTypes.containsKey(varName);
        return param || local;
    }

    private boolean isField(String varName, SymbolTable st){
        return st.cur.cur_class.fieldTypes.containsKey(varName);
    }

    private Identifier makeIdentifier(String varName, SymbolTable st){
        Identifier varId;
        // if it is a param or local, make the corresonding sparrow _varName
        if(isLocal(varName, st)){
            varId = new Identifier(new NodeToken("_"+varName));
        }
        // if it is a field, make a new (appropriately named) "tmp"
        else if(isField(varName, st)){
            varId = new Identifier(new NodeToken("m_"+varName));
        }
        // shouldn't be anything else, since it would be caught in typechecker
        else{
            varId = new Identifier(new NodeToken("$$ERROR"));
        }
        return varId;
    }

    // AssignmentStatement Visitor
    // f0 -> Identifier() f1 -> "=" f2 -> Expression() f3 -> ";"
    @Override
    public NodeListOptional visit(AssignmentStatement assignStmt, SymbolTable st)
    {
        // make sure we are in a class or method
        if(st.cur.cur_class == null || st.cur.cur_method == null){
            fail("not in class or method at AssignmentStatement");
        }
        // make sparrow identifier
        String varName = assignStmt.f0.f0.toString();
        Identifier varId = makeIdentifier(varName, st);
        Instruction setVarID = getValue(varName, varId, st);
        // get value of expression
        Identifier resultTmp = newTemp();
        NodeListOptional expressionCode = evaluateExpression(resultTmp, assignStmt.f2, st);
        // assign expression result back to varId
        Instruction storeBack = new Instruction(new NodeChoice(new Move(varId, resultTmp)));
        // put instructions together
        NodeListOptional sparrow_assignStmt = new NodeListOptional();
        sparrow_assignStmt.addNode(setVarID);
        combine(sparrow_assignStmt, expressionCode);
        sparrow_assignStmt.addNode(storeBack);
        // store back into class if it is a field
        if(!isLocal(varName, st) && isField(varName, st)){
            sparrow_assignStmt.addNode(storeFieldValue(varName, varId, st));
        }
        // return NodeListOptional of instructions
        return sparrow_assignStmt;
    }

    private IntegerLiteral getFieldOffset(String varName, SymbolTable st){
        Integer fieldIndex = st.cur.cur_class.fieldIndices.get(varName);
        Integer offset = (1 + fieldIndex) * 4;
        IntegerLiteral offsetIL = new IntegerLiteral(new NodeToken(offset.toString()));
        return offsetIL;
    }

    // get value of field from class, or local from itself
    private Instruction getValue(String varName, Identifier varID, SymbolTable st){
        Instruction get;
        if(isLocal(varName, st)){
            // just assign it to itself
            Move reflect = new Move(varID, varID);
            get = new Instruction(new NodeChoice(reflect));
        }
        // otherwise, it is a field
        else{
            // get offset
            IntegerLiteral offset = getFieldOffset(varName, st);
            // load array from fields
            Load fetch = new Load(varID, thisID, offset);
            get = new Instruction(new NodeChoice(fetch));
        }
        return get;
    }

    private Instruction storeFieldValue(String fieldName, Identifier targetVal, SymbolTable st){
        // get offset
        IntegerLiteral offset = getFieldOffset(fieldName, st);
        // write store-back instruction
        Store writeBack = new Store(thisID, offset, targetVal);
        Instruction store = new Instruction(new NodeChoice(writeBack));
        // return instruction
        return store;
    }
    
    // ArrayAssignmentStatement Visitor
    // f0 -> Identifier() f1 -> "[" f2 -> Expression() f3 -> "]" f4 -> "=" f5 -> Expression() f6 -> ";"
    @Override
    public NodeListOptional visit(ArrayAssignmentStatement arrayAssign, SymbolTable st)
    {
        // get proper sparrow identifier for array name (f0)
        String arrayName = arrayAssign.f0.f0.toString();
        Identifier arrayID = makeIdentifier(arrayName,st);
        // get the array
        Instruction getArray = getValue(arrayName, arrayID, st);
        // make sure the array has been initialized/allocated
        Label uninitializedLabel = newLabel();
        Instruction uninitializedMark = new Instruction(new NodeChoice(new LabelWithColon(uninitializedLabel)));
        Label initializedContinueLabel = newLabel();
        Instruction initializedContinueMark = new Instruction(new NodeChoice(new LabelWithColon(initializedContinueLabel)));
        Instruction gotoInitializedContinueLabel = new Instruction(new NodeChoice(new Goto(initializedContinueLabel)));
        Instruction initializedCheck = new Instruction(new NodeChoice(new If(arrayID, uninitializedLabel)));
        // get offset from expression (f2)
        Identifier offsetTmp = newTemp();
        NodeListOptional offsetCode = evaluateExpression(offsetTmp, arrayAssign.f2, st);
        // make sure offset is within range of the array
        Label continueLabelOOB = newLabel();
        Instruction continueMarkOOB = new Instruction(new NodeChoice(new LabelWithColon(continueLabelOOB)));
        Instruction gotoContinueOOB = new Instruction(new NodeChoice(new Goto(continueLabelOOB)));
        Label outOfBoundsLabel = newLabel();
        Instruction outOfBoundsMark = new Instruction(new NodeChoice(new LabelWithColon(outOfBoundsLabel)));
        // get array size
        Identifier arraySizeTmp = newTemp();
        Instruction loadArraySize = new Instruction(new NodeChoice(new Load(arraySizeTmp, arrayID, zeroIL)));
        Identifier inBoundsTmp = newTemp();
        Instruction setInBounds = new Instruction(new NodeChoice(new LessThan(inBoundsTmp, offsetTmp, arraySizeTmp)));
        Instruction inBoundsCheck = new Instruction(new NodeChoice(new If(inBoundsTmp, outOfBoundsLabel)));
        // check that index expression is nonnegative
        Label continueLabelNonNegative = newLabel();
        Instruction continueMarkNonNegative = new Instruction(new NodeChoice(new LabelWithColon(continueLabelNonNegative)));
        Instruction gotoContinueNonNegative = new Instruction(new NodeChoice(new Goto(continueLabelNonNegative)));
        Label negativeLabel = newLabel();
        Instruction negativeMark = new Instruction(new NodeChoice(new LabelWithColon(negativeLabel)));
        Instruction setZero = new Instruction(new NodeChoice(new SetInteger(zeroID, zeroIL)));
        Identifier offsetPlus1Temp = newTemp();
        Instruction setOne = new Instruction(new NodeChoice(new SetInteger(oneID, oneIL)));
        Instruction incrementOffset = new Instruction(new NodeChoice(new Add(offsetPlus1Temp, offsetTmp, oneID)));
        Identifier nonnegativeTmp = newTemp();
        Instruction setIfNonnegative = new Instruction(new NodeChoice(new LessThan(nonnegativeTmp, zeroID, offsetPlus1Temp)));
        Instruction nonnegativeCheck = new Instruction(new NodeChoice(new If(nonnegativeTmp,negativeLabel)));
        // convert java offset to sparrow offset
        Instruction set4Instruction = new Instruction(new NodeChoice(new SetInteger(fourID, fourIL)));
        Instruction mult4 = new Instruction(new NodeChoice(new Multiply(offsetTmp, offsetTmp, fourID)));
        Instruction add4 = new Instruction(new NodeChoice(new Add(offsetTmp, offsetTmp, fourID)));
        // convert to single tmp to index array
        Identifier indexTmp = newTemp();
        Add getIndex = new Add(indexTmp, arrayID, offsetTmp);
        Instruction indexInsn = new Instruction(new NodeChoice(getIndex));
        // get value to be assigned from expression (f5)
        Identifier resultTmp = newTemp();
        NodeListOptional resultCode = evaluateExpression(resultTmp, arrayAssign.f5, st);
        // store result into array
        Store saveResult = new Store(indexTmp, zeroIL, resultTmp);
        Instruction store = new Instruction(new NodeChoice(saveResult));
        ///////////////////////
        // put code together
        ///////////////////////
        NodeListOptional sparrow_arrayAssign = new NodeListOptional();
        // get the array, from local if local or from heap if field
        sparrow_arrayAssign.addNode(getArray);
        // make sure the array has been initialized
        sparrow_arrayAssign.addNode(initializedCheck);
        sparrow_arrayAssign.addNode(gotoInitializedContinueLabel);
        sparrow_arrayAssign.addNode(uninitializedMark);
        sparrow_arrayAssign.addNode(throwErr_nullPointer);
        sparrow_arrayAssign.addNode(initializedContinueMark);
        // determine the (java) offset into the array
        combine(sparrow_arrayAssign, offsetCode);
        // check that (java) offset is within array
        sparrow_arrayAssign.addNode(loadArraySize);
        sparrow_arrayAssign.addNode(setInBounds);
        sparrow_arrayAssign.addNode(inBoundsCheck);
        sparrow_arrayAssign.addNode(gotoContinueOOB);
        sparrow_arrayAssign.addNode(outOfBoundsMark);
        sparrow_arrayAssign.addNode(throwErr_outOfBounds);
        sparrow_arrayAssign.addNode(continueMarkOOB);
        // check that (java) offset is nonnegative
        sparrow_arrayAssign.addNode(setZero);
        sparrow_arrayAssign.addNode(setOne);
        sparrow_arrayAssign.addNode(incrementOffset);
        sparrow_arrayAssign.addNode(setIfNonnegative);
        sparrow_arrayAssign.addNode(nonnegativeCheck);
        sparrow_arrayAssign.addNode(gotoContinueNonNegative);
        sparrow_arrayAssign.addNode(negativeMark);
        sparrow_arrayAssign.addNode(throwErr_outOfBounds);
        sparrow_arrayAssign.addNode(continueMarkNonNegative);
        // convert to sparrow offset
        sparrow_arrayAssign.addNode(set4Instruction);
        sparrow_arrayAssign.addNode(mult4);
        sparrow_arrayAssign.addNode(add4);
        // add offset result to array start index
        sparrow_arrayAssign.addNode(indexInsn);
        // evaluate the RHS expression
        combine(sparrow_arrayAssign, resultCode);
        // store RHS into designated spot
        sparrow_arrayAssign.addNode(store);
        // store back into class if it is a field
        if(!isLocal(arrayName, st) && isField(arrayName, st)){
            sparrow_arrayAssign.addNode(storeFieldValue(arrayName, arrayID, st));
        }
        return sparrow_arrayAssign;
    }

    private NodeListOptional evaluateExpression(Identifier resultTmp, Expression expr, SymbolTable st){
        Identifier prev_exprResult_tmpID = st.cur.exprResult_tmpID;
        st.cur.exprResult_tmpID = resultTmp;
        NodeListOptional resultCode = this.visit(expr, st);
        st.cur.exprResult_tmpID = prev_exprResult_tmpID;
        return resultCode;
    }

    private Label newLabel(){
        // create new label name
        String tempName =  "L_" + label_counter.toString();
        // increment label counter
        label_counter++;
        // return the new temp name
        return new Label(new NodeToken(tempName));
    }

    // IfStatement Visitor
    // f0 -> "if" f1 -> "(" f2 -> Expression() f3 -> ")" f4 -> Statement() f5 -> "else" f6 -> Statement()
    @Override
    public NodeListOptional visit(IfStatement ifStmt, SymbolTable st)
    {
        // get expression result (f2)
        Identifier conditionalResult = newTemp();
        NodeListOptional conditionalCode = evaluateExpression(conditionalResult, ifStmt.f2, st);
        // make label to go to else
        Label elseLabel = newLabel();
        Instruction elseMark = new Instruction(new NodeChoice(new LabelWithColon(elseLabel)));
        // make label to get out of if
        Label continueLabel = newLabel();
        Instruction continueMark = new Instruction(new NodeChoice(new LabelWithColon(continueLabel)));
        // make instruction to get out of if
        Instruction continueInsn = new Instruction(new NodeChoice(new Goto(continueLabel)));
        // check if expression result is 0
        If check = new If(conditionalResult, elseLabel);
        Instruction conditional = new Instruction(new NodeChoice(check));
        // get "if" code (f4)
        NodeListOptional ifCode = this.visit(ifStmt.f4, st);
        // get "else" code (f6)
        NodeListOptional elseCode= this.visit(ifStmt.f6, st);
        // put code together
        NodeListOptional sparrow_if = new NodeListOptional();
        combine(sparrow_if, conditionalCode);
        sparrow_if.addNode(conditional);
        combine(sparrow_if, ifCode);
        sparrow_if.addNode(continueInsn);
        sparrow_if.addNode(elseMark);
        combine(sparrow_if, elseCode);
        sparrow_if.addNode(continueMark);
        return sparrow_if;
    }

    // WhileStatement Visitor
    //  f0 -> "while" f1 -> "(" f2 -> Expression() f3 -> ")" f4 -> Statement()
    @Override
    public NodeListOptional visit(WhileStatement whileStmt, SymbolTable st)
    {
        // make label to mark start of loop
        Label whileLabel = newLabel();
        Instruction whileMark = new Instruction(new NodeChoice(new LabelWithColon(whileLabel)));
        Instruction gotoWhileMark = new Instruction(new NodeChoice(new Goto(whileLabel)));
        // make label to mark end of loop
        Label continueLabel = newLabel();
        Instruction continueMark = new Instruction(new NodeChoice(new LabelWithColon(continueLabel)));
        // get expression result (f2)
        Identifier conditionalResult = newTemp();
        NodeListOptional conditionalCode = evaluateExpression(conditionalResult, whileStmt.f2, st);
        // check if expression result is 0, get out of loop if it is
        If check = new If(conditionalResult, continueLabel);
        Instruction conditional = new Instruction(new NodeChoice(check));
        // make loop body (f4)
        NodeListOptional whileCode = this.visit(whileStmt.f4, st);
        // put code together
        NodeListOptional sparrow_while = new NodeListOptional();
        sparrow_while.addNode(whileMark);
        combine(sparrow_while, conditionalCode);
        sparrow_while.addNode(conditional);
        combine(sparrow_while, whileCode);
        sparrow_while.addNode(gotoWhileMark);
        sparrow_while.addNode(continueMark);
        return sparrow_while;
    }

    // PrintStatement Visitor
    //  f0 -> "System.out.println" f1 -> "(" f2 -> Expression() f3 -> ")" f4 -> ";"
    @Override 
    public NodeListOptional visit(PrintStatement printStmt, SymbolTable st)
    {
        // get result of expression (f2)
        Identifier expressionResult = newTemp();
        NodeListOptional expressionCode = evaluateExpression(expressionResult, printStmt.f2, st);
        // print it out
        Instruction print = new Instruction(new NodeChoice(new Print(expressionResult)));
        // put code together
        NodeListOptional sparrow_print = new NodeListOptional();
        combine(sparrow_print, expressionCode);
        sparrow_print.addNode(print);
        return sparrow_print;
    }

    // MethodDeclaration Visitor
    // f0 -> "public" f1 -> Type() f2 -> Identifier() f3 -> "(" f4 -> ( FormalParameterList() )? 
    // f5 -> ")" f6 -> "{" f7 -> ( VarDeclaration() )* f8 -> ( Statement() )* f9 -> "return" 
    // f10 -> Expression() f11 -> ";" f12 -> "}"
    // Convert to:
    // f0 -> "func" f1 -> FunctionName() f2 -> "(" f3 -> ( Identifier() )* f4 -> ")" f5 -> Block()
    @Override
    public FunctionDeclaration visit(MethodDeclaration methodDec, SymbolTable st)
    {
        // get method
        String methodName = methodDec.f2.f0.toString();
        MethodLike method = st.cur.cur_class.methods.get(methodName);
        // scope in to method
        MethodLike prevMethod = st.cur.cur_method;
        st.cur.cur_method = method;
        // make function name name using (f2) and owner from [st]
        String methodOwner= method.owner;
        String functionName = methodOwner + methodName;
        FunctionName funcName = new FunctionName(new NodeToken(functionName));
        // make function parameter list using parameter list from [st]
        ArrayList<String> methodParams = method.params;
        NodeListOptional functionParams = new NodeListOptional(thisID);
        for(String param : methodParams){
            Identifier funcParam = new Identifier(new NodeToken("_"+param));
            functionParams.addNode(funcParam);
        }
        // make body (f7, f8)
        NodeListOptional varDecCode = this.visit(methodDec.f7, st);
        NodeListOptional stmtCode = this.visit(methodDec.f8, st);
        NodeListOptional bodyCode = new NodeListOptional();
        combine(bodyCode, varDecCode);
        combine(bodyCode, stmtCode);
        // make return (f10)
        Identifier returnID = newTemp();
        NodeListOptional returnCode = evaluateExpression(returnID, methodDec.f10, st);
        combine(bodyCode, returnCode);
        // scope back out of method
        st.cur.cur_method = prevMethod;
        // put function together
        Block funcCode = new Block(bodyCode, returnID);
        FunctionDeclaration sparrowFunc = new FunctionDeclaration(funcName, functionParams, funcCode);
        return sparrowFunc;
    }

    // Expression Visitor
    @Override
    public NodeListOptional visit(Expression expr, SymbolTable st)
    {
        return this.visit(expr.f0, st);
    }

    private NodeListOptional evaluateExpression(Identifier resultTmp, PrimaryExpression expr, SymbolTable st){
        Identifier prev_exprResult_tmpID = st.cur.exprResult_tmpID;
        st.cur.exprResult_tmpID = resultTmp;
        NodeListOptional resultCode = this.visit(expr, st);
        st.cur.exprResult_tmpID = prev_exprResult_tmpID;
        return resultCode;
    }

    // f0 -> AndExpression() | CompareExpression() | PlusExpression() | MinusExpression() | TimesExpression() | ArrayLookup() | ArrayLength() | MessageSend() | PrimaryExpression(
    // AndExpression Visitor
    // f0 -> PrimaryExpression() f1 -> "&&" f2 -> PrimaryExpression()
    @Override
    public NodeListOptional visit(AndExpression andExpr, SymbolTable st)
    {
        // get identifier where result will be stored
        Identifier resultTmp = st.cur.exprResult_tmpID;
        // make label to set value to 0
        Label setZeroLabel = newLabel();
        Instruction setZeroMark = new Instruction(new NodeChoice(new LabelWithColon(setZeroLabel)));
        // get first expression result (f0)
        Identifier firstExprResult = newTemp();
        NodeListOptional firstExprCode = evaluateExpression(firstExprResult, andExpr.f0, st);
        // check if first expression result is false
        If firstCheck = new If(firstExprResult, setZeroLabel);
        Instruction firstExprCheck = new Instruction(new NodeChoice(firstCheck));
        // set result value to 0 (if first expression is false)
        Instruction setZero = new Instruction(new NodeChoice(new SetInteger(resultTmp, zeroIL)));
        // get second expression result (f2)
        Identifier secondExprResult = newTemp();
        NodeListOptional secondExprCode = evaluateExpression(secondExprResult, andExpr.f2, st);
        // set identifier to second expression result
        Instruction recordResult = new Instruction(new NodeChoice(new Move(resultTmp, secondExprResult)));
        // make label to skip the short-circuit section
        Label continueLabel = newLabel();
        Instruction continueInsn = new Instruction(new NodeChoice(new Goto(continueLabel)));
        Instruction continueMark = new Instruction(new NodeChoice(new LabelWithColon(continueLabel)));
        // put code together
        NodeListOptional sparrow_andExpression = new NodeListOptional();
        combine(sparrow_andExpression, firstExprCode);
        sparrow_andExpression.addNode(firstExprCheck);
        combine(sparrow_andExpression, secondExprCode);
        sparrow_andExpression.addNode(recordResult);
        sparrow_andExpression.addNode(continueInsn);
        sparrow_andExpression.addNode(setZeroMark);
        sparrow_andExpression.addNode(setZero);
        sparrow_andExpression.addNode(continueMark);
        return sparrow_andExpression;
    }

    // CompareExpression Visitor
    // minijava: f0 -> PrimaryExpression() f1 -> "<" f2 -> PrimaryExpression()
    // sparrow:  f0 -> Identifier() f1 -> "=" f2 -> Identifier() f3 -> "<" f4 -> Identifier()
    @Override
    public NodeListOptional visit(CompareExpression cmpExpr, SymbolTable st)
    {
        // get identifier where result will be stored
        Identifier resultTmp = st.cur.exprResult_tmpID;
        // evaluate the first expression (f0)
        Identifier firstExprResult = newTemp();
        NodeListOptional firstExprCode = evaluateExpression(firstExprResult, cmpExpr.f0, st);
        // evaluate the second expression (f2)
        Identifier secondExprResult = newTemp();
        NodeListOptional secondExprCode = evaluateExpression(secondExprResult, cmpExpr.f2, st);
        // do the comparison
        Instruction compare = new Instruction(new NodeChoice(new LessThan(resultTmp, firstExprResult, secondExprResult)));
        // put the code together
        NodeListOptional sparrow_cmpExpr = new NodeListOptional();
        combine(sparrow_cmpExpr, firstExprCode);
        combine(sparrow_cmpExpr, secondExprCode);
        sparrow_cmpExpr.addNode(compare);
        return sparrow_cmpExpr;
    }

    // PlusExpression Visitor
    //  f0 -> PrimaryExpression() f1 -> "+" f2 -> PrimaryExpression()
    @Override
    public NodeListOptional visit(PlusExpression plusExpr, SymbolTable st)
    {
        // get ID where result is to be stored
        Identifier resultTmp = st.cur.exprResult_tmpID;
        // evaluate first expression (f0)
        Identifier firstExprResult = newTemp();
        NodeListOptional firstExprCode = evaluateExpression(firstExprResult, plusExpr.f0, st);
        // evaluate second expression (f2)
        Identifier secondExprResult = newTemp();
        NodeListOptional secondExprCode = evaluateExpression(secondExprResult, plusExpr.f2, st);
        // store result
        Instruction add = new Instruction(new NodeChoice(new Add(resultTmp, firstExprResult, secondExprResult)));
        // put code together
        NodeListOptional sparrow_plusExpr = new NodeListOptional();
        combine(sparrow_plusExpr, firstExprCode);
        combine(sparrow_plusExpr, secondExprCode);
        sparrow_plusExpr.addNode(add);
        return sparrow_plusExpr;
    }

    // MinusExpression Visitor
    @Override
    public NodeListOptional visit(MinusExpression minusExpr, SymbolTable st)
    {
        // get ID where result is to be stored
        Identifier resultTmp = st.cur.exprResult_tmpID;
        // evaluate first expression (f0)
        Identifier firstExprResult = newTemp();
        NodeListOptional firstExprCode = evaluateExpression(firstExprResult, minusExpr.f0, st);
        // evaluate second expression (f2)
        Identifier secondExprResult = newTemp();
        NodeListOptional secondExprCode = evaluateExpression(secondExprResult, minusExpr.f2, st);
        // store result
        Instruction subtract = new Instruction(new NodeChoice(new Subtract(resultTmp, firstExprResult, secondExprResult)));
        // put code together
        NodeListOptional sparrow_minusExpr = new NodeListOptional();
        combine(sparrow_minusExpr, firstExprCode);
        combine(sparrow_minusExpr, secondExprCode);
        sparrow_minusExpr.addNode(subtract);
        return sparrow_minusExpr;
    }

    // TimesExpression Visitor
    @Override
    public NodeListOptional visit(TimesExpression timesExpr, SymbolTable st)
    {
        // get ID where result is to be stored
        Identifier resultTmp = st.cur.exprResult_tmpID;
        // evaluate first expression (f0)
        Identifier firstExprResult = newTemp();
        NodeListOptional firstExprCode = evaluateExpression(firstExprResult, timesExpr.f0, st);
        // evaluate second expression (f2)
        Identifier secondExprResult = newTemp();
        NodeListOptional secondExprCode = evaluateExpression(secondExprResult, timesExpr.f2, st);
        // store result
        Instruction multiply = new Instruction(new NodeChoice(new Multiply(resultTmp, firstExprResult, secondExprResult)));
        // put code together
        NodeListOptional sparrow_timesExpr = new NodeListOptional();
        combine(sparrow_timesExpr, firstExprCode);
        combine(sparrow_timesExpr, secondExprCode);
        sparrow_timesExpr.addNode(multiply);
        return sparrow_timesExpr;
    }

    // ArrayLookup Visitor
    //  f0 -> PrimaryExpression() f1 -> "[" f2 -> PrimaryExpression() f3 -> "]"
    @Override
    public NodeListOptional visit(ArrayLookup arrayLookup, SymbolTable st)
    {
        // get tmp where result is to be stored
        Identifier resultTmp = st.cur.exprResult_tmpID;
        // get name of array (f0) 
        Identifier arrayNameTmp = newTemp();
        NodeListOptional arrayNameCode = evaluateExpression(arrayNameTmp, arrayLookup.f0,st);
        // check that array has been initialized
        Label continueLabel = newLabel();
        Instruction continueMark = new Instruction(new NodeChoice(new LabelWithColon(continueLabel)));
        Instruction gotoContinue = new Instruction(new NodeChoice(new Goto(continueLabel)));
        Label uninitializedLabel = newLabel();
        Instruction uninitializedMark = new Instruction(new NodeChoice(new LabelWithColon(uninitializedLabel)));
        Instruction initializedCheck = new Instruction(new NodeChoice(new If(arrayNameTmp, uninitializedLabel)));
        
        // get result of index expression (f2)
        Identifier indexTmp = newTemp();
        NodeListOptional indexCode = evaluateExpression(indexTmp, arrayLookup.f2, st);
        // check that index expression is in-bounds
        Label continueLabelOOB = newLabel();
        Instruction continueMarkOOB = new Instruction(new NodeChoice(new LabelWithColon(continueLabelOOB)));
        Instruction gotoContinueOOB = new Instruction(new NodeChoice(new Goto(continueLabelOOB)));
        Label outOfBoundsLabel = newLabel();
        Instruction outOfBoundsMark = new Instruction(new NodeChoice(new LabelWithColon(outOfBoundsLabel)));
        // get array size
        Identifier arraySizeTmp = newTemp();
        Instruction loadArraySize = new Instruction(new NodeChoice(new Load(arraySizeTmp, arrayNameTmp, zeroIL)));
        Identifier inBoundsTmp = newTemp();
        Instruction setInBounds = new Instruction(new NodeChoice(new LessThan(inBoundsTmp, indexTmp, arraySizeTmp)));
        Instruction inBoundsCheck = new Instruction(new NodeChoice(new If(inBoundsTmp, outOfBoundsLabel)));
        // check that index expression is nonnegative
        Label continueLabelNegative = newLabel();
        Instruction continueMarkNegative = new Instruction(new NodeChoice(new LabelWithColon(continueLabelNegative)));
        Instruction gotoContinueNegative = new Instruction(new NodeChoice(new Goto(continueLabelNegative)));
        Label negativeLabel = newLabel();
        Instruction negativeMark = new Instruction(new NodeChoice(new LabelWithColon(negativeLabel)));
        Instruction setZero = new Instruction(new NodeChoice(new SetInteger(zeroID, zeroIL)));
        Identifier indexPlus1Temp = newTemp();
        Instruction setOne = new Instruction(new NodeChoice(new SetInteger(oneID, oneIL)));
        Instruction incrementIndex = new Instruction(new NodeChoice(new Add(indexPlus1Temp, indexTmp, oneID)));
        Identifier nonnegativeTmp = newTemp();
        Instruction setIfNonnegative = new Instruction(new NodeChoice(new LessThan(nonnegativeTmp, zeroID, indexPlus1Temp)));
        Instruction nonnegativeCheck = new Instruction(new NodeChoice(new If(nonnegativeTmp,negativeLabel)));
        // add 1 to this expression and multiply by 4 to get actual offset needed
        Instruction set4 = new Instruction(new NodeChoice(new SetInteger(fourID, fourIL)));
        Instruction mult4 = new Instruction(new NodeChoice(new Multiply(indexTmp, indexTmp, fourID)));
        Instruction add4 = new Instruction(new NodeChoice(new Add(indexTmp, indexTmp, fourID)));
        // add array address and index result to get a usable heap address
        Instruction combineOffset = new Instruction(new NodeChoice(new Add(indexTmp, arrayNameTmp, indexTmp)));
        // store array-lookup result in result tmp
        Instruction loadValue = new Instruction(new NodeChoice(new Load(resultTmp, indexTmp, zeroIL)));
        // put code together
        NodeListOptional sparrow_arrayLookup = new NodeListOptional();
        // get arrayname
        combine(sparrow_arrayLookup, arrayNameCode);
        // check if array is initialized
        sparrow_arrayLookup.addNode(initializedCheck);
        sparrow_arrayLookup.addNode(gotoContinue);
        sparrow_arrayLookup.addNode(uninitializedMark);
        sparrow_arrayLookup.addNode(throwErr_nullPointer);
        sparrow_arrayLookup.addNode(continueMark);
        // figure out where we are supposed to index
        combine(sparrow_arrayLookup, indexCode);
        // make sure index is inside array
        sparrow_arrayLookup.addNode(loadArraySize);
        sparrow_arrayLookup.addNode(setInBounds);
        sparrow_arrayLookup.addNode(inBoundsCheck);
        sparrow_arrayLookup.addNode(gotoContinueOOB);
        sparrow_arrayLookup.addNode(outOfBoundsMark);
        sparrow_arrayLookup.addNode(throwErr_outOfBounds);
        sparrow_arrayLookup.addNode(continueMarkOOB);
        // make sure index is positive
        sparrow_arrayLookup.addNode(setZero);
        sparrow_arrayLookup.addNode(setOne);
        sparrow_arrayLookup.addNode(incrementIndex);
        sparrow_arrayLookup.addNode(setIfNonnegative);
        sparrow_arrayLookup.addNode(nonnegativeCheck);
        sparrow_arrayLookup.addNode(gotoContinueNegative);
        sparrow_arrayLookup.addNode(negativeMark);
        sparrow_arrayLookup.addNode(throwErr_outOfBounds);
        sparrow_arrayLookup.addNode(continueMarkNegative);
        // get value
        sparrow_arrayLookup.addNode(set4);
        sparrow_arrayLookup.addNode(mult4);
        sparrow_arrayLookup.addNode(add4);
        sparrow_arrayLookup.addNode(combineOffset);
        sparrow_arrayLookup.addNode(loadValue);
        return sparrow_arrayLookup;
    }

    // ArrayLength Visitor
    //  f0 -> PrimaryExpression() f1 -> "." f2 -> "length"
    @Override
    public NodeListOptional visit(ArrayLength arrayLength, SymbolTable st)
    {
        // get tmp where result is to be stored
        Identifier resultTmp = st.cur.exprResult_tmpID;
        // get name of array (f0)
        Identifier arrayNameTmp = newTemp();
        NodeListOptional arrayNameCode = evaluateExpression(arrayNameTmp, arrayLength.f0,st);
        // check that array has been initialized
        Label uninitializedArrayLabel = newLabel();
        Instruction uninitializedArrayMark = new Instruction(new NodeChoice(new LabelWithColon(uninitializedArrayLabel)));
        Label arrayInitializedContinueLabel = newLabel();
        Instruction arrayInitializedContinueMark = new Instruction(new NodeChoice(new LabelWithColon(arrayInitializedContinueLabel)));
        Instruction gotoArrayInitializedContinueLabel = new Instruction(new NodeChoice(new Goto(arrayInitializedContinueLabel)));
        Instruction checkArrayInitialized = new Instruction(new NodeChoice(new If(arrayNameTmp, uninitializedArrayLabel)));
        // get 0th entry from array, place in result tmp
        Instruction getLength = new Instruction(new NodeChoice(new Load(resultTmp, arrayNameTmp, zeroIL)));
        // put code together
        NodeListOptional sparrow_arrayLength = new NodeListOptional();
        combine(sparrow_arrayLength, arrayNameCode);
        sparrow_arrayLength.addNode(checkArrayInitialized);
        sparrow_arrayLength.addNode(gotoArrayInitializedContinueLabel);
        sparrow_arrayLength.addNode(uninitializedArrayMark);
        sparrow_arrayLength.addNode(throwErr_nullPointer);
        sparrow_arrayLength.addNode(arrayInitializedContinueMark);
        sparrow_arrayLength.addNode(getLength);
        return sparrow_arrayLength;
    }

    private String getClassType(PrimaryExpression pe, SymbolTable st)
    {
        cs132.minijava.syntaxtree.Node n = pe.f0.choice;
        // check possible primary expression types
        // ones that cannot call functions
        if((n instanceof IntegerLiteral)
        || (n instanceof TrueLiteral)
        || (n instanceof FalseLiteral)
        || (n instanceof ArrayAllocationExpression)
        || (n instanceof NotExpression))
        {
            return null;
        }
        else if(n instanceof cs132.minijava.syntaxtree.Identifier){
            // check table
            cs132.minijava.syntaxtree.Identifier id = (cs132.minijava.syntaxtree.Identifier) n;
            String varName = id.f0.toString();
            // check method locals/params
            if(st.cur.cur_method != null){
                MethodLike curMethod = st.cur.cur_method;
                if(curMethod.localsTypes.containsKey(varName)){
                    return curMethod.localsTypes.get(varName);
                }
                else if(curMethod.paramsTypes.containsKey(varName)){
                    return curMethod.paramsTypes.get(varName);
                }
            }
            // check class fields
            if(st.cur.cur_class != null){
                ClassLike curClass = st.cur.cur_class;
                if(curClass.fieldTypes.containsKey(varName)){
                    return curClass.fieldTypes.get(varName);
                }
            }
            // if not found, give up
            return null;
        }
        else if(n instanceof ThisExpression){
            // check table
            return st.cur.cur_class.className;
        }
        else if (n instanceof AllocationExpression){
            // check identifier
            AllocationExpression ae = (AllocationExpression) n;
            return ae.f1.f0.toString();
        }
        else{
            // must be BracketExpression -> MessageSend | PrimaryExpression
            BracketExpression brackExp = (BracketExpression) n;
            Expression nestedNode = brackExp.f1;
            cs132.minijava.syntaxtree.Node nInner = nestedNode.f0.choice;
            if(nInner instanceof PrimaryExpression){
                return getClassType((PrimaryExpression) nInner, st);
            }
            // deal with the MessageSend case
            else{
                // nInner is a messageSend
                MessageSend msgSend = (MessageSend) nInner;
                // get type of object calling messageSend
                PrimaryExpression caller = msgSend.f0;
                String callerType = getClassType(caller, st);
                // get return type of method being called
                String methodName = msgSend.f2.f0.toString();
                ClassLike callerClass = st.table.get(callerType);
                MethodLike method = callerClass.methods.get(methodName);
                String returnType = method.returnType;
                // return this type
                return returnType;
            }
        }
    }

    // MessageSend Visitor
    // f0 -> PrimaryExpression() f1 -> "." f2 -> Identifier() f3 -> "(" f4 -> ( ExpressionList() )? f5 -> ")"
    @Override
    public NodeListOptional visit(MessageSend messageSend, SymbolTable st)
    {
        // get tmp where result is to be stored
        Identifier resultTmp = st.cur.exprResult_tmpID;
        // get name of object calling method (f0)
        Identifier objectTmp = newTemp();
        NodeListOptional objectCode = evaluateExpression(objectTmp, messageSend.f0, st);
        // check that object has been initialized
        Label uninitializedLabel = newLabel();
        Instruction uninitializedMark = new Instruction(new NodeChoice(new LabelWithColon(uninitializedLabel)));
        Label initializedContinueLabel = newLabel();
        Instruction initializedContinueMark = new Instruction(new NodeChoice(new LabelWithColon(initializedContinueLabel)));
        Instruction gotoInitializedContinueMark = new Instruction(new NodeChoice(new Goto(initializedContinueLabel)));
        Instruction initializedCheck = new Instruction(new NodeChoice(new If(objectTmp, uninitializedLabel)));
        // get type of object [st]
        String objectType = getClassType(messageSend.f0, st);
        // get method name (f2)
        String methodName = messageSend.f2.f0.toString();
        // get method table
        Identifier methodTableTmp = newTemp();
        Instruction getMethodTable = new Instruction(new NodeChoice(new Load(methodTableTmp, objectTmp, zeroIL)));
        // get function index in table
        ClassLike callerClass = st.table.get(objectType);
        MethodLike calledMethod = callerClass.methods.get(methodName);
        Integer index = calledMethod.index;
        // get function pointer from method table
        index *= 4;
        IntegerLiteral offset = new IntegerLiteral(new NodeToken(index.toString()));
        Identifier funcTmp = newTemp();
        Instruction loadFunction = new Instruction(new NodeChoice(new Load(funcTmp, methodTableTmp, offset)));
        // evaluate parameter expressions
        NodeListOptional paramEvaluations = new NodeListOptional();
        NodeListOptional paramTmps = new NodeListOptional(objectTmp);
        if(messageSend.f4.present()){
            ExpressionList exprList = (ExpressionList) messageSend.f4.node;
            // handle first expression
            Expression initialExpr = exprList.f0;
            Identifier expr1Temp = newTemp();
            NodeListOptional expr1Code = evaluateExpression(expr1Temp, initialExpr, st);
            paramTmps.addNode(expr1Temp);
            paramEvaluations.addNode(expr1Code);
            // handle rest of the expressions
            cs132.minijava.syntaxtree.NodeListOptional tailExprs = exprList.f1;
            if(tailExprs.present()){
            for(cs132.minijava.syntaxtree.Node exprRest : tailExprs.nodes){
                ExpressionRest commaExpr = (ExpressionRest) exprRest;
                Expression expr = commaExpr.f1;
                Identifier exprTemp = newTemp();
                NodeListOptional exprCode = evaluateExpression(exprTemp, expr, st);
                paramTmps.addNode(exprTemp);
                paramEvaluations.addNode(exprCode);
            }
            }
        }
        // call function and store result
        Instruction callFunction = new Instruction(new NodeChoice(new Call(resultTmp, funcTmp, paramTmps)));
        // put code together
        NodeListOptional sparrow_msgSend = new NodeListOptional();
        combine(sparrow_msgSend, objectCode);
        // check that object is initialized
        sparrow_msgSend.addNode(initializedCheck);
        sparrow_msgSend.addNode(gotoInitializedContinueMark);
        sparrow_msgSend.addNode(uninitializedMark);
        sparrow_msgSend.addNode(throwErr_nullPointer);
        sparrow_msgSend.addNode(initializedContinueMark);
        // continue with msgSend
        sparrow_msgSend.addNode(getMethodTable);
        sparrow_msgSend.addNode(loadFunction);
        combine(sparrow_msgSend, paramEvaluations);
        sparrow_msgSend.addNode(callFunction);
        return sparrow_msgSend;
    }

    // PrimaryExpression Visitor
    @Override
    public NodeListOptional visit(PrimaryExpression primaryExpr, SymbolTable st){
        return this.visit(primaryExpr.f0, st);
    }
    // f0 -> IntegerLiteral() | TrueLiteral() | FalseLiteral() | Identifier() | ThisExpression() | ArrayAllocationExpression() | AllocationExpression() | NotExpression() | BracketExpression()
    
    // IntegerLiteral Visitor
    @Override
    public Instruction visit(cs132.minijava.syntaxtree.IntegerLiteral integerLiteral, SymbolTable st)
    {
        // get tmp where result is to be stored
        Identifier resultTmp = st.cur.exprResult_tmpID;
        // get integer literal
        String integer = integerLiteral.f0.toString();
        IntegerLiteral intLiteral = new IntegerLiteral(new NodeToken(integer));
        // store integer literal into result tmp
        Instruction setInteger = new Instruction(new NodeChoice(new SetInteger(resultTmp, intLiteral)));
        return setInteger;
    }

    // TrueLiteral Visitor
    @Override
    public Instruction visit(TrueLiteral trueLiteral, SymbolTable st)
    {
        // get tmp where result is to be stored
        Identifier resultTmp = st.cur.exprResult_tmpID;
        // store integer literal 1 into result tmp
        Instruction setTrue = new Instruction(new NodeChoice(new SetInteger(resultTmp, oneIL)));
        // return instruction
        return setTrue;
    }
    
    // FalseLiteral Visitor
    @Override
    public Instruction visit(FalseLiteral falseLiteral, SymbolTable st)
    {
        // get tmp where result is to be stored
        Identifier resultTmp = st.cur.exprResult_tmpID;
        // store integer literal 0 into result tmp
        Instruction setFalse = new Instruction(new NodeChoice(new SetInteger(resultTmp, zeroIL)));
        // return instruction
        return setFalse;
    }

    // Identifier Visitor
    @Override
    public NodeListOptional visit(cs132.minijava.syntaxtree.Identifier id, SymbolTable st)
    {
        // get tmp where result is to be stored
        Identifier resultTmp = st.cur.exprResult_tmpID;
        // get variable name
        String varName = id.f0.toString();
        // get proper identifier for variable name
        Identifier varID = makeIdentifier(varName, st);
        Instruction setVarID = getValue(varName, varID, st);
        // set result tmp to variable identifier
        Instruction setID = new Instruction(new NodeChoice(new Move(resultTmp, varID)));
        // return instruction
        NodeListOptional sparrow_identifier = new NodeListOptional();
        sparrow_identifier.addNode(setVarID);
        sparrow_identifier.addNode(setID);
        return sparrow_identifier;
    }

    // ThisExpression Visitor
    @Override
    public Instruction visit(ThisExpression thisExpr, SymbolTable st)
    {
        // get tmp where result is to be stored
        Identifier resultTmp = st.cur.exprResult_tmpID;
        // store thisID into result tmp
        Instruction setThis = new Instruction(new NodeChoice(new Move(resultTmp, thisID)));
        // return instruction
        return setThis;
    }

    // ArrayAllocationExpression Visitor
    //  f0 -> "new" f1 -> "int" f2 -> "[" f3 -> Expression() f4 -> "]"
    @Override
    public NodeListOptional visit(ArrayAllocationExpression arrayAllocExpr, SymbolTable st)
    {
        // get tmp where result is to be stored
        Identifier resultTmp = st.cur.exprResult_tmpID;
        // evaluate expression to determine how much space is needed for the array (f3)
        Identifier arrayLenResult = newTemp();
        NodeListOptional arrayLenCode = evaluateExpression(arrayLenResult, arrayAllocExpr.f3, st);
        // add 1 to this expression and multiply by 4 to get total array space needed
        Identifier arraySpace = new Identifier(new NodeToken("array_space"));
        Instruction setArraySpace = new Instruction(new NodeChoice(new Move(arraySpace, arrayLenResult)));
        Instruction set4 = new Instruction(new NodeChoice(new SetInteger(fourID, fourIL)));
        Instruction mult4 = new Instruction(new NodeChoice(new Multiply(arraySpace, arraySpace, fourID)));
        Instruction add4 = new Instruction(new NodeChoice(new Add(arraySpace, arraySpace, fourID)));
        // call alloc to get the amount of desired space
        Instruction allocate = new Instruction(new NodeChoice(new Alloc(resultTmp, arraySpace)));
        // set first spot in array to array length
        Instruction storeLength = new Instruction(new NodeChoice(new Store(resultTmp, zeroIL, arrayLenResult)));
        // put code together
        NodeListOptional sparrow_arrayAllocExpr = new NodeListOptional();
        combine(sparrow_arrayAllocExpr, arrayLenCode);
        sparrow_arrayAllocExpr.addNode(setArraySpace);
        sparrow_arrayAllocExpr.addNode(set4);
        sparrow_arrayAllocExpr.addNode(mult4);
        sparrow_arrayAllocExpr.addNode(add4);
        sparrow_arrayAllocExpr.addNode(allocate);
        sparrow_arrayAllocExpr.addNode(storeLength);
        return sparrow_arrayAllocExpr;
    }

    // AllocationExpression Visitor
    // f0 -> "new" f1 -> Identifier() f2 -> "(" f3 -> ")"
    @Override
    public NodeListOptional visit(AllocationExpression allocExpr, SymbolTable st)
    {
        // get tmp where result is to be stored
        Identifier resultTmp = st.cur.exprResult_tmpID;
        // get name of method to be called (f1)
        String className = allocExpr.f1.f0.toString();
        String methodName = "new" + className;
        FunctionName constructor = new FunctionName(new NodeToken(methodName));
        // set tmp equal to the method
        Identifier constructorTmp = newTemp();
        Instruction setConstructor = new Instruction(new NodeChoice(new SetFuncName(constructorTmp, constructor)));
        // call the method
        NodeListOptional params = new NodeListOptional();
        Instruction callConstructor = new Instruction(new NodeChoice(new Call(resultTmp, constructorTmp, params)));
        // put code together
        NodeListOptional sparrow_allocExpr = new NodeListOptional();
        sparrow_allocExpr.addNode(setConstructor);
        sparrow_allocExpr.addNode(callConstructor);
        return sparrow_allocExpr;
    }

    // NotExpression Visitor
    // f0 -> "!" f1 -> Expression()
    @Override
    public NodeListOptional visit(NotExpression notExpr, SymbolTable st)
    {
        // get tmp where result is to be stored
        Identifier resultTmp = st.cur.exprResult_tmpID;
        // get result of enclosed expression
        Identifier intermediateResult = newTemp();
        NodeListOptional intermediateResultCode = evaluateExpression(intermediateResult, notExpr.f1, st);
        // negate that result, using a subtract
        Identifier oneTemp = newTemp();
        Instruction setOneTemp = new Instruction(new NodeChoice(new SetInteger(oneTemp, oneIL)));
        Instruction negate = new Instruction(new NodeChoice(new Subtract(resultTmp, oneTemp, intermediateResult)));
        // put code together
        NodeListOptional sparrow_notExpr = new NodeListOptional();
        combine(sparrow_notExpr, intermediateResultCode);
        sparrow_notExpr.addNode(setOneTemp);
        sparrow_notExpr.addNode(negate);
        return sparrow_notExpr;
    }

    // BracketExpression Visitor
    @Override
    public NodeListOptional visit(BracketExpression brackExpr, SymbolTable st)
    {
        return this.visit(brackExpr.f1, st);
    }
}
