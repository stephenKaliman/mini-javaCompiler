import cs132.IR.ParseException;
import cs132.IR.SparrowParser;
import cs132.IR.syntaxtree.*;

import java.util.ArrayList;
import java.util.HashMap;

public class S2SV{
    public static void main(String [] args) {
        try{
            SparrowParser parser = new SparrowParser(System.in);
            Program prgm = parser.Program();
            LivenessCollector pass1 = new LivenessCollector();
            Cursor cur1 = new Cursor();
            prgm.accept(pass1, cur1);
            ArrayList<FunctionObject> livenessData = pass1.livenessData;
            HashMap<String, FunctionObject> functions = new HashMap<String, FunctionObject>();
            for(FunctionObject f : livenessData){
                f.setDirectSuccessors();
                f.setGotoSuccessors();
                f.fixedPointLiveness();
                f.setLiveIntervals();
                f.allocateArgRegisters();
                f.linearScanAllocation();
                functions.put(f.funcName, f);
            }
            Translator pass2 = new Translator(functions);
            Cursor cur2 = new Cursor();
            Program sparrowV = pass2.visit(prgm, cur2);
            SparrowPrinter pass3 = new SparrowPrinter();
            String sparrowV_program = sparrowV.accept(pass3, false);
            System.out.println(sparrowV_program);
        }
        catch(ParseException p){}
    }
}