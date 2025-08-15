import org.antlr.v4.runtime.tree.ParseTreeListener;

public class MyParserListener extends SysYParserBaseListener {
    @Override
    public void exitBlockItem(SysYParser.BlockItemContext ctx) {
        System.out.println();
    }
    @Override
    public void enterBlock(SysYParser.BlockContext ctx) {
        System.out.print(" {");
    }
    @Override
    public void exitBlock(SysYParser.BlockContext ctx) {


        System.out.println();
        System.out.println("}");
    }
    @Override
    public void enterFuncDef(SysYParser.FuncDefContext ctx) {
        System.out.println();
    }
}
