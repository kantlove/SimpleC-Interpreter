import java.io.*;
import java.util.*;

// **********************************************************************
// Ast class (base class for all other kinds of nodes)
// **********************************************************************
abstract class Ast {
    protected SymbolTable table;
    protected CodeBuffer code;
}

enum Tag {
    GLOBAL,
    LOCAL,
    PARAM,
    CALL,
    CALLF,
    READ,
    WRITE
}

class SymbolTable {
    static class Indexer {
        private static int globalIndex;
        private static int labelIndex;
        private int localIndex;
        private int tempIndex;
        private int paramIndex;

        public int getGlobalIndex() {
            return globalIndex++;
        }

        public int countGlobal() {
            return globalIndex;
        }

        public int getLabelIndex() {
            return labelIndex++;
        }

        public int getLocalIndex() {
            return localIndex++;
        }

        public int countLocal() {
            return localIndex;
        }

        public int getTempIndex() {
            return tempIndex++;
        }

        public int countTemp() {
            return tempIndex;
        }

        public int getParamIndex() {
            return paramIndex++;
        }

        public int countParam() {
            return paramIndex;
        }
    }

    private SymbolTable ancestor;
    private String currentFn;
    private Indexer indexer;

    private Map<String, String> mapVar;
    private List<String> stringList;

    public SymbolTable() {
        mapVar = new TreeMap<String, String>();
        indexer = new Indexer();
        stringList = new ArrayList<String>();
    }

    public SymbolTable(SymbolTable ancestor) {
        this.ancestor = ancestor;
        mapVar = new TreeMap<String, String>();
        indexer = ancestor.indexer;
        stringList = ancestor.stringList;
    }

    public void enterVariable(Id name, Type type, Tag tag) {
        String code = null;
        if (tag == Tag.GLOBAL)
            code = String.format("$%d_%s", indexer.getGlobalIndex(), name.lexeme());
        else if (tag == Tag.LOCAL)
            code = String.format("@%d_%s", indexer.getLocalIndex(), name.lexeme());
        else if (tag == Tag.PARAM)
            code = String.format("%%%d_%s", indexer.getParamIndex(), name.lexeme());
        mapVar.put(name.lexeme(), code);
    }

    public void enterFunction(Id name, Type type, FormalsList formalList) {
        // Do nothing
    }

    public String enterString(String s) {
        for (int i = 0; i < stringList.size(); ++i)
            if (s.equals(stringList.get(i)))
                return String.format("?%d", i);

        String res = String.format("?%d", stringList.size());
        stringList.add(s);
        return res;
    }

    public int countGlobal() {
        return indexer.countGlobal();
    }

    public int countLocal() {
        return indexer.countLocal();
    }

    public int countTemp() {
        return indexer.countTemp();
    }

    public String newLabel() {
        return "~" + indexer.getLabelIndex();
    }

    public String newTemp() {
        return "&" + indexer.getTempIndex();
    }

    public String lookup(Id name) {
        for (SymbolTable t = this; t != null; t = t.ancestor)
            if (t.mapVar.containsKey(name.lexeme()))
                return t.mapVar.get(name.lexeme());
        return "unknown";
    }

    public CodeBuffer getStringListCode() {
        CodeBuffer code = new CodeBuffer();
        for (String s: stringList)
            code.append("str " + s);
        return code;
    }

    public void setCurrentFn(String currentFn) {
        this.currentFn = currentFn;
        indexer = new Indexer();
    }

    public String getCurrentFn() {
        return currentFn;
    }
}

class CodeBuffer {
    private List<String> list;

    public CodeBuffer() {
        list = new ArrayList<String>();
    }

    public void append(String s) {
        list.add(s);
    }

    public void append(String format, Object... args) {
        append(String.format(format, args));
    }

    public void append(CodeBuffer o) {
        if (o == null) return;
        for (String s: o.list)
            list.add(s);
    }

    public void appendTab(CodeBuffer o) {
        if (o == null) return;
        for (String s: o.list)
            list.add("    " + s);
    }

    public void appendLn() {
        append("");
    }

    public void output(PrintWriter out) {
        for (String s: list)
            out.println(s);
    }
}

class Program extends Ast {
    private DeclList declList;

    public Program(DeclList declList) {
        this.declList = declList;
    }

