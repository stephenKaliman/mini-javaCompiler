import java.util.HashMap;

public class ClassLike {
    String className;

    String superclassName;
    ClassLike superclass;

    HashMap<String, Integer> fieldIndices;
    HashMap<String, String> fieldTypes;
    int totalFields;

    HashMap<String, MethodLike> methods;

    private boolean filled;

    public ClassLike(String className)
    {
        this.className = className;
        fieldIndices = new HashMap<String, Integer>();
        fieldTypes = new HashMap<String, String>();
        methods = new HashMap<String, MethodLike>();
    }

    // Fill in parents' methods
    private void fillMethods(){
        // initialize set of methods for this class, as copy of parent methods
        HashMap<String, MethodLike> parentMethods = superclass.methods;
        HashMap<String, MethodLike> newThisMethods = new HashMap<String, MethodLike>(parentMethods);
        // be sure to fill in indices before overriding!
        fillMethodIndices();
        // add or override with this class's defined methods
        newThisMethods.putAll(this.methods);
        // set class methods to this created hashmap
        this.methods = newThisMethods;
    }
    // Fill in proper method indices
    private void fillMethodIndices(){
        // get number of methods in parent class, if it exists
        int parentMethodCount;
        // get parent methods
        HashMap<String, MethodLike> parentMethods;
        if(superclass != null){
            parentMethodCount = superclass.methods.size();
            parentMethods = superclass.methods;
        }
        // otherwise, just leave it as 0
        else{
            parentMethodCount = 0;
            parentMethods = new HashMap<String, MethodLike>();
        }
        // initialize counter to index new unique methods
        int nextIndex = parentMethodCount;
        // go through methods in this class
        for(MethodLike cur_method : this.methods.values())
        {
            // if it exists in the parent class, give it the same index
            if(parentMethods.containsKey(cur_method.methodName)){
                MethodLike parentMethod = parentMethods.get(cur_method.methodName);
                cur_method.index = parentMethod.index;
            }
            // otherwise, just give it the next available index
            else{
                cur_method.index = nextIndex;
                // increment the index counter
                nextIndex ++ ;
            }
        }
    }

    // Fill in proper indices for fields, add inherited fields
    private void fillFields()
    {
        int parentFieldCount = superclass.totalFields;
        // fill proper indices for non-inherited fields
        for(String fieldName : fieldIndices.keySet()){
            int index = fieldIndices.get(fieldName);
            index += parentFieldCount;
            fieldIndices.put(fieldName, index);
        }
        // set proper total field count
        totalFields = parentFieldCount + fieldIndices.size();
        // track all inherited field indices and types
        for(String fieldName : superclass.fieldIndices.keySet()){
            // if the field is inherited, add to current class as it is in parent class
            if(!fieldIndices.containsKey(fieldName)){
                Integer fieldIndex = superclass.fieldIndices.get(fieldName);
                String fieldType = superclass.fieldTypes.get(fieldName);
                fieldIndices.put(fieldName, fieldIndex);
                fieldTypes.put(fieldName, fieldType);
            }
        }
    }

    // Fill in all info, using superclasses
    // return total # of fields, for recursive purposes
    public void fill()
    {
        // if we have already filled it, nothing to do
        if(filled){
            return;
        }
        // if there is no superclass, set as base case
        else if(superclassName == null){
            // no need to "fill" fields or methods since no parent class
            // fill method indices
            fillMethodIndices();
            // set totalFields
            totalFields = fieldIndices.size();
            // mark as having been filled
            filled = true;
        }
        // otherwise, fill based on parent class
        else{
            // ensure parent is filled
            superclass.fill();
            // fill fields
            fillFields();
            // fill methods
            fillMethods();
            // mark as having been filled
            filled = true;
        }
    }
}
