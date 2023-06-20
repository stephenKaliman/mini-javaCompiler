import java.io.InputStream;
import java.util.HashMap;

import cs132.minijava.syntaxtree.*;
import cs132.minijava.MiniJavaParser;
import cs132.minijava.ParseException;

import cs132.IR.syntaxtree.Node;
 
public class J2S{
    public static void main(String [] args) {
        try{
            InputStream in = System.in;
            Goal root = new MiniJavaParser(in).Goal();
            // build table
            ClassCollector collector = new ClassCollector();
            Cursor cur = new Cursor();
            root.accept(collector, cur);
            // fill in superclass pointers 
            collector.fillSuperclasses();
            // complete inheritance
            HashMap<String, ClassLike> classTable = collector.classTable;
            for(ClassLike curClass : classTable.values())
            {
                curClass.fill();
            }
            // create symbol table
            SymbolTable st = new SymbolTable(classTable);
            // write the sparrow program
            SparrowWriter writer = new SparrowWriter();
            Node sparrowProgramTree = root.accept(writer, st);
            // Print the sparrow program
            SparrowPrinter printer = new SparrowPrinter();
            String sparrowProgram = sparrowProgramTree.accept(printer, false);
            System.out.println(sparrowProgram);
        } catch (ParseException e) {
            System.exit(1);
        }
    }
}