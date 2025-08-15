include Makefile.git

LLVM_JAR = $(shell echo `find /usr/local/lib -name "llvm-*.jar"` | sed  "s/\s\+/:/g")
JAVACPP_JAR = $(shell echo `find /usr/local/lib -name "javacpp-*.jar"` | sed  "s/\s\+/:/g")
ANTLR_PATH = $(shell find /usr/local/lib -name "antlr-*-complete.jar")

export CLASSPATH=$(ANTLR_PATH):$(LLVM_JAR):$(JAVACPP_JAR)

DOMAINNAME = oj.compilers.cpl.icu
ANTLR = java -jar $(ANTLR_PATH) -listener -visitor -long-messages
JAVAC = javac -g -encoding UTF-8
JAVA = java


PFILE = $(shell find . -name "SysYParser.g4")
LFILE = $(shell find . -name "SysYLexer.g4")
JAVAFILE = $(shell find . -name "*.java")

compile: antlr
	$(call git_commit,"make")
	mkdir -p classes
	$(JAVAC) -classpath $(CLASSPATH) $(JAVAFILE) -d classes

run: compile
	java -classpath ./classes:$(CLASSPATH) Main $(FILEPATH) $(TARGET)

antlr: $(LFILE) $(PFILE)
	$(ANTLR) $(PFILE) $(LFILE)

test:  # 测试
	$(call git_commit, "test")
	/usr/lib/jvm/java-11-openjdk-amd64/bin/java -Djava.library.path=\\wsl.localhost\Ubuntu\usr\local\lib -Dfile.encoding=UTF-8 -classpath /mnt/e/codes/compiler-principle/Lab_backtrace/Lab/out/production/Lab:/usr/local/lib/antlr-4.9.1-complete.jar:/usr/local/lib/javacpp/javacpp-1.5.7.jar:/usr/local/lib/javacpp/javacpp-1.5.7-linux-x86_64.jar:/usr/local/lib/llvm/llvm-13.0.1-1.5.7.jar:/usr/local/lib/llvm/llvm-13.0.1-1.5.7-linux-x86_64.jar:/usr/local/lib/llvm-platform/llvm-platform-13.0.1-1.5.7.jar:/usr/local/lib/llvm4j/kotlin-stdlib-1.9.0.jar:/usr/local/lib/llvm4j/llvm4j-0.1.2-SNAPSHOT.jar:/usr/local/lib/rars/rars.jar Main tests/test.sysy tests/test.arm test tests/test.ll
	bash ./tests/runtest.sh

clean:
	rm -f src/*.tokens
	rm -f src/*.interp
	rm -f src/SysYLexer.java src/SysYParser.java src/SysYParserBaseListener.java src/SysYParserBaseVisitor.java src/SysYParserListener.java src/SysYParserVisitor.java
	rm -rf classes
	rm -rf out

submit: clean
	git gc
	bash submit.sh

.PHONY: compile antlr test run clean submit