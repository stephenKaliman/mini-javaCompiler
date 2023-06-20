import java.util.HashMap;

public class SymbolTable {
    HashMap<String, ClassLike> table;
    Cursor cur;

    public SymbolTable(HashMap<String, ClassLike> table){
        this.table = table;
        cur = new Cursor();
    }
}
