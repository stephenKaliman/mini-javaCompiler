import java.util.HashMap;

public class ProgramObject {
    HashMap<String, FunctionObject> funcTable = new HashMap<String, FunctionObject>();
    HashMap<String, Integer> errorMsgs = new HashMap<String, Integer>();
    String mainFunction = null;

    public String getMsgName(String errorMsg){
        return "msg_"+errorMsgs.get(errorMsg);
    }
}
