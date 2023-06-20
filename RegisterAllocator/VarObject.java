public class VarObject {
    // name in sparrow
    String varName;
    // name in sparrowV
    String v_regName;
    String v_varName;
    Boolean inRegister;
    // interval
    Integer start;
    Integer end;
    Boolean includesCall;

    public VarObject(String name, Integer start){
        this.varName = name;
        this.start = start;
        this.end = start;
        includesCall = false;
        v_regName = null;
        v_varName = name;
        inRegister = false;
    }
}
