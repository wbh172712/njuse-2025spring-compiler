import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public class MyLexerErrorListener extends BaseErrorListener {
    static StringBuilder errorInfo = new StringBuilder();
    static boolean hasError = false;
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        errorInfo.append("Error type A at Line " + line + ": " + charPositionInLine + " " + msg + "\n");
        hasError = true;
    }

    public boolean hasError() {
        return hasError;
    }
    public void printLexerErrorInformation() {
        System.err.println(errorInfo);
    }
}