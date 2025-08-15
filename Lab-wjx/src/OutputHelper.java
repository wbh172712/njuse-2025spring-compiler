
public class OutputHelper {
    static boolean hasSemanticError = false;
    static void printSemanticError(ErrorType errorType, int lineNo, String symbolText) {
        System.err.println("Error type " + errorType.getNo() + " at Line " + lineNo + ":" + errorType.getMsg() + " " + symbolText);
        hasSemanticError = true;
    }

    static boolean hasSemanticError() {
        return hasSemanticError;
    }
}
