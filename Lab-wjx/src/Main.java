import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.*;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;

public class Main {

        public static boolean printSysYTokenInformation(Token t) {
        String tokenName = SysYLexer.VOCABULARY.getDisplayName(t.getType());
        String tokenText = t.getText();
        String symbol = SysYLexer.VOCABULARY.getSymbolicName(t.getType());
        int line = t.getLine();
        int charPositionInLine = t.getCharPositionInLine();
        if (t.getType() == SysYLexer.WS) {
            return false;
        }
        if (t.getType() == SysYLexer.LINE_COMMENT) {
            return false;
        }
        if (t.getType() == SysYLexer.MULTILINE_COMMENT) {
            return false;
        }
        if (t.getType() == SysYLexer.INTEGER_CONST) {
//            tokenText = String.valueOf(NumberConverter.convertToDecimal(tokenText));
        }
//        System.err.println(symbol + " " + tokenText + " at Line " + line + ".");
        return true;
    }



    public static void main(String[] args) throws IOException {
        String source = args[0];
        String target = args[1];
        CharStream input = CharStreams.fromFileName(source);
        SysYLexer sysYLexer = new SysYLexer(input);
        List<? extends Token> myTokens = sysYLexer.getAllTokens();
        List<Token> myNewTokens = new ArrayList<>();
        for (Token t : myTokens) {
            if(printSysYTokenInformation(t)) {
                myNewTokens.add(t);
            };
        }
        ListTokenSource tokenSource = new ListTokenSource(myNewTokens);
        TokenStream tokenStream = new CommonTokenStream(tokenSource);
        SysYParser sysYParser = new SysYParser(tokenStream);
        MyIrVisitor myIrVisitor = null;
        LLVMModuleRef myModuleRef = null;
        if (args.length == 4 && args[2].equals("test")) {
            myIrVisitor = new MyIrVisitor(args[0], args[3]);
            myIrVisitor.visit(sysYParser.program());
            myModuleRef = myIrVisitor.module;
        } else {
            myIrVisitor = new MyIrVisitor(source, target);
            myIrVisitor.visit(sysYParser.program());
            myModuleRef = myIrVisitor.module;
        }

//
//        RISCVTargetCodeGenerator myCodeGenerator = new RISCVTargetCodeGenerator(myModuleRef, target);
//        myCodeGenerator.generate();

    }
}