    // Compile
    public void compile(PrintWriter out) {
        table = new SymbolTable();
        declList.table = table;
        declList.compile(Tag.GLOBAL);

        code = new CodeBuffer();
        code.append(table.getStringListCode());
        code.appendLn();
        code.append("entry main, %d", table.countGlobal());
        code.append(declList.code);

        code.output(out);
    }
}

// **********************************************************************
// Decls
// **********************************************************************
class DeclList extends Ast {
    // linked list of kids (Decls)
    protected LinkedList decls;

    public DeclList(LinkedList decls) {
        this.decls = decls;
    }

    public void compile(Tag tag) {
        code = new CodeBuffer();
        ListIterator listIterator = decls.listIterator();
        while (listIterator.hasNext()) {
            Decl decl = (Decl) listIterator.next();
            decl.table = table;
            decl.compile(tag);
            code.append(decl.code);
        }
    }
}

abstract class Decl extends Ast {
    public abstract void compile(Tag tag);
}

class VarDecl extends Decl {
    private Type type;
    private Id name;

    public VarDecl(Type type, Id name) {
        this.type = type;
        this.name = name;
    }

    @Override
    public void compile(Tag tag) {
        table.enterVariable(name, type, tag);
    }
}

class FnDecl extends Decl {
    private Type type;
    private Id name;
    private FormalsList formalList;
    private FnBody body;

    public FnDecl(Type type, Id name, FormalsList formalList, FnBody body) {
        this.type = type;
        this.name = name;
        this.formalList = formalList;
        this.body = body;
    }

    @Override
    public void compile(Tag tag) {
        table.enterFunction(name, type, formalList);

        table = new SymbolTable(table);
        table.setCurrentFn(name.lexeme());

        formalList.table = table;
        formalList.compile();

        body.table = table;
        body.compile();

        code = new CodeBuffer();
        code.appendLn();
        code.append("func " + name.lexeme());
        code.append("funci %d, %d", table.countLocal(), table.countTemp());
        code.appendTab(body.code);
        code.append("efunc " + name.lexeme());
    }
}

class FnPreDecl extends Decl {
    private Type type;
    private Id name;
    private FormalsList formalList;

    public FnPreDecl(Type type, Id name, FormalsList formalList) {
        this.type = type;
        this.name = name;
        this.formalList = formalList;
    }

    @Override
    public void compile(Tag tag) {
        // Do nothing
    }
}

class FormalsList extends Ast {
    // linked list of kids (FormalDecls)
    private LinkedList formals;

    public FormalsList(LinkedList formals) {
        this.formals = formals;
    }

    public void compile() {
        code = new CodeBuffer();
        ListIterator listIterator = formals.listIterator();
        while (listIterator.hasNext()) {
            FormalDecl decl = (FormalDecl) listIterator.next();
            decl.table = table;
            decl.compile(Tag.PARAM);
            
            code.append(decl.code);
        }
    }
}

class FormalDecl extends Decl {
    private Type type;
    private Id name;

    public FormalDecl(Type type, Id name) {
        this.type = type;
        this.name = name;
    }

    @Override
    public void compile(Tag tag) {
        table.enterVariable(name, type, tag);
    }
}

class FnBody extends Ast {
    private DeclList declList;
    private StmtList stmtList;

    public FnBody(DeclList declList, StmtList stmtList) {
        this.declList = declList;
        this.stmtList = stmtList;
    }

    public void compile() {
        declList.table = table;
        declList.compile(Tag.LOCAL);

        stmtList.table = table;
        stmtList.compile();

        code = stmtList.code;
    }
}

class StmtList extends Ast {
    // linked list of kids (Stmts)
    private LinkedList stmts;

    public StmtList(LinkedList stmts) {
        this.stmts = stmts;
    }

    public void compile() {
        code = new CodeBuffer();
        ListIterator listIterator = stmts.listIterator();
        while (listIterator.hasNext()) {
            Stmt stmt = (Stmt) listIterator.next();
            stmt.table = table;
            stmt.nextLabel = table.newLabel();
            stmt.compile();

            code.append(stmt.code);
            code.append(stmt.nextLabel + ":");
        }
    }
}

