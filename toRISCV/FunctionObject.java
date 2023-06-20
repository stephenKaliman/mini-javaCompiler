import java.util.ArrayList;
import java.util.HashMap;
import cs132.IR.sparrowv.LabelInstr;
import cs132.IR.token.Label;

public class FunctionObject {
    String funcName;
    HashMap<String, Integer> funcArgMap;
    HashMap<String, Integer> funcVarMap;
    String retVal;

    public FunctionObject(String name){
        this.funcName = name;
        this.funcArgMap = new HashMap<String, Integer>();
        this.funcVarMap = new HashMap<String, Integer>();
    }

    public int getOffset(String varName){
        if(funcVarMap.containsKey(varName)){
            return getLocalOffset(varName);
        }
        else{
            return getParamOffset(varName);
        }
    }

    private int getLocalOffset(String localName){
        int index = funcVarMap.get(localName);
        int offset = -4 * (index + 3);
        return offset;
    }

    private int getParamOffset(String paramName){
        int index = funcArgMap.get(paramName);
        int offset = 4 * index;
        return offset;
    }

    public int getFrameSize(){
        int frameSize = 4 * (2 + funcVarMap.size());
        return frameSize;
    }

    public int getParamSize(){
        int paramSize = 4 * (funcArgMap.size());
        return paramSize;
    }

    public String getNewLabel(LabelInstr label){
        return funcName + "_" + label.label.toString();
    }

    public String getNewLabel(Label label){
        return funcName + "_" + label.toString();
    }

    public String getContinueLabel(Label label){
        return "continue_" + getNewLabel(label);
    }
}
