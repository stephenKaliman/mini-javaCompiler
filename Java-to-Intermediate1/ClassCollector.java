import cs132.minijava.visitor.GJDepthFirst;
import cs132.minijava.syntaxtree.*;

import java.util.HashMap;

public class ClassCollector extends GJDepthFirst<String, Cursor>{
    HashMap<String, ClassLike> classTable;

    public ClassCollector(){
        classTable = new HashMap<String, ClassLike>();
    }

    public static void fail(String msg)
    {
        System.out.println(msg);
        System.exit(0);
    }

    // use default Goal visitor

    // MainClass Visitor
    // f0 -> "class" f1 -> Identifier() f2 -> "{" f3 -> "public" f4 -> "static" f5 -> "void" f6 -> "main" f7 -> "(" 
    // f8 -> "String" f9 -> "[" f10 -> "]" f11 -> Identifier() f12 -> ")" f13 -> "{" f14 -> ( VarDeclaration() )* 
    // f15 -> ( Statement() )* f16 -> "}" f17 -> "}"
    @Override
    public String visit(MainClass mainClass, Cursor cur)
    {
        // get class name (f1)
        String className = mainClass.f1.accept(this, cur);
        // make corresponding class object 
        ClassLike newClass = new ClassLike(className);
        // add class to table
        classTable.put(className, newClass);
        // scope into class
        cur.cur_class = newClass;
        // get method name (main)
        String methodName = "main";
        // return type is void
        String returnType = null;
        // make method object
        MethodLike mainMethod = new MethodLike(methodName, className, returnType);
        // add method to current class
        cur.cur_class.methods.put(methodName, mainMethod);
        // scope into method
        cur.cur_method = mainMethod;
        // no method params
        // fill method locals (f14)
        mainClass.f14.accept(this, cur);
        // scope out of method
        cur.cur_method = null;
        // scope back out of class
        cur.cur_class = null;
        return null;
    }

    // use default TypeDeclaration visitor

    // ClassDeclaration
    // f0 -> "class" f1 -> Identifier() f2 -> "{" 
    // f3 -> ( VarDeclaration() )* f4 -> ( MethodDeclaration() )* f5 -> "}"
    @Override
    public String visit(ClassDeclaration classDec, Cursor cur)
    {
        // get class name (f1)
        String className = classDec.f1.accept(this, cur);
        // make corresponding class object
        ClassLike newClass = new ClassLike(className);
        // add class to table
        classTable.put(className, newClass);
        // set curClass to class
        cur.cur_class = newClass;
        // call accept on fields (f3)
        classDec.f3.accept(this, cur);
        // call accept on methods (f4)
        classDec.f4.accept(this, cur);
        // scope back out of class
        cur.cur_class = null;

        return null;
    }

    // ClassExtendsDeclaration
    //  f0 -> "class" f1 -> Identifier() f2 -> "extends" f3 -> Identifier() 
    // f4 -> "{" f5 -> ( VarDeclaration() )* f6 -> ( MethodDeclaration() )* f7 -> "}"
    @Override
    public String visit(ClassExtendsDeclaration classExtDec, Cursor cur)
    {
        // get class name (f1)
        String className = classExtDec.f1.accept(this, cur);
        // make corresponding class object
        ClassLike newClass = new ClassLike(className);
        // add parent class (name) to object (f3)
        String superclassName = classExtDec.f3.accept(this, cur);
        newClass.superclassName = superclassName;
        // add class to table
        classTable.put(className, newClass);
        // set curClass to class
        cur.cur_class = newClass;
        // call accept on fields (f5)
        classExtDec.f5.accept(this, cur);
        // call accept on methods (f6)
        classExtDec.f6.accept(this, cur);
        // scope back out of class
        cur.cur_class = null;
        return null;
    }

    // VarDeclaration
    //  f0 -> Type() f1 -> Identifier() f2 -> ";"
    @Override
    public String visit(VarDeclaration varDec, Cursor cur)
    {
        // make sure current class is not null
        if(cur.cur_class == null){
            fail("curClass null in ClassCollector->VarDeclaration visitor");
        }
        // if we are not in a method, add to fields
        else if(cur.cur_method == null){
            // add var name to current class (f1)
            String varName = varDec.f1.accept(this, cur);
            cur.cur_class.fieldIndices.put(varName, cur.cur_class.fieldIndices.size());
            // keep track of var type as well (f0)
            String varType = varDec.f0.accept(this, cur);
            cur.cur_class.fieldTypes.put(varName, varType);
        }
        // otherwise, add to the method locals
        else{
            // get var name (f1)
            String varName = varDec.f1.accept(this,cur);
            // get var type (f0)
            String varType = varDec.f0.accept(this,cur);
            // add to table
            cur.cur_method.localsTypes.put(varName, varType);
        }

        return null;
    }

    // Type (f0->NodeChoice)
    @Override
    public String visit(Type t, Cursor cur)
    {
        return t.f0.choice.accept(this, cur);
    }

    // MethodDeclaration
    // f0 -> "public" f1 -> Type() f2 -> Identifier() f3 -> "(" 
    // f4 -> ( FormalParameterList() )?  f5 -> ")" f6 -> "{" 
    // f7 -> ( VarDeclaration() )* f8 -> ( Statement() )* 
    // f9 -> "return" f10 -> Expression() f11 -> ";" f12 -> "}"
    @Override
    public String visit(MethodDeclaration methodDec, Cursor cur)
    {
        // make sure current class is not null
        if(cur.cur_class == null){
            fail("curClass null in ClassCollector->MethodDeclaration visitor");
        }
        ClassLike curClass = cur.cur_class;
        // get method name (f2)
        String methodName = methodDec.f2.f0.toString();
        // get method return type
        String returnType = methodDec.f1.accept(this, cur);
        // make method object (set name and owner)
        String curClassName = curClass.className;
        MethodLike curMethod = new MethodLike(methodName, curClassName, returnType);
        // add method to current class
        curClass.methods.put(methodName, curMethod);
        // scope into method
        cur.cur_method = curMethod;
        // fill method params and types (f4)
        methodDec.f4.accept(this, cur);
        // fill method locals (f7)
        methodDec.f7.accept(this, cur);
        // scope out of method
        cur.cur_method = null;
        return null;
    }

    // FormalParameter Visitor
    // f0 -> Type() f1 -> Identifier()
    @Override
    public String visit(FormalParameter formalParam, Cursor cur)
    {
        // make sure current class and method are not null
        if(cur.cur_class == null || cur.cur_method == null){
            fail("curClass or curMethod is null in ClassCollector->FormalParameter Visitor");
        }
        // get param name (f1)
        String paramName = formalParam.f1.f0.toString();
        // get param type (f0)
        String paramType = formalParam.f0.accept(this,cur);
        // add parameter to list
        cur.cur_method.params.add(paramName);
        // add parameter type
        cur.cur_method.paramsTypes.put(paramName, paramType);
        return null;
    }

    // Identifier visitor
    // f0 ->
    @Override
    public String visit(Identifier id, Cursor cur)
    {
        return id.f0.toString();
    }

    // Fill in superclass for all classes
    public void fillSuperclasses()
    {
        for(String className : classTable.keySet())
        {
            // get current class object
            ClassLike cur_class = classTable.get(className);
            // move on if current class extends nothing
            if(cur_class.superclassName == null){continue;}
            // otherwise, gather superclass name
            String superclassName = cur_class.superclassName;
            // get superclass object
            ClassLike superclass = classTable.get(superclassName);
            // fill in cur_class's superclass
            cur_class.superclass = superclass;
        }
        return;
    }
}