// **********************************************************************
// Types
// **********************************************************************
class Type extends Ast {
    public static final String voidTypeName = "void";
    public static final String intTypeName = "int";

    private String name;
    private int size;  // use if this is an array type
    private int numPointers;

    private Type() {
    }
    
    public static Type CreateSimpleType(String name) {
        Type t = new Type();
        t.name = name;
        t.size = -1;
        t.numPointers = 0;
        
        return t;
    }

    public static Type CreateArrayType(String name, int size) {
        Type t = new Type();
        t.name = name;
        t.size = size;
        t.numPointers = 0;
        
        return t;
    }

    public static Type CreatePointerType(String name, int numPointers) {
        Type t = new Type();
        t.name = name;
        t.size = -1;
        t.numPointers = numPointers;
        
        return t;
    }

    public static Type CreateArrayPointerType(String name, int size, int numPointers) {
        Type t = new Type();
        t.name = name;
        t.size = size;
        t.numPointers = numPointers;
        
        return t;
    }
    
    public String name() {
        return name;
    }
}

// **********************************************************************
// Stmts
// **********************************************************************
abstract class Stmt extends Ast {
    public abstract void compile();
    protected String nextLabel;
}

class AssignStmt extends Stmt {
    private Exp lhs;
    private Exp exp;

    public AssignStmt(Exp lhs, Exp exp) {
        this.lhs = lhs;
        this.exp = exp;
    }

    @Override
    public void compile() {
        exp.table = table;
        exp.compile();

        lhs.table = table;
        lhs.compile();

        code = new CodeBuffer();
        code.append(exp.code);
        code.append("move %s, %s", lhs.addr, exp.addr);
    }
}

class IfStmt extends Stmt {
    private Exp exp;
    private DeclList declList;
    private StmtList stmtList;

    public IfStmt(Exp exp, DeclList declList, StmtList stmtList) {
        this.exp = exp;
        this.declList = declList;
        this.stmtList = stmtList;
    }

    @Override
    public void compile() {
        exp.trueLabel = table.newLabel();
        exp.falseLabel = nextLabel;
        exp.table = table;
        exp.compile();

        declList.table = new SymbolTable(table);
        declList.compile(Tag.LOCAL);

        stmtList.table = declList.table;
        stmtList.compile();

        code = new CodeBuffer();
        code.append(exp.code);
        code.append(exp.trueLabel + ":");
        code.append(stmtList.code);
    }
}

class IfElseStmt extends Stmt {
    private Exp exp;
    private DeclList declList1;
    private DeclList declList2;
    private StmtList stmtList1;
    private StmtList stmtList2;

    public IfElseStmt(Exp exp, DeclList declList1, StmtList stmtList1, 
            DeclList declList2, StmtList stmtList2) {
        this.exp = exp;
        this.declList1 = declList1;
        this.stmtList1 = stmtList1;
        this.declList2 = declList2;
        this.stmtList2 = stmtList2;
    }

    @Override
    public void compile() {
        exp.trueLabel = table.newLabel();
        exp.falseLabel = table.newLabel();
        exp.table = table;
        exp.compile();

        declList1.table = new SymbolTable(table);
        declList1.compile(Tag.LOCAL);

        stmtList1.table = declList1.table;
        stmtList1.compile();

        declList2.table = new SymbolTable(table);
        declList2.compile(Tag.LOCAL);

        stmtList2.table = declList2.table;
        stmtList2.compile();

        code = new CodeBuffer();
        code.append(exp.code);
        code.append(exp.trueLabel + ":");
        code.append(stmtList1.code);
        code.append("jump %s", this.nextLabel);
        code.append(exp.falseLabel + ":");
        code.append(stmtList2.code);
    }
}

class WhileStmt extends Stmt {
    private Exp exp;
    private DeclList declList;
    private StmtList stmtList;

    public WhileStmt(Exp exp, DeclList declList, StmtList stmtList) {
        this.exp = exp;
        this.declList = declList;
        this.stmtList = stmtList;
    }

    @Override
    public void compile() {
        String begin = table.newLabel();
        exp.trueLabel = table.newLabel();
        exp.falseLabel = nextLabel;
        exp.table = table;
        exp.compile();

        declList.table = new SymbolTable(table);
        declList.compile(Tag.LOCAL);

        stmtList.table = declList.table;
        stmtList.compile();

        code = new CodeBuffer();
        code.append(begin + ":");
        code.append(exp.code);
        code.append(exp.trueLabel + ":");
        code.append(stmtList.code);
        code.append("jump %s", begin);
    }
}

