import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

public class MyParserErrorListener extends BaseErrorListener {
    static StringBuilder errorInfo = new StringBuilder();
    static boolean hasError = false;
    static List<Integer> errorLine = new ArrayList<>();
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        if (!errorLine.contains(line)) {
            errorInfo.append("Error type B at Line " + line + ": " + charPositionInLine + " " + msg + "\n");
            errorLine.add(line);
            hasError = true;
        }

    }

    public boolean hasError() {
        return hasError;
    }

    public void printParserErrorInformation() {
        System.out.print(errorInfo);
    }

}
