import org.antlr.v4.runtime.*;

public class SysYParserErrorListener extends BaseErrorListener {
    public boolean hasSyntaxError = false;
    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        hasSyntaxError = true;
//        System.out.printf("Error type B at Line %d: %s\n", line, msg);
    }
}
