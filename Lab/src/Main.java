import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("input and output path is required");
        }

        CharStream input = CharStreams.fromFileName(args[0]);
        SysYLexer sysYLexer = new SysYLexer(input);

        CommonTokenStream tokens = new CommonTokenStream(sysYLexer);
        SysYParser sysYParser = new SysYParser(tokens);

        sysYParser.removeErrorListeners();
        SysYParserErrorListener myParserErrorListener = new SysYParserErrorListener();
        sysYParser.addErrorListener(myParserErrorListener);

        ParseTree tree = sysYParser.program();

//        SysYFormatter visitor = new SysYFormatter();
//        SysYSemanticErrorPrinter visitor = new SysYSemanticErrorPrinter();


//        MyLLVMVisitor visitor = new MyLLVMVisitor();
        MyLLVMVisitor visitor = new MyLLVMVisitor(args[1]);
        visitor.visit(tree);

//        MyRISCVVisitor riscvVisitor = new MyRISCVVisitor(visitor.module, args[1]);
//        riscvVisitor.generateRiscVCode();

//        if (!visitor.hasSemanticError) {
//            System.err.println("No semantic errors in the program!");
//        }

    }
}