class ForStmt extends Stmt {
    private Stmt init;
    private Exp cond;
    private Stmt incr;
    private DeclList declList;
    private StmtList stmtList;

    public ForStmt(Stmt init, Exp cond, Stmt incr, 
            DeclList declList, StmtList stmtList) {
        this.init = init;
        this.cond = cond;
        this.incr = incr;
        this.declList = declList;
        this.stmtList = stmtList;
    }

    @Override
    public void compile() {
        String begin = table.newLabel();

        init.nextLabel = begin;
        init.table = table;
        init.compile();

        cond.trueLabel = table.newLabel();
        cond.falseLabel = nextLabel;
        cond.table = table;
        cond.compile();

        declList.table = new SymbolTable(table);
        declList.compile(Tag.LOCAL);

        stmtList.table = declList.table;
        stmtList.compile();
        
        incr.nextLabel = begin;
        incr.table = table;
        incr.compile();

        code = new CodeBuffer();
        code.append(init.code);
        code.append(begin + ":");
        code.append(cond.code);
        code.append(cond.trueLabel + ":");
        code.append(stmtList.code);
        code.append(incr.code);
        code.append("jump %s", begin);
    }
}

class CallStmt extends Stmt {
    private CallExp callExp;

    public CallStmt(CallExp callExp) {
        this.callExp = callExp;
    }

    @Override
    public void compile() {
        callExp.table = table;
        callExp.compile(Tag.CALL);
        code = callExp.code;
    }
}

class ReturnStmt extends Stmt {
    private Exp exp; // null for empty return

    public ReturnStmt(Exp exp) {
        this.exp = exp;
    }

    @Override
    public void compile() {
        code = new CodeBuffer();
        if (exp == null) {
            code.append("ret " + table.getCurrentFn());
        } else {
            exp.table = table;
            exp.compile();
            code.append(exp.code);
            code.append("retf %s, %s", table.getCurrentFn(), exp.addr);
        }
    }
}

// **********************************************************************
// Exps
// **********************************************************************
abstract class Exp extends Ast {
    public abstract int getLine();
    public abstract int getChar();
    
    public String addr;
    public String trueLabel;
    public String falseLabel;
    public abstract void compile();
}

abstract class BasicExp extends Exp {
    private int lineNum;
    private int charNum;
    
    public BasicExp(int lineNum, int charNum) {
        this.lineNum = lineNum;
        this.charNum = charNum;
    }
    
    public int getLine() {
        return lineNum;
    }
    public int getChar() {
        return charNum;
    }
}

class IntLit extends BasicExp {
    private int intVal;

    public IntLit(int lineNum, int charNum, int intVal) {
        super(lineNum, charNum);
        this.intVal = intVal;
    }

    @Override
    public void compile() {
        addr = Integer.toString(intVal);
    }
}

class StringLit extends BasicExp {
    private String strVal;

    public StringLit(int lineNum, int charNum, String strVal) {
        super(lineNum, charNum);
        this.strVal = strVal;
    }

    public String str() {
        return strVal;
    }

    @Override
    public void compile() {
        addr = table.enterString(strVal);
    }
}

class Id extends BasicExp {
    private String strVal;

    public Id(int lineNum, int charNum, String strVal) {
        super(lineNum, charNum);
        this.strVal = strVal;
    }

    public String lexeme() {
        return strVal;
    }

    @Override
    public void compile() {
        addr = table.lookup(this);
    }
}

class ArrayExp extends Exp {
    private Exp lhs;
    private Exp exp;

    public ArrayExp(Exp lhs, Exp exp) {
        this.lhs = lhs;
        this.exp = exp;
    }

    public int getLine() {
        return lhs.getLine();
    }

    public int getChar() {
        return lhs.getChar();
    }

    @Override
    public void compile() {
        // Do nothing
    }
}

class CallExp extends Exp {
    private Id name;
    private ActualList actualList;

    public CallExp(Id name, ActualList actualList) {
        this.name = name;
        this.actualList = actualList;
    }

