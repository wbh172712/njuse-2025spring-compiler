import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.ArrayList;
import java.util.List;

public class ErrorListener extends BaseErrorListener {

    public boolean hasErrorInformation = false;
    public List<Integer> lines = new ArrayList<>();

    public void syntaxError(Recognizer<?,?> recognizer, Object offendingSymbol,
                            int line, int charPositionInLine, String msg,
                            RecognitionException e) {
        lines.add(line);
        hasErrorInformation = true;
    }

    public void printLexerErrorInformation() {
        for (Integer line : lines) {
            System.err.println("Error type A at Line " + line + ":" + "Error");
        }
    }

}
