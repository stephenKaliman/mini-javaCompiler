import java.util.HashMap;
import java.util.ArrayList;

public class MethodLike {
    String methodName;
    Integer index;
    HashMap<String, String> localsTypes;
    String owner;
    ArrayList<String> params;
    HashMap<String, String> paramsTypes;
    String returnType;

    public MethodLike(String name, String owner, String returnType){
        this.methodName = name;
        this.owner = owner;
        this.returnType = returnType;
        this.index = null;
        localsTypes = new HashMap<String, String>();
        params = new ArrayList<String>();
        paramsTypes = new HashMap<String, String>();
    }
}