    public CallExp(Id name) {
        this.name = name;
        this.actualList = new ActualList(new LinkedList());
    }

    public int getLine() {
        return name.getLine();
    }

    public int getChar() {
        return name.getChar();
    }

    @Override
    public void compile() {
        code = new CodeBuffer();
        actualList.table = table;
        actualList.compile(null);
        addr = table.newTemp();
        code.append(actualList.code);
        code.append("callf %s, %s, %d", addr, name.lexeme(), actualList.size());
    }

    public void compile(Tag tag) {
        code = new CodeBuffer();

        actualList.table = table;
        if (name.lexeme().equals("scanf")) {
            actualList.compile(Tag.READ);
            code.append(actualList.code);
        }
        else if (name.lexeme().equals("printf")) {
            actualList.compile(Tag.WRITE);
            code.append(actualList.code);
        }
        else {
            actualList.compile(null);
            code.append(actualList.code);
            if (tag == Tag.CALL) {
                code.append("call %s, %d", name.lexeme(), actualList.size());
            }
            else {
                addr = table.newTemp();
                code.append("callf %s, %s, %d", addr, name.lexeme(), actualList.size());
            }
        }
    }
}

class ActualList extends Ast {
    // linked list of kids (Exps)
    private LinkedList exps;

    public ActualList(LinkedList exps) {
        this.exps = exps;
    }

    public int size() {
        return exps.size();
    }

    public void compile(Tag tag) {
        code = new CodeBuffer();
        ListIterator listIterator = exps.listIterator();
        while (listIterator.hasNext()) {
            Exp exp = (Exp) listIterator.next();
            exp.table = table;
            exp.compile();

            code.append(exp.code);
        }

        int order = 0;
        listIterator = exps.listIterator();
        while (listIterator.hasNext()) {
            Exp exp = (Exp) listIterator.next();
            if (tag == Tag.READ)
                code.append("read %s", exp.addr);
            else if (tag == Tag.WRITE)
                code.append("write %s", exp.addr);
            else
                code.append("arg %s, %d", exp.addr, order++);
        }
    }
}

abstract class UnaryExp extends Exp {
    protected Exp exp;

    public UnaryExp(Exp exp) {
        this.exp = exp;
    }

    public int getLine() {
        return exp.getLine();
    }

    public int getChar() {
        return exp.getChar();
    }
}

abstract class BinaryExp extends Exp {
    protected Exp exp1;
    protected Exp exp2;

    public BinaryExp(Exp exp1, Exp exp2) {
        this.exp1 = exp1;
        this.exp2 = exp2;
    }

    public int getLine() {
        return exp1.getLine();
    }

    public int getChar() {
        return exp1.getChar();
    }

    @Override
    public void compile() {
        exp1.table = table;
        exp1.compile();

        exp2.table = table;
        exp2.compile();

        code = new CodeBuffer();
        code.append(exp1.code);
        code.append(exp2.code);

        addr = table.newTemp();
        finalStep();
    }

    protected abstract void finalStep();
}

abstract class BooleanExpr extends BinaryExp {
    public BooleanExpr(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    protected boolean isBooleanExpr() {
        return trueLabel != null && falseLabel != null;
    }

    @Override
    public void compile() {
        exp1.table = table;
        exp1.compile();

        exp2.table = table;
        exp2.compile();

        code = new CodeBuffer();
        combineCode();

        if (!isBooleanExpr()) {
            addr = table.newTemp();
            finalStep();
        }
    }

    protected void combineCode() {
        code.append(exp1.code);
        code.append(exp2.code);

        if (isBooleanExpr()) {
            addr = table.newTemp();
            finalStep();
            code.append("jt %s, %s", addr, trueLabel);
            code.append("jump %s", falseLabel);
        }
    }
}


// **********************************************************************
// UnaryExps
// **********************************************************************
class UnaryMinusExp extends UnaryExp {
    public UnaryMinusExp(Exp exp) {
        super(exp);
    }

    @Override
    public void compile() {
        exp.table = table;
        exp.compile();

        addr = table.newTemp();
        code = new CodeBuffer();
        code.append(exp.code);
        code.append("sub %s, 0, %s", addr, exp.addr);
    }
}

class NotExp extends UnaryExp {
    public NotExp(Exp exp) {
        super(exp);
    }

    @Override
    public void compile() {
        exp.table = table;
        exp.compile();

        addr = table.newTemp();
        code = new CodeBuffer();
        code.append(exp.code);
        code.append("not %s, %s", addr, exp.addr);

        if (trueLabel != null && falseLabel != null) {
            exp.trueLabel = falseLabel;
            exp.falseLabel = trueLabel;
        }
    }
}

class AddrOfExp extends UnaryExp {
    public AddrOfExp(Exp exp) {
        super(exp);
    }

    @Override
    public void compile() {
        // Do nothing
    }
}

class DeRefExp extends UnaryExp {
    public DeRefExp(Exp exp) {
        super(exp);
    }

    @Override
    public void compile() {
        // Do nothing
    }
}

// **********************************************************************
// BinaryExps
// **********************************************************************
class PlusExp extends BinaryExp {
    public PlusExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    protected void finalStep() {
        code.append("add %s, %s, %s", addr, exp1.addr, exp2.addr);
    }
}

class MinusExp extends BinaryExp {
    public MinusExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    protected void finalStep() {
        code.append("sub %s, %s, %s", addr, exp1.addr, exp2.addr);
    }
}

class TimesExp extends BinaryExp {
    public TimesExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    protected void finalStep() {
        code.append("mult %s, %s, %s", addr, exp1.addr, exp2.addr);
    }
}

class DivideExp extends BinaryExp {
    public DivideExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    protected void finalStep() {
        code.append("div %s, %s, %s", addr, exp1.addr, exp2.addr);
    }
}

class ModuloExp extends BinaryExp {
    public ModuloExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    protected void finalStep() {
        code.append("mod %s, %s, %s", addr, exp1.addr, exp2.addr);
    }
}

class AndExp extends BooleanExpr {
    public AndExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    protected void combineCode() {
        if (isBooleanExpr()) {
            exp1.trueLabel = table.newLabel();
            exp1.falseLabel = falseLabel;
            exp2.trueLabel = trueLabel;
            exp2.falseLabel = falseLabel;

            code.append(exp1.code);
            code.append(exp1.trueLabel + ":");
            code.append(exp2.code);
        } else {
            code.append(exp1.code);
            code.append(exp2.code);
        }
    }

    @Override
    public void finalStep() {
        code.append("and %s, %s, %s", addr, exp1.addr, exp2.addr);
    }
}

class OrExp extends BooleanExpr {
    public OrExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    protected void combineCode() {
        if (isBooleanExpr()) {
            exp1.trueLabel = trueLabel;
            exp1.falseLabel = table.newLabel();
            exp2.trueLabel = trueLabel;
            exp2.falseLabel = falseLabel;

            code.append(exp1.code);
            code.append(exp1.falseLabel + ":");
            code.append(exp2.code);
        } else {
            code.append(exp1.code);
            code.append(exp2.code);
        }
    }

    @Override
    public void finalStep() {
        code.append("or %s, %s, %s", addr, exp1.addr, exp2.addr);
    }
}

class EqualsExp extends BooleanExpr {
    public EqualsExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    public void finalStep() {
        code.append("eq %s, %s, %s", addr, exp1.addr, exp2.addr);
    }
}

class NotEqualsExp extends BooleanExpr {
    public NotEqualsExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    public void finalStep() {
        code.append("neq %s, %s, %s", addr, exp1.addr, exp2.addr);
    }
}

class LessExp extends BooleanExpr {
    public LessExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    public void finalStep() {
        code.append("lt %s, %s, %s", addr, exp1.addr, exp2.addr);
    }
}

class GreaterExp extends BooleanExpr {
    public GreaterExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    public void finalStep() {
        code.append("gt %s, %s, %s", addr, exp1.addr, exp2.addr);
    }
}

class LessEqExp extends BooleanExpr {
    public LessEqExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    public void finalStep() {
        code.append("lte %s, %s, %s", addr, exp1.addr, exp2.addr);
    }
}

class GreaterEqExp extends BooleanExpr {
    public GreaterEqExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    public void finalStep() {
        code.append("gte %s, %s, %s", addr, exp1.addr, exp2.addr);
    }
}